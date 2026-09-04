#!/usr/bin/env python3
"""Small, token-protected LAN file server used by Saman Center."""

from argparse import ArgumentParser
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, unquote, urlparse
import ctypes
import errno
import html
import hmac
import os
import shutil
import socket
import sys
import threading
import uuid

CHUNK = 1024 * 1024
DEFAULT_MAX_BYTES = 20 * 1024**3
DEFAULT_RESERVE_BYTES = 256 * 1024**2
DEFAULT_TIMEOUT = 30.0
DEFAULT_MAX_CONNECTIONS = 32
MODES = frozenset(("two-way", "send", "receive"))
INTERNAL_PREFIX = ".saman-share-"
MAX_FILENAME_BYTES = 255

_libc = ctypes.CDLL(None, use_errno=True)
_renameat2 = _libc.renameat2
_renameat2.argtypes = (
    ctypes.c_int,
    ctypes.c_char_p,
    ctypes.c_int,
    ctypes.c_char_p,
    ctypes.c_uint,
)
_renameat2.restype = ctypes.c_int
AT_FDCWD = -100
RENAME_NOREPLACE = 1


def rename_no_replace(source, destination):
    """Atomically publish source at destination, failing if it exists."""
    if _renameat2(
        AT_FDCWD,
        os.fsencode(source),
        AT_FDCWD,
        os.fsencode(destination),
        RENAME_NOREPLACE,
    ) != 0:
        error = ctypes.get_errno()
        raise OSError(error, os.strerror(error), destination)


class UploadSpaceError(Exception):
    """The upload cannot fit while retaining the configured free-space reserve."""


def env_int(name, default, *, minimum=0):
    raw = os.environ.get(name, str(default))
    try:
        value = int(raw)
    except ValueError as exc:
        raise ValueError(f"{name} must be an integer") from exc
    if value < minimum:
        raise ValueError(f"{name} must be at least {minimum}")
    return value


def env_float(name, default, *, minimum=0.0):
    raw = os.environ.get(name, str(default))
    try:
        value = float(raw)
    except ValueError as exc:
        raise ValueError(f"{name} must be a number") from exc
    if value <= minimum:
        raise ValueError(f"{name} must be greater than {minimum}")
    return value


def valid_name(raw):
    """Decode and validate one direct-child filename; never normalize it."""
    if not raw or len(raw) > 1024:
        return None
    try:
        name = unquote(raw, errors="strict")
    except UnicodeError:
        return None
    if (
        not name
        or name in (".", "..")
        or name.startswith(INTERNAL_PREFIX)
        or "/" in name
        or "\\" in name
        or any(ord(char) < 32 or ord(char) == 127 for char in name)
    ):
        return None
    try:
        if len(name.encode("utf-8")) > MAX_FILENAME_BYTES:
            return None
    except UnicodeError:
        return None
    return name


def collision_name(requested_name, index):
    if index == 0:
        return requested_name
    requested = Path(requested_name)
    stem, suffix = requested.stem, requested.suffix
    marker = f" ({index})"
    candidate = f"{stem}{marker}{suffix}"
    while len(candidate.encode("utf-8")) > MAX_FILENAME_BYTES:
        if stem:
            stem = stem[:-1]
        elif suffix:
            suffix = suffix[:-1]
        else:
            raise ValueError("collision filename is too long")
        candidate = f"{stem}{marker}{suffix}"
    return candidate


def human(number):
    value = float(number)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if value < 1024 or unit == "TB":
            return f"{value:.1f} {unit}" if unit != "B" else f"{int(value)} B"
        value /= 1024
    return f"{number} B"


