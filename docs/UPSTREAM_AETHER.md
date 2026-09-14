# Saman + Official Upstream Aether

Starting with **Saman Termux 1.8.1**, Saman no longer installs or maintains a patched Aether core.

## Architecture

- Aether is installed and updated independently in Termux.
- Saman resolves the existing `aether` command from `PATH`.
- Saman only provides ready-made presets for:
  - WireGuard
  - GOOL
  - MASQUE H3
  - MASQUE H2
  - Tor through the selected transport (`--tor`)
  - the tunnel through Tor (`--tor-reverse`, MASQUE H2 only)
  - Tor alone (`--tor-only`)
- The Aether executable is never copied, patched, overwritten, or removed by Saman.
- `$PREFIX/bin/saman-aether-core` is only a compatibility symlink to the official `aether` command so older Saman diagnostics continue to work.
- The installed upstream CLI does not advertise Psiphon; Saman therefore does not expose a Psiphon mode or settings menu.
- The verified capability matrix for the current installation is maintained in `docs/UPSTREAM_AETHER_MATRIX.md`.

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

## Tor support

Tor is supplied only by an official Aether release built with the `tor` feature.
Saman checks the live CLI and the binary's compiled Arti markers before enabling
the Tor menu. It passes through only official flags and keeps bridge lines and
custom pluggable-transport paths private in summaries:

```text
--tor             Tor inside the selected WARP transport
--tor-reverse     MASQUE/H2 through Tor
--tor-only        Tor without a WARP tunnel
```

Official Android releases include the `pt/` directory beside `aether`. Saman's
updater verifies the archive checksum, validates its paths and entry types, and
installs or rolls back `aether` and `pt/` as one managed pair. It never patches,
rebuilds, or replaces the upstream networking implementation.

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
