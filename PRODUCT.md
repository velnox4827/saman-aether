# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Android users who need to connect through Saman Tunnel's supported tunneling protocols and local proxy modes.

## Product Purpose

Saman Tunnel provides Android access to Aether-based tunnel protocols, Android VPN/TUN routing, app routing, and local SOCKS5/HTTP proxy endpoints.

## Capabilities and Constraints

- Preserve all existing working capabilities; the UI may relocate controls to match the approved HTML v4 reference, but must not remove functionality.
- UI must be native Android; WebView is prohibited.
- Preserve real protocol/service state and backend actions. Replace visual prototype behavior with real Android actions; never ship fake connection state, random IPs, or simulated production behavior.
- Supported features include MASQUE H2/H3, MIM, WireGuard, GOOL, Psiphon, Tor, SOCKS5/HTTP proxy, VPN/TUN/HEV, app routing, diagnostics, logs, notifications, and update/about flows, as implemented by the repository.
- Egress lookup must use the local SOCKS5 proxy and display the real IP, country, and flag. If country is unavailable, show the real IP, country `—`, and flag `🌐`.
- Visual target is the user-provided HTML v4 in `HERMES_SAMAN_TUNNEL_V4_MASTER_UI_DEBUG.md`; implement its glass/orb background, connection hero, settings bottom sheet, themes, palettes, and responsive layout as closely as native Android permits.

## Evidence on Hand

- Native Android source and existing backend implementation in this repository.
- User-provided HTML v4 design reference in `HERMES_SAMAN_TUNNEL_V4_MASTER_UI_DEBUG.md`.
- No fabricated testimonials, performance claims, or connection data.

## Product Principles

- UI state reflects actual backend state.
- Preserve existing functionality while matching the approved visual reference.
- Prefer native Android behavior and accessibility over web-only mechanics.
- Never present simulated networking results as real.

## Accessibility & Inclusion

Use native Android touch targets, system font scaling, system Back behavior, and light/dark themes.

## Stack

Existing native Android Kotlin application; preserve current project stack.

## Brand Commitments

Product name: SAMAN TUNNEL. Preserve the user-provided v4 wording, visual structure, and palettes while translating web-only interactions into real native Android controls.