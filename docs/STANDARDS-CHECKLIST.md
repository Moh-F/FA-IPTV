# app-standards checklist (status of this project)

| Item | Status |
|---|---|
| Stack | Native Kotlin + Jetpack Compose + Media3 ExoPlayer (Android/TV app) |
| App icon + TV banner | Drawn at build time (`buildSrc/IconGen.java`): gradient TV + play mark, "FA" wordmark |
| Diagonal version sash | On the launcher icon, splash logo and TV banner (generated with the real version), plus corner ribbon in the app (switchable) |
| Splash with icon, progress bar, % counter (real steps) | Done (`ui/Root.kt` Splash) |
| Light/dark | System / dark / light (TV defaults to dark) |
| ar (RTL) / en / de, per-app language in Android settings | Done (`locales_config.xml`, AppCompat locales, in-app picker) |
| Update check at start, SHA-256, install over old version | Done (`core/Updater.kt`, CI creates `latest.json`) |
| Update modes | Ask / auto-download then ask; forced via `mandatory`/`minBuild` in latest.json |
| Same signature, no data loss | Own keystore `fa-iptv` + 4 GitHub secrets (see README) |
| Error log, rotating, redacted, LAN browser viewer | Done (`AppLog.kt`, `LanServer.kt`); off by default; share log from settings |
| Privacy / GDPR | No data collected; `PRIVACY.md` |
| Tests + CI | Unit tests (M3U parser, catch-up URLs, Xtream JSON, updater, redaction); CI on every push, signed release on tags |
| TV focus / D-pad + touch | Focus ring + zoom on every card, Menu key = long press, player fully remote-controlled |
| Sync | Home network + private GitHub repo, end-to-end encrypted |
| Not yet | Golden/UI tests, bundled fonts, EPG timeline grid, Baseline Profile generated on a device |
