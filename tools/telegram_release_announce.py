#!/usr/bin/env python3
"""Send a Saman Tunnel release announcement to Telegram.

Stdlib only. Reads secrets from the environment, never prints the token.
Idempotent: skips when the release body already carries the announce marker.
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

MARKER = "<!-- saman-tunnel-telegram-announced -->"
API = "https://api.telegram.org"


def html_escape(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def build_message(tag: str, release_url: str, notes: str, assets: list[str]) -> str:
    lines = [
        f"🚀 Saman Tunnel {html_escape(tag)}",
        "",
        "نسخه جدید منتشر شد.",
        "",
        "تغییرات:",
    ]
    for bullet in notes.splitlines():
        bullet = bullet.strip()
        if bullet.startswith("- "):
            lines.append(f"• {html_escape(bullet[2:])}")
    lines += ["", "دانلود:"]
    for name in assets:
        if name.endswith(".apk"):
            lines.append(f"• {html_escape(name)}")
    lines += [
        "",
        f"Release: {html_escape(release_url)}",
        "",
        "فقط از Release رسمی GitHub دانلود کنید و checksum را بررسی کنید.",
    ]
    return "\n".join(lines)


def post_message(token: str, chat_id: str, thread_id: str | None, text: str) -> None:
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": True,
    }
    if thread_id:
        payload["message_thread_id"] = thread_id
    data = json.dumps(payload).encode("utf-8")
    last_error: Exception | None = None
    for attempt in range(3):
        request = urllib.request.Request(
            f"{API}/bot{token}/sendMessage",
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                result = json.loads(response.read().decode("utf-8"))
            if result.get("ok"):
                return
            last_error = RuntimeError(result.get("description", "Telegram rejected message"))
        except (urllib.error.URLError, TimeoutError, RuntimeError) as error:
            last_error = error
        time.sleep(2 ** attempt)
    raise SystemExit(f"Telegram send failed: {last_error}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--release-url", required=True)
    parser.add_argument("--notes-file", required=True)
    parser.add_argument("--assets-file", required=True, help="JSON list of asset names")
    parser.add_argument("--release-body-file", default="", help="Current release body for idempotency check")
    args = parser.parse_args()

    token = os.environ.get("TELEGRAM_BOT_TOKEN", "")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "")
    thread_id = os.environ.get("TELEGRAM_THREAD_ID", "") or None
    if not token or not chat_id:
        raise SystemExit("TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID are required")

    if args.release_body_file:
        with open(args.release_body_file, encoding="utf-8") as handle:
            if MARKER in handle.read():
                print("already announced; skipping")
                return

    with open(args.notes_file, encoding="utf-8") as handle:
        notes = handle.read()
    with open(args.assets_file, encoding="utf-8") as handle:
        assets = json.load(handle)

    text = build_message(args.tag, args.release_url, notes, assets)
    post_message(token, chat_id, thread_id, text)
    print(f"announced {args.tag}")


if __name__ == "__main__":
    main()
