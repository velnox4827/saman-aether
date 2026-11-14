# Saman Tunnel v2.2.0 — config-store implementation contract

## Overview
Keep v2.1.0's native Kotlin/View UI and existing tunnel modes. Add subscription import and connection using a separate Xray module, retaining Aether 2.3.0 and minSdk 24 (Android 7).

## Goals / non-goals
- Import HTTPS subscriptions, VLESS/VMess/Trojan/Shadowsocks links and QR, with explicit errors for unsupported/invalid values. Never silently discard a node.
- Subscription list, selected node, manual/periodic refresh, usage/expiry only from returned metadata; missing metadata is unknown, not zero or unlimited.
- Reuse existing SOCKS5 1819 / HTTP 1820, HEV VPN, routing, diagnostics, dark/light tokens, Xiaomi service guidance and settings stores.
- Buy/test/renew opens https://t.me/TunnelYarBot. No payments, panel credentials, bot/database/server modifications, or hardcoded subscriber endpoint.
- Preserve original repository as backup. No Aether 2.4.0; no public release without approval.

## Technical design
UI -> ConfigStore (app-private atomic JSON) -> selected profile -> XrayConfig -> XrayRunner process -> loopback SOCKS -> existing HEV/VPN.
Aether modes continue their unchanged native-library path. Xray is packaged as a separate executable native asset; no new VpnService or second VPN. AetherService remains lifecycle/Binder owner and dispatches XRAY mode to XrayRunner.
Sub refresh uses Android JobScheduler, bounded network/time/size limits. QR uses a bundled decoder (offline, no remote image submission).

## Interface contract
- Pure Kotlin ConfigParser.parse(text): List<ImportedProfile>; ImportedProfile(name: String, outbound: JSONObject).
- ConfigParser.subscription(text): List<ImportedProfile> supports plain links and base64 link lists; errors name the entry/protocol but never echo secrets.
- XrayConfig.build(profile: ImportedProfile): JSONObject binds only loopback 1819/1820 and has no fallback direct route.
- SubscriptionInfo.parse(header: String?): SubscriptionInfo?; nullable upload/download/total/expire numeric values; units bytes and Unix seconds. Invalid/negative/overflow metadata is an explicit error.
- ConfigStore instance(context): subscriptions/nodes stored in app-private JSON with atomic writes, no plaintext URLs/UUIDs in logs or crash messages. Parent owns Android persistence/UI integration; parser implementation owns the data classes and parsing/config builder.
- Unsupported hysteria2/tuic imports produce an explicit unsupported message. Standard WireGuard INI imports use Xray's verified userspace WireGuard outbound (one peer; unsupported fields/multiple peers fail explicitly). Existing WARP WireGuard modes remain unchanged.

## Testing strategy
TDD for parsers/config/metadata, invalid encodings/ports/UUIDs/TLS/Reality fields, partial subscription failure, expiry and atomic persistence. Native Xray config validation with actual packaged-version CLI. Unit/lint/three-ABI APK CI, ZIP/SHA256/certificate continuity and old-mode regression tests. Runtime end-to-end requires a real bot-issued test URL/QR; never label synthetic fixtures as a real account test.

## Rollout / rollback
Work only on feat/v2.2.0-store-config in ~/saman-tunnel-v220, baseline tag v2.1.0. Original ~/saman-aether retained untouched. Review diffs, test, push branch, use existing hosted CI. Later create only a draft release with unchanged package/signature and versionCode 20200 after verified signed builds. Keep old prefs/profiles; new config store is additive.

## Open evidence / decisions
User explicitly chose Xray first, Aether 2.3.0, Android 7, and retaining backup. v2.2.0/draft/bot-link are brief defaults, awaiting any override. Deployed Mirza/3x-ui versions, production signing access and a real 48-hour bot test account are not yet verified. Aether 2.4.0 security delta is reported separately; no cherry-pick without a scoped review.
