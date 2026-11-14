#!/usr/bin/env python3
"""Local VLESS transport fixture, not a bot-account or real-server acceptance test."""
import argparse
import http.server
import json
import pathlib
import socket
import subprocess
import tempfile
import threading
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xray', required=True)
    parser.add_argument('--generator-jar', required=True)
    parser.add_argument('--json-jar', required=True)
    args = parser.parse_args()
    # Do not disturb an existing local proxy owned by the user.
    for port in (1819, 1820):
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))
    processes = []
    class Endpoint(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'saman-local-vless-fixture')
        def log_message(self, *args):
            pass
    endpoint = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Endpoint)
    threading.Thread(target=endpoint.serve_forever, daemon=True).start()
    try:
        with tempfile.TemporaryDirectory(prefix='saman-xray-loopback-') as temp:
            root = pathlib.Path(temp)
            with socket.socket() as reservation:
                reservation.bind(('127.0.0.1', 0))
                server_port = reservation.getsockname()[1]
            uid = '123e4567-e89b-12d3-a456-426614174000'
            server = {'log': {'loglevel': 'debug'}, 'inbounds': [{'listen': '127.0.0.1', 'port': server_port,
                'protocol': 'vless', 'settings': {'clients': [{'id': uid}], 'decryption': 'none'}}],
                # The upstream core blocks loopback destinations by default; this test-only server
                # explicitly permits its local HTTP fixture. Production client has no direct outbound.
                'outbounds': [{'protocol': 'freedom', 'settings': {'finalRules': [{'action': 'allow', 'ip': ['127.0.0.1/32']}]}}]}
            (root / 'server.json').write_text(json.dumps(server))
            (root / 'fixture.link').write_text(f'vless://{uid}@127.0.0.1:{server_port}?encryption=none&security=none#LocalFixture')
            subprocess.run(['java', '-cp', args.generator_jar + ':' + args.json_jar, 'GenerateConfigFixtureKt',
                str(root / 'fixture.link'), str(root / 'client.json')], check=True)
            client = json.loads((root / 'client.json').read_text())
            client['log'] = {'loglevel': 'debug'}
            (root / 'client.json').write_text(json.dumps(client))
            for name in ('server', 'client'):
                subprocess.run([args.xray, 'run', '-test', '-config', str(root / (name + '.json'))],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=15)
                with (root / (name + '.log')).open('wb') as log:
                    processes.append(subprocess.Popen([args.xray, 'run', '-config', str(root / (name + '.json'))],
                        stdout=log, stderr=subprocess.STDOUT))
            command = ['curl', '--silent', '--fail', '--max-time', '3', '--noproxy', '',
                '--socks5-hostname', '127.0.0.1:1819', f'http://127.0.0.1:{endpoint.server_port}/']
            deadline = time.monotonic() + 8
            while True:
                result = subprocess.run(command, capture_output=True)
                if result.returncode == 0:
                    break
                if time.monotonic() >= deadline:
                    for name in ('server', 'client'):
                        print(name, (root / (name + '.log')).read_text()[-4000:])
                    raise AssertionError(f'VLESS loopback did not become ready: curl={result.returncode}, stderr={result.stderr.decode(errors="replace")[:800]}, core_exits={[p.poll() for p in processes]}')
                time.sleep(.1)
            assert result.stdout == b'saman-local-vless-fixture'
            processes[0].terminate()
            processes[0].wait(timeout=5)
            result = subprocess.run(command, capture_output=True)
            assert result.returncode != 0, 'Client bypassed failed VLESS server with a direct connection'
            print('PASS: imported VLESS -> generated Xray config -> actual SOCKS/VLESS HTTP transfer')
            print('PASS: stopping server fails closed; no direct fallback (local fixture only)')
    finally:
        for process in processes:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=5)
        endpoint.shutdown()
        endpoint.server_close()


if __name__ == '__main__':
    main()
