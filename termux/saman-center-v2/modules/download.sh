#!/data/data/com.termux/files/usr/bin/bash

s2_download_open() {
    local p
    if p="$(s2_legacy sdm 2>/dev/null)"; then
        "$p"
    else
        s2_err "SDM not found."
        s2_pause
    fi
}

s2_download_url() {
    local url="${1:-}"
    [ -n "$url" ] || { read -r -p "URL: " url; }
    [ -n "$url" ] || return 1
    case "$url" in
        *youtube.com/*|*youtu.be/*|*youtube-nocookie.com/*)
            if p="$(s2_legacy sdm 2>/dev/null)"; then "$p" --youtube-share "$url"; else return 1; fi
            ;;
        *)
            if p="$(s2_legacy dl 2>/dev/null)"; then "$p" "$url"; else
                mkdir -p "$SAMAN2_DOWNLOADS"
                aria2c --dir="$SAMAN2_DOWNLOADS" --continue=true --max-connection-per-server=8 --split=8 --file-allocation=none "$url"
            fi
            ;;
    esac
}

s2_download_menu() {
    while true; do
        s2_clear; s2_title "DOWNLOAD CENTER"
        echo "1) Open SDM"
        echo "2) Download a URL"
        echo "3) Download URL from clipboard"
        echo "4) Open download folder"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) s2_download_open ;;
            2) s2_download_url; s2_pause ;;
            3) if s2_have termux-clipboard-get; then u="$(termux-clipboard-get 2>/dev/null || true)"; s2_download_url "$u"; else s2_err "Clipboard API unavailable"; fi; s2_pause ;;
            4) mkdir -p "$SAMAN2_DOWNLOADS"; if s2_have termux-open; then termux-open "$SAMAN2_DOWNLOADS" >/dev/null 2>&1 || true; fi; ls -lah "$SAMAN2_DOWNLOADS" | tail -n 30; s2_pause ;;
            0) return ;;
        esac
    done
}
