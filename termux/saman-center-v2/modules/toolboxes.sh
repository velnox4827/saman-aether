#!/data/data/com.termux/files/usr/bin/bash

s2_file_menu() {
    if p="$(s2_legacy saman-filebox 2>/dev/null)"; then "$p"; else s2_err "File Toolbox not found"; s2_pause; fi
}

s2_image_menu() {
    if p="$(s2_legacy saman-imagebox 2>/dev/null)"; then "$p"; else s2_err "Image Toolbox not found"; s2_pause; fi
}