class ShareHTTPServer(ThreadingHTTPServer):
    allow_reuse_address = True
    daemon_threads = True
    block_on_close = False
    request_queue_size = 32

    def __init__(self, address, handler, share, token, mode, max_bytes, reserve_bytes, read_timeout, max_connections):
        self.share = share
        self.token = token
        self.mode = mode
        self.max_bytes = max_bytes
        self.reserve_bytes = reserve_bytes
        self.read_timeout = read_timeout
        self.max_connections = max_connections
        self._connection_slots = threading.BoundedSemaphore(max_connections)
        self.parts = share / f"{INTERNAL_PREFIX}parts"
        self._reservation_lock = threading.Lock()
        self._reserved_names = set()
        self._reserved_bytes = 0
        super().__init__(address, handler)

    def process_request(self, request, client_address):
        if not self._connection_slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self._connection_slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self._connection_slots.release()

    def get_request(self):
        request, client_address = super().get_request()
        request.settimeout(self.read_timeout)
        return request, client_address

    def reserve_upload(self, requested_name, length):
        with self._reservation_lock:
            free = shutil.disk_usage(self.share).free
            if free - self._reserved_bytes - length < self.reserve_bytes:
                raise UploadSpaceError
            index = 0
            while True:
                name = collision_name(requested_name, index)
                destination = self.share / name
                if name not in self._reserved_names and not destination.exists():
                    self._reserved_names.add(name)
                    self._reserved_bytes += length
                    return name, destination
                index += 1

    def release_upload(self, name, length):
        with self._reservation_lock:
            self._reserved_names.discard(name)
            self._reserved_bytes = max(0, self._reserved_bytes - length)

    def publish_upload(self, part, requested_name, reserved_name):
        """Atomically publish a partial upload without replacing any path."""
        with self._reservation_lock:
            index = 0
            while True:
                name = collision_name(requested_name, index)
                if name != reserved_name and name in self._reserved_names:
                    index += 1
                    continue
                destination = self.share / name
                try:
                    rename_no_replace(part, destination)
                except FileExistsError:
                    index += 1
                    continue
                return name

    def handle_error(self, request, client_address):
        # Malformed/disconnected LAN clients should not print tracebacks in the UI.
        return


class Handler(BaseHTTPRequestHandler):
    server_version = "SamanShare/2.1"
    sys_version = ""

    def setup(self):
        super().setup()
        self.connection.settimeout(self.server.read_timeout)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        self.send_header("Pragma", "no-cache")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Content-Security-Policy", "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
        super().end_headers()

    def send_error(self, code, message=None, explain=None):
        self.close_connection = True
        super().send_error(code, message, explain)

    def token_matches(self, candidate):
        return bool(candidate) and hmac.compare_digest(candidate, self.server.token)

    def cookie_token(self):
        raw = self.headers.get("Cookie", "")
        if len(raw) > 4096:
            return ""
        cookie = SimpleCookie()
        try:
            cookie.load(raw)
        except Exception:
            return ""
        morsel = cookie.get("saman_token")
        return morsel.value if morsel else ""

    def token_ok(self):
        header_values = self.headers.get_all("X-Saman-Token", [])
        if len(header_values) == 1 and self.token_matches(header_values[0]):
            return True
        return self.token_matches(self.cookie_token())

    def bootstrap_token(self, parsed):
        if parsed.path != "/":
            return False
        values = parse_qs(parsed.query, keep_blank_values=True).get("token", [])
        return len(values) == 1 and self.token_matches(values[0])

    def redirect_after_bootstrap(self):
        self.send_response(303)
        self.send_header("Location", "/")
        self.send_header("Set-Cookie", f"saman_token={self.server.token}; Path=/; HttpOnly; SameSite=Strict")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def deny(self):
        body = b"Saman Share: invalid or missing access token."
        self.send_response(403)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.safe_write(body)

    def safe_write(self, data):
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError, socket.timeout, TimeoutError):
            self.close_connection = True

    def send_html(self, body):
        data = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.safe_write(data)

    def listing_html(self):
        rows = []
        try:
            entries = list(os.scandir(self.server.share))
        except OSError:
            entries = []
        for entry in entries:
            if entry.name.startswith(INTERNAL_PREFIX) or entry.name.startswith("saman-share-qr"):
                continue
            try:
                if not entry.is_file(follow_symlinks=False):
                    continue
                size = entry.stat(follow_symlinks=False).st_size
            except OSError:
                continue
            rows.append((entry.name.lower(), entry.name, size))
        rows.sort(key=lambda item: (item[0], item[1]))
        rendered = [
            f'<li><a href="/file/{quote(name, safe="")}">{html.escape(name)}</a><span>{human(size)}</span></li>'
            for _, name, size in rows
        ]
        return "<h2>Files on phone</h2><ul>" + ("".join(rendered) or "<li>No files yet.</li>") + "</ul>"

    def page(self):
        can_download = self.server.mode in ("two-way", "send")
        can_upload = self.server.mode in ("two-way", "receive")
        files_html = self.listing_html() if can_download else ""
        upload_html = ""
        if can_upload:
            upload_html = '''<h2>Upload to phone</h2>
<input id="files" type="file" multiple>
<button onclick="uploadFiles()">Upload</button>
<div id="status"></div>
<script>
async function uploadFiles(){
  const list=document.getElementById('files').files;
  const status=document.getElementById('status');
  if(!list.length){status.textContent='Choose one or more files.';return;}
  for(const f of list){
    status.textContent='Uploading '+f.name+'…';
    let r;
    try { r=await fetch('/upload',{method:'POST',headers:{'X-Filename':encodeURIComponent(f.name),'Content-Type':'application/octet-stream'},body:f}); }
    catch(e) { status.textContent='Upload failed: '+f.name; return; }
    if(!r.ok){status.textContent='Upload failed: '+f.name+' ('+r.status+')';return;}
  }
  status.textContent='Upload complete ✓';
  setTimeout(()=>location.reload(),500);
}
</script>'''
        return f'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Saman Share</title><style>
