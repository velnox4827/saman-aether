#!/usr/bin/env python3
"""Unit/integration tests for Saman Share's stdlib HTTP server."""

import concurrent.futures
import http.client
import os
from pathlib import Path
import signal
import shlex
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest

HERE = Path(__file__).resolve().parent
SERVER = HERE / "server.py"
SECURITY_HEADERS = {
    "cache-control": "no-store",
    "referrer-policy": "no-referrer",
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
}


def unused_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class RunningServer:
    def __init__(self, mode="two-way", *, max_bytes=1024 * 1024, reserve_bytes=0, timeout=1, max_connections=32):
        self.temp = tempfile.TemporaryDirectory()
        self.share = Path(self.temp.name)
        self.token = "test-token"
        self.port = unused_port()
        env = os.environ.copy()
        env.update(
            SAMAN_SHARE_MAX_BYTES=str(max_bytes),
            SAMAN_SHARE_RESERVE_BYTES=str(reserve_bytes),
            SAMAN_SHARE_TIMEOUT=str(timeout),
            SAMAN_SHARE_TOKEN=self.token,
        )
        self.proc = subprocess.Popen(
            [sys.executable, "-u", str(SERVER), str(self.share), "127.0.0.1", str(self.port), "-", mode],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            env=env,
        )
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            if self.proc.poll() is not None:
                out, err = self.proc.communicate()
                raise RuntimeError(f"server exited early: {out}{err}")
            try:
                with socket.create_connection(("127.0.0.1", self.port), timeout=0.05):
                    return
            except OSError:
                time.sleep(0.02)
        self.close()
        raise RuntimeError("server did not become ready")

    def request(self, method, path, body=None, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        conn.request(method, path, body=body, headers=headers or {})
        response = conn.getresponse()
        data = response.read()
        result = response.status, {k.lower(): v for k, v in response.getheaders()}, data
        conn.close()
        return result

    def auth_headers(self, **extra):
        return {"X-Saman-Token": self.token, **extra}

    def close(self):
        if self.proc.poll() is None:
            self.proc.send_signal(signal.SIGINT)
            try:
                self.proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(timeout=3)
        self.stdout, self.stderr = self.proc.communicate()
        self.temp.cleanup()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


class ShareServerTests(unittest.TestCase):
    def assert_security_headers(self, headers):
        for name, value in SECURITY_HEADERS.items():
            self.assertEqual(headers.get(name), value, name)

    def test_query_token_bootstraps_cookie_then_redirects_without_token(self):
        with RunningServer() as server:
            status, headers, body = server.request("GET", "/?token=test-token")
            self.assertEqual(status, 303)
            self.assertEqual(headers.get("location"), "/")
            cookie = headers.get("set-cookie", "")
            self.assertIn("saman_token=", cookie)
            self.assertIn("HttpOnly", cookie)
            self.assertIn("SameSite=Strict", cookie)
            self.assertNotIn(server.token, headers.get("location", ""))
            self.assert_security_headers(headers)

            status, headers, body = server.request("GET", "/", headers={"Cookie": cookie.split(";", 1)[0]})
            self.assertEqual(status, 200)
            page = body.decode()
            self.assertNotIn("?token=", page)
            self.assertNotIn(server.token, page)
            self.assert_security_headers(headers)

    def test_missing_or_wrong_auth_is_forbidden_and_header_auth_works(self):
        with RunningServer() as server:
            for headers in ({}, {"X-Saman-Token": "wrong"}):
                status, response_headers, _ = server.request("GET", "/", headers=headers)
                self.assertEqual(status, 403)
                self.assert_security_headers(response_headers)
            status, _, _ = server.request("GET", "/", headers=server.auth_headers())
            self.assertEqual(status, 200)

    def test_query_token_is_only_accepted_for_root_bootstrap(self):
        with RunningServer() as server:
            (server.share / "hello.txt").write_text("hello")
            status, _, _ = server.request("GET", "/file/hello.txt?token=test-token")
            self.assertEqual(status, 403)

    def test_traversal_and_unsafe_upload_names_are_rejected(self):
        bad_names = ["../escape", "..%2Fescape", "%2Ftmp%2Fevil", "a%5Cb", ".", "..", "%00bad", "a%0Ab"]
        with RunningServer() as server:
            for name in bad_names:
                with self.subTest(name=name):
                    status, _, _ = server.request(
                        "POST", "/upload", b"x", server.auth_headers(**{"X-Filename": name, "Content-Length": "1"})
                    )
                    self.assertEqual(status, 400)
            self.assertEqual(list(server.share.iterdir()), [])

    def test_download_traversal_is_not_normalized_to_a_file(self):
        with RunningServer() as server:
            (server.share / "secret.txt").write_text("secret")
            for path in ("/file/../secret.txt", "/file/%2E%2E%2Fsecret.txt", "/file/%2Fsecret.txt"):
                status, _, _ = server.request("GET", path, headers=server.auth_headers())
                self.assertIn(status, (400, 404))

    def test_concurrent_same_name_uploads_are_collision_safe(self):
        payloads = [bytes([i]) * (128 * 1024) for i in range(1, 9)]
        with RunningServer(max_bytes=256 * 1024) as server:
            gate = threading.Barrier(len(payloads))

            def upload(payload):
                gate.wait()
                return server.request(
                    "POST", "/upload", payload,
                    server.auth_headers(**{"X-Filename": "same.bin", "Content-Length": str(len(payload))}),
                )

            with concurrent.futures.ThreadPoolExecutor(max_workers=len(payloads)) as pool:
                results = list(pool.map(upload, payloads))
            self.assertTrue(all(status == 201 for status, _, _ in results), results)
            files = sorted(p for p in server.share.iterdir() if p.is_file())
            self.assertEqual(len(files), len(payloads))
            self.assertEqual({p.read_bytes() for p in files}, set(payloads))
            self.assertFalse(any(".part" in p.name for p in server.share.rglob("*")))

    def test_collision_suffix_stays_within_filesystem_name_limit(self):
        name = "a" * 251 + ".bin"
        with RunningServer() as server:
            headers = server.auth_headers(**{"X-Filename": name, "Content-Length": "1"})
            self.assertEqual(server.request("POST", "/upload", b"1", headers)[0], 201)
            self.assertEqual(server.request("POST", "/upload", b"2", headers)[0], 201)
            files = [path for path in server.share.iterdir() if path.is_file()]
            self.assertEqual(len(files), 2)
            self.assertTrue(all(len(path.name.encode("utf-8")) <= 255 for path in files))

    def test_external_file_created_during_upload_is_not_overwritten(self):
        upload = b"uploaded payload"
        external = b"external payload"
        with RunningServer() as server:
            sock = socket.create_connection(("127.0.0.1", server.port), timeout=3)
            request = (
                "POST /upload HTTP/1.1\r\nHost: localhost\r\nX-Saman-Token: test-token\r\n"
                f"X-Filename: race.bin\r\nContent-Length: {len(upload)}\r\n\r\n"
            )
            sock.sendall(request.encode("ascii") + upload[:1])

            deadline = time.monotonic() + 2
            while time.monotonic() < deadline and not list(server.share.rglob("*.part")):
                time.sleep(0.01)
            self.assertTrue(list(server.share.rglob("*.part")), "upload did not reach its reserved partial file")
            (server.share / "race.bin").write_bytes(external)

            sock.sendall(upload[1:])
            response = b""
            while True:
                chunk = sock.recv(4096)
                if not chunk:
                    break
                response += chunk
            sock.close()

            self.assertIn(b" 201 ", response.split(b"\r\n", 1)[0])
            self.assertEqual((server.share / "race.bin").read_bytes(), external)
            uploaded_files = [
                path for path in server.share.iterdir()
                if path.is_file() and path.read_bytes() == upload
            ]
            self.assertEqual(len(uploaded_files), 1)
            self.assertNotEqual(uploaded_files[0].name, "race.bin")

    def test_short_or_timed_out_upload_leaves_no_file_or_partial(self):
        with RunningServer(timeout=0.2) as server:
            sock = socket.create_connection(("127.0.0.1", server.port), timeout=2)
            request = (
                "POST /upload HTTP/1.1\r\nHost: localhost\r\nX-Saman-Token: test-token\r\n"
                "X-Filename: incomplete.bin\r\nContent-Length: 100\r\n\r\npartial"
            )
            sock.sendall(request.encode())
            time.sleep(0.5)
            sock.close()
            deadline = time.monotonic() + 2
            while time.monotonic() < deadline and list(server.share.rglob("*.part")):
                time.sleep(0.02)
            visible = [p for p in server.share.iterdir() if p.is_file() and not p.name.startswith(".")]
            self.assertEqual(visible, [])
            self.assertFalse(any(".part" in p.name for p in server.share.rglob("*")))

    def test_send_and_receive_modes_enforce_direction(self):
        with RunningServer("send") as server:
            (server.share / "file.txt").write_text("data")
            self.assertEqual(server.request("GET", "/file/file.txt", headers=server.auth_headers())[0], 200)
            self.assertEqual(server.request("POST", "/upload", b"x", server.auth_headers(**{"X-Filename": "x", "Content-Length": "1"}))[0], 403)
        with RunningServer("receive") as server:
            status, _, page = server.request("GET", "/", headers=server.auth_headers())
            self.assertEqual(status, 200)
            self.assertNotIn("Files on phone", page.decode())
            self.assertEqual(server.request("GET", "/file/file.txt", headers=server.auth_headers())[0], 403)
            self.assertEqual(server.request("POST", "/upload", b"x", server.auth_headers(**{"X-Filename": "x", "Content-Length": "1"}))[0], 201)

    def test_upload_size_content_length_transfer_encoding_and_space_limits(self):
        with RunningServer(max_bytes=4) as server:
            self.assertEqual(server.request("POST", "/upload", b"12345", server.auth_headers(**{"X-Filename": "big", "Content-Length": "5"}))[0], 413)
            conn = http.client.HTTPConnection("127.0.0.1", server.port, timeout=3)
            conn.putrequest("POST", "/upload")
            conn.putheader("X-Saman-Token", server.token)
            conn.putheader("X-Filename", "none")
            conn.endheaders()
            response = conn.getresponse()
            self.assertEqual(response.status, 411)
            response.read()
            conn.close()
            self.assertEqual(server.request("POST", "/upload", b"x", server.auth_headers(**{"X-Filename": "chunked", "Transfer-Encoding": "chunked"}))[0], 400)
        with RunningServer(reserve_bytes=2**63 - 1) as server:
            status, _, _ = server.request("POST", "/upload", b"x", server.auth_headers(**{"X-Filename": "full", "Content-Length": "1"}))
            self.assertEqual(status, 507)
            self.assertFalse((server.share / "full").exists())

    def test_listing_survives_files_disappearing_during_requests(self):
        with RunningServer() as server:
            stop = threading.Event()

            def churn():
                path = server.share / "racy.txt"
                while not stop.is_set():
                    path.write_bytes(b"x")
                    try:
                        path.unlink()
                    except FileNotFoundError:
                        pass

            thread = threading.Thread(target=churn)
            thread.start()
            try:
                statuses = [server.request("GET", "/", headers=server.auth_headers())[0] for _ in range(50)]
            finally:
                stop.set()
                thread.join()
            self.assertEqual(set(statuses), {200})

    def test_invalid_mode_is_rejected_without_starting(self):
        with tempfile.TemporaryDirectory() as share:
            proc = subprocess.run(
                [sys.executable, str(SERVER), share, "127.0.0.1", "0", "token", "invalid"],
                capture_output=True, text=True, timeout=3,
            )
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn("mode", (proc.stdout + proc.stderr).lower())

    def test_shell_menu_rejects_invalid_mode_before_launch(self):
        with tempfile.TemporaryDirectory() as home:
            marker = Path(home) / "python-called"
            script = f'''
source {shlex.quote(str(HERE.parent / "modules" / "share.sh"))}
s2_lan_ip() {{ printf '127.0.0.1'; }}
s2_err() {{ :; }}
s2_pause() {{ :; }}
s2_clear() {{ :; }}
s2_title() {{ :; }}
s2_warn() {{ :; }}
s2_have() {{ return 1; }}
python() {{ touch {shlex.quote(str(marker))}; }}
printf '9\\n' | s2_share_start
'''
            proc = subprocess.run(["bash", "-c", script], env={**os.environ, "HOME": home}, capture_output=True, text=True)
            self.assertNotEqual(proc.returncode, 0)
            self.assertFalse(marker.exists())

    def test_bind_conflict_and_ctrl_c_exit_cleanly(self):
        with RunningServer() as server:
            conflict = subprocess.run(
                [sys.executable, str(SERVER), tempfile.gettempdir(), "127.0.0.1", str(server.port), "token", "send"],
                capture_output=True, text=True, timeout=3,
            )
            self.assertNotEqual(conflict.returncode, 0)
            self.assertIn("already in use", (conflict.stdout + conflict.stderr).lower())
        self.assertEqual(server.proc.returncode, 0)
        self.assertIn("stopped", (server.stdout + server.stderr).lower())


if __name__ == "__main__":
    unittest.main(verbosity=2)
