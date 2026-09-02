from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs, quote, unquote
from pathlib import Path
import html
import os
import sys

SHARE = Path(sys.argv[1]).resolve()
HOST = sys.argv[2]
PORT = int(sys.argv[3])
TOKEN = sys.argv[4]
MODE = sys.argv[5] if len(sys.argv) > 5 else "two-way"
MAX_BYTES = 20 * 1024**3
CHUNK = 1024 * 1024
SHARE.mkdir(parents=True, exist_ok=True)


def human(n: int) -> str:
    x = float(n)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if x < 1024 or unit == "TB":
            return f"{x:.1f} {unit}" if unit != "B" else f"{int(x)} B"
        x /= 1024
    return f"{n} B"


class Handler(BaseHTTPRequestHandler):
    server_version = "SamanShare/2.0"

    def token_ok(self):
        qs = parse_qs(urlparse(self.path).query)
        return qs.get("token", [""])[0] == TOKEN or self.headers.get("X-Saman-Token", "") == TOKEN

    def deny(self):
        body = b"Saman Share: invalid or missing access token."
        self.send_response(403)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_html(self, body):
        data = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def page(self):
        can_download = MODE in ("two-way", "send")
        can_upload = MODE in ("two-way", "receive")
        files_html = ""
        if can_download:
            rows = []
            for p in sorted(SHARE.iterdir(), key=lambda x: (not x.is_dir(), x.name.lower())):
                if not p.is_file() or p.name.startswith("saman-share-qr"):
                    continue
                qname = quote(p.name)
                rows.append(f'<li><a href="/file/{qname}?token={quote(TOKEN)}">{html.escape(p.name)}</a><span>{human(p.stat().st_size)}</span></li>')
            files_html = "<h2>Files on phone</h2><ul>" + ("".join(rows) or "<li>No files yet.</li>") + "</ul>"
        upload_html = ""
        if can_upload:
            upload_html = f'''<h2>Upload to phone</h2>
<input id="files" type="file" multiple>
<button onclick="uploadFiles()">Upload</button>
<div id="status"></div>
<script>
const token={TOKEN!r};
async function uploadFiles(){{
  const list=document.getElementById('files').files;
  const status=document.getElementById('status');
  if(!list.length){{status.textContent='Choose one or more files.';return;}}
  for(const f of list){{
    status.textContent='Uploading '+f.name+'…';
    const r=await fetch('/upload?token='+encodeURIComponent(token),{{method:'POST',headers:{{'X-Filename':encodeURIComponent(f.name),'Content-Type':'application/octet-stream'}},body:f}});
    if(!r.ok){{status.textContent='Upload failed: '+f.name;return;}}
  }}
  status.textContent='Upload complete ✓';
  setTimeout(()=>location.reload(),500);
}}
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
<h1>Saman Share 2</h1><div class="small">Mode: {html.escape(MODE)} · Token-protected LAN session</div>
{upload_html}{files_html}</div></body></html>'''

    def do_GET(self):
        if not self.token_ok():
            return self.deny()
        parsed = urlparse(self.path)
        if parsed.path == "/":
            return self.send_html(self.page())
        if parsed.path.startswith("/file/"):
            if MODE not in ("two-way", "send"):
                return self.send_error(403)
            name = os.path.basename(unquote(parsed.path[len("/file/"):]))
            file = (SHARE / name).resolve()
            if file.parent != SHARE or not file.is_file():
                return self.send_error(404)
            size = file.stat().st_size
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Disposition", f"attachment; filename*=UTF-8''{quote(file.name)}")
            self.send_header("Content-Length", str(size))
            self.end_headers()
            try:
                with file.open("rb") as f:
                    while True:
                        data = f.read(CHUNK)
                        if not data: break
                        self.wfile.write(data)
            except (BrokenPipeError, ConnectionResetError):
                pass
            return
        self.send_error(404)

    def do_POST(self):
        if not self.token_ok():
            return self.deny()
        if urlparse(self.path).path != "/upload" or MODE not in ("two-way", "receive"):
            return self.send_error(403)
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.send_error(411)
        if length <= 0 or length > MAX_BYTES:
            return self.send_error(413)
        raw_name = self.headers.get("X-Filename", "")
        name = os.path.basename(unquote(raw_name)).strip()
        if not name or name in (".", ".."):
            return self.send_error(400)
        dest = (SHARE / name).resolve()
        if dest.parent != SHARE:
            return self.send_error(400)
        if dest.exists():
            stem, suffix = dest.stem, dest.suffix
            i = 1
            while dest.exists():
                dest = SHARE / f"{stem} ({i}){suffix}"
                i += 1
        remaining = length
        try:
            with dest.open("wb") as f:
                while remaining:
                    data = self.rfile.read(min(CHUNK, remaining))
                    if not data: break
                    f.write(data)
                    remaining -= len(data)
            if remaining:
                dest.unlink(missing_ok=True)
                return self.send_error(400)
        except Exception:
            dest.unlink(missing_ok=True)
            raise
        body = f"Saved {dest.name}".encode()
        self.send_response(201)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        return


print(f"Saman Share 2 listening on http://{HOST}:{PORT}")
ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