body{{font-family:system-ui,sans-serif;max-width:760px;margin:auto;padding:24px;background:#0f172a;color:#e2e8f0}}
.card{{background:#111827;border:1px solid #334155;border-radius:18px;padding:22px;box-shadow:0 10px 30px #0004}}
h1{{margin-top:0}} h2{{font-size:1.05rem;margin-top:28px}} ul{{list-style:none;padding:0}}
li{{display:flex;gap:12px;justify-content:space-between;padding:11px 0;border-bottom:1px solid #263244}}
a{{color:#7dd3fc;text-decoration:none;overflow-wrap:anywhere}} span{{color:#94a3b8;white-space:nowrap}}
button{{margin-top:12px;padding:10px 18px;border:0;border-radius:10px;font-weight:700}} input{{max-width:100%}}
.small{{color:#94a3b8;font-size:.9rem}}</style></head><body><div class="card">
<h1>Saman Share 2</h1><div class="small">Mode: {html.escape(self.server.mode)} · Token-protected LAN session</div>
{upload_html}{files_html}</div></body></html>'''

    def do_GET(self):
        parsed = urlparse(self.path)
        if self.bootstrap_token(parsed):
            return self.redirect_after_bootstrap()
        if not self.token_ok():
            return self.deny()
        if parsed.path == "/":
            return self.send_html(self.page())
        if parsed.path.startswith("/file/"):
            if self.server.mode not in ("two-way", "send"):
                return self.send_error(403)
            name = valid_name(parsed.path[len("/file/"):])
            if name is None:
                return self.send_error(400)
            file_path = self.server.share / name
            try:
                if not file_path.is_file() or file_path.is_symlink():
                    return self.send_error(404)
                size = file_path.stat().st_size
                source = file_path.open("rb")
            except OSError:
                return self.send_error(404)
            with source:
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Disposition", f"attachment; filename*=UTF-8''{quote(name, safe='')}")
                self.send_header("Content-Length", str(size))
                self.end_headers()
                while True:
                    try:
                        data = source.read(CHUNK)
                        if not data:
                            break
                        self.wfile.write(data)
                    except (BrokenPipeError, ConnectionResetError, socket.timeout, TimeoutError):
                        self.close_connection = True
                        break
            return
        self.send_error(404)

    def content_length(self):
        if self.headers.get("Transfer-Encoding") is not None:
            return None, 400
        values = self.headers.get_all("Content-Length", [])
        if len(values) != 1:
            return None, 411
        raw = values[0]
        if not raw.isascii() or not raw.isdigit() or len(raw) > 20:
            return None, 411
        length = int(raw)
        if length <= 0 or length > self.server.max_bytes:
            return None, 413
        return length, None

    def do_POST(self):
        if not self.token_ok():
            return self.deny()
        if urlparse(self.path).path != "/upload":
            return self.send_error(404)
        if self.server.mode not in ("two-way", "receive"):
            return self.send_error(403)
        length, error = self.content_length()
        if error:
            return self.send_error(error)
        names = self.headers.get_all("X-Filename", [])
        name = valid_name(names[0]) if len(names) == 1 else None
        if name is None:
            return self.send_error(400)
        try:
            reserved_name, destination = self.server.reserve_upload(name, length)
        except UploadSpaceError:
            return self.send_error(507, "Insufficient Storage")
        except (OSError, ValueError):
            return self.send_error(400)

        part = self.server.parts / f"{uuid.uuid4().hex}.part"
        remaining = length
        complete = False
        saved_name = reserved_name
        try:
            self.server.parts.mkdir(mode=0o700, exist_ok=True)
            flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
            with os.fdopen(os.open(part, flags, 0o600), "wb") as target:
                while remaining:
                    data = self.rfile.read(min(CHUNK, remaining))
                    if not data:
                        break
                    target.write(data)
                    remaining -= len(data)
                if remaining:
                    return self.send_error(400, "Incomplete upload")
                target.flush()
                os.fsync(target.fileno())
            saved_name = self.server.publish_upload(part, name, reserved_name)
            complete = True
        except (socket.timeout, TimeoutError):
            return self.send_error(408, "Upload timed out")
        except OSError:
            return self.send_error(507, "Could not store upload")
        finally:
            if not complete:
                try:
                    part.unlink()
                except FileNotFoundError:
                    pass
                except OSError:
                    pass
            self.server.release_upload(reserved_name, length)

        body = f"Saved {saved_name}".encode("utf-8")
        self.send_response(201)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.safe_write(body)

    def log_message(self, fmt, *args):
        return


def parse_args(argv=None):
    parser = ArgumentParser(description="Saman Share LAN server")
    parser.add_argument("share", type=Path)
    parser.add_argument("host")
    parser.add_argument("port", type=int)
    parser.add_argument("token", help="session token, or '-' to read SAMAN_SHARE_TOKEN")
    parser.add_argument("mode", nargs="?", default="two-way", choices=sorted(MODES))
    args = parser.parse_args(argv)
    if args.token == "-":
        args.token = os.environ.get("SAMAN_SHARE_TOKEN", "")
    if not 0 <= args.port <= 65535:
        parser.error("port must be between 0 and 65535")
    if not args.token or len(args.token) > 512 or any(ord(char) < 33 or ord(char) == 127 for char in args.token):
        parser.error("token must be 1-512 visible characters")
    return args


def main(argv=None):
    args = parse_args(argv)
    try:
        max_bytes = env_int("SAMAN_SHARE_MAX_BYTES", DEFAULT_MAX_BYTES, minimum=1)
        reserve_bytes = env_int("SAMAN_SHARE_RESERVE_BYTES", DEFAULT_RESERVE_BYTES)
        read_timeout = env_float("SAMAN_SHARE_TIMEOUT", DEFAULT_TIMEOUT)
        max_connections = env_int("SAMAN_SHARE_MAX_CONNECTIONS", DEFAULT_MAX_CONNECTIONS, minimum=1)
        share = args.share.expanduser().resolve()
        share.mkdir(parents=True, exist_ok=True)
        if not share.is_dir():
            raise ValueError("share path is not a directory")
    except (OSError, ValueError) as exc:
        print(f"Saman Share configuration error: {exc}", file=sys.stderr)
        return 2

    try:
        server = ShareHTTPServer(
            (args.host, args.port), Handler, share, args.token, args.mode,
            max_bytes, reserve_bytes, read_timeout, max_connections,
        )
    except OSError as exc:
        if exc.errno == errno.EADDRINUSE:
            print(f"Saman Share could not start: {args.host}:{args.port} is already in use.", file=sys.stderr)
        else:
            print(f"Saman Share could not bind {args.host}:{args.port}: {exc}", file=sys.stderr)
        return 1

    actual_port = server.server_address[1]
    print(f"Saman Share 2 listening on http://{args.host}:{actual_port}", flush=True)
    try:
        server.serve_forever(poll_interval=0.25)
    except KeyboardInterrupt:
        print("\nSaman Share stopped.", flush=True)
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
