# Changelog

## 1.1.0
- Profiles (YouTube-like picker at start), optional profile PIN, kids profile with a parents' whitelist (categories and single channels)
- Several named favourites lists per profile and account; move/sort lists and channels; rename and hide channels and categories; reset order
- Encrypted sync (AES-256-GCM, PBKDF2) of accounts, profiles, lists, order, names and history: home network (mDNS + direct exchange) and a private GitHub repository
- Player rebuilt: engine abstraction with ExoPlayer and libVLC (LGPL), automatic fallback to VLC per channel, de-interlacing, quality presets (auto/max/1080/720/480/360) and stream variants
- Zapping with debounce (saves connections), number entry, transparent in-player channel list (categories + channels), configurable info bar with resolution, fps, codecs, real stream rate, network estimate and data used
- Up/Down switch channels even while the control bar is visible; touch: swipe to zap, double-tap to seek
- Remote: focus enters lists at the first visible item; game-pad A/Y keys
- New colour palettes (calm navy + teal default, charcoal + gold, graphite + red), softer light theme, clearer dark theme; teal logo
- Arabic UI always with Western digits
- QR code for the phone page; account card buttons work with remote and touch
- Speed: binary library cache, background search index, per-ABI APKs (updater picks the right one), profile installer

## 1.0.0
- IPTV player for phones, tablets, Android TV, Google TV and Fire TV (Android 7.0+ / Fire OS 6+)
- Xtream Codes login (account info, expiry, connections) and M3U/M3U8 playlists (link or file); get.php links are detected as Xtream
- Live TV with categories, favorites, recently watched, channel zapping, programme guide (Xtream EPG or XMLTV)
- Catch-up / replay (Xtream timeshift and the common M3U catch-up types)
- Movies and series with posters, details, seasons, continue watching, next episode
- Media3 ExoPlayer: audio/subtitle/quality tracks, picture size, speed, picture-in-picture, external player (VLC, MX…)
- Recording of live channels (now or scheduled from the guide)
- Multiple accounts, parental PIN with locked categories (adult categories locked automatically)
- Search across channels, movies and series (Arabic-aware)
- In-app updates via GitHub Releases (same signature, installs over the old version, data kept)
- Splash with progress, version sash on the icon, Arabic (RTL) / English / German, light and dark
- Add accounts from the phone over the home network; live error log in the browser
