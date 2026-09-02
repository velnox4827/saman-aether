#!/data/data/com.termux/files/usr/bin/bash

s2_ffmpeg_progress() {
    local label="$1" duration="$2"; shift 2
    local key value pct status
    ffmpeg -hide_banner -loglevel error -nostdin -nostats -progress pipe:1 "$@" 2>"$SAMAN2_LOG/ffmpeg-last.log" |
    while IFS='=' read -r key value; do
        case "$key" in
            out_time_us|out_time_ms)
                pct="$(awk -v us="$value" -v d="$duration" 'BEGIN{if(d<=0){print 0;exit} p=(us/1000000)*100/d; if(p<0)p=0;if(p>100)p=100;printf "%d",p}')"
                s2_progress "$pct" "$label"
                ;;
        esac
    done
    status="${PIPESTATUS[0]}"
    printf '\r\033[K'
    [ "$status" -eq 0 ] && s2_progress 100 "$label" && printf ' ✓\n'
    return "$status"
}

s2_media_resize_mp() {
    s2_clear; s2_title "VIDEO RESIZE BY MEGAPIXELS"
    local input target_mp w h pixels nw nh duration base out
    read -r -p "Video path: " input
    input="$(s2_expand_path "$input")"
    [ -f "$input" ] || { s2_err "File not found: $input"; s2_pause; return 1; }
    echo
    echo "Presets: 2.07 ≈ 1080p | 0.92 ≈ 720p | 0.41 ≈ 480p"
    read -r -p "Target megapixels: " target_mp
    [[ "$target_mp" =~ ^[0-9]+([.][0-9]+)?$ ]] || { s2_err "Invalid megapixel value"; s2_pause; return 1; }
    local dims
    dims="$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0:s=x "$input" 2>/dev/null | head -n1)"
    w="${dims%x*}"; h="${dims#*x}"
    [[ "$w" =~ ^[0-9]+$ && "$h" =~ ^[0-9]+$ ]] || { s2_err "Could not read video dimensions"; s2_pause; return 1; }
    pixels=$((w*h))
    read -r _ nw nh < <(awk -v w="$w" -v h="$h" -v mp="$target_mp" 'BEGIN{
        target=mp*1000000; current=w*h; s=sqrt(target/current); if(s>1)s=1;
        nw=int((w*s)/2)*2; nh=int((h*s)/2)*2; if(nw<2)nw=2;if(nh<2)nh=2;
        printf "%.6f %d %d\n",s,nw,nh
    }')
    duration="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$input" 2>/dev/null | head -n1)"
    duration="${duration:-1}"
    base="${input%.*}"; out="${base}-$(printf '%s' "$target_mp" | tr '.' '_')MP.mp4"
    echo
    echo "Source : ${w}x${h} ($(awk -v p="$pixels" 'BEGIN{printf "%.2f",p/1000000}') MP)"
    echo "Output : ${nw}x${nh} (target ≤ $target_mp MP)"
    echo "Codec  : H.264 CRF 23 + AAC"
    echo "File   : $out"
    echo
    read -r -p "Start? [Y/n]: " go
    case "$go" in n|N) return 0;; esac
    if s2_ffmpeg_progress "Resizing video" "$duration" -y -i "$input" -map 0:v:0 -map '0:a:0?' -vf "scale=${nw}:${nh}" -c:v libx264 -crf 23 -preset medium -pix_fmt yuv420p -c:a aac -b:a 128k -movflags +faststart "$out"; then
        echo; s2_ok "Saved: $out"; s2_notify "Saman Media" "Video resize completed"
    else
        rm -f "$out"; echo; s2_err "ffmpeg failed"; tail -n 12 "$SAMAN2_LOG/ffmpeg-last.log" 2>/dev/null || true
    fi
    s2_pause
}

s2_media_menu() {
    while true; do
        s2_clear; s2_title "MEDIA CENTER"
        echo "1) Open existing Media Toolbox"
        echo "2) Resize / compress by megapixels  NEW"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) if p="$(s2_legacy media 2>/dev/null)"; then "$p"; else s2_err "Media Toolbox not found"; s2_pause; fi ;;
            2) s2_media_resize_mp ;;
            0) return ;;
        esac
    done
}
