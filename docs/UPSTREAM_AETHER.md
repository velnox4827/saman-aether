# Saman + Official Upstream Aether

Starting with **Saman Termux 1.7.0**, Saman no longer installs or maintains a patched Aether core.

## Architecture

- Aether is installed and updated independently in Termux.
- Saman resolves the existing `aether` command from `PATH`.
- Saman only provides ready-made presets for:
  - WireGuard
  - GOOL
  - MASQUE H3
  - MASQUE H2
- The Aether executable is never copied, patched, overwritten, or removed by Saman.
- `$PREFIX/bin/saman-aether-core` is only a compatibility symlink to the official `aether` command so older Saman diagnostics continue to work.

## Update behavior

If you update Aether itself, for example so that `aether --version` changes, the next Saman launch uses that same updated executable automatically. No Saman rebuild is required.

Check routing with:

```bash
bash install.sh check
readlink -f "$PREFIX/bin/saman-aether-core"
command -v aether
aether --version
```

The resolved compatibility path and the official Aether executable should point to the same binary.

## Install / update

A working official `aether` command must already exist in Termux.

Then install or update Saman normally:

```bash
bash install.sh install
# or
bash install.sh update
```

You can explicitly select the official Aether command without modifying it:

```bash
SAMAN_AETHER_BIN="$PREFIX/bin/aether" bash install.sh update
```

## Uninstall

`bash install.sh uninstall` removes only Saman integration files. The official Aether installation remains untouched.
