# Data isolation policy

V Client's isolation contract, in one sentence: **V Client reads only Minecraft's package
metadata through PackageManager, and writes only inside its own sandbox.**

## What V Client reads

| Read | API | Purpose |
|---|---|---|
| `com.mojang.minecraftpe` package info (versionName) | `PackageManager.getPackageInfo` | Version compatibility |
| The game's signing certificate bytes | `PackageManager` (`GET_SIGNING_CERTIFICATES`) | Signature verification (SHA-256 digest) |
| The game's launch intent | `PackageManager.getLaunchIntentForPackage` | Launching the stock game |

No game files, worlds, or account data are ever read.

## What V Client writes — and where

Everything lives under a single root, `Context.filesDir/vclient/` (plus the standard
private `SharedPreferences` file for small session settings):

```
<vclient>/
├── profiles/<name>/profile.json   Modules, settings, HUD layout, waypoints
├── logs/app-YYYYMMDD.log          VLogger output (rotated at 2 MiB)
├── logs/native.log                Native runtime log (rotated at 512 KiB)
├── crashes/crash-*.txt            Uncaught-exception reports
├── crashes/native-crash-*.txt     Native tombstones
├── crashes/crash-history.json     Crash-loop detection (safe mode)
├── signatures/trusted.json        Pinned Minecraft certificate digests
├── compat/user_versions.json      User-imported version registry overrides
└── cache/                         Throw-away files (wipeable from Settings)
```

Consequences:

- Deleting that directory (or uninstalling V Client) removes **all** V Client data.
- Minecraft's own storage (`Android/data/com.mojang.minecraftpe/…`) is never touched —
  not for configs, not for caches, not for anything.
- The Minecraft APK is never modified, re-signed, or repackaged. V Client launches the
  installed package's own main activity through the standard Android intent mechanism.
- `allowBackup=false`: V Client data stays out of cloud backups unless you flip it.
- No network permission is requested at all.

## Overlay behavior

The HUD, V Menu and effect windows are `TYPE_APPLICATION_OVERLAY` windows inside V
Client's own process — they render above the game but cannot read the game's screen
pixels, memory or input (except the explicit, optional accessibility motion-event feed,
which sees raw touch coordinates only, with `canRetrieveWindowContent="false"`).

## Zoom & accessibility

Zoom uses Android's magnification controller through the accessibility service — a
system-level camera zoom, not game injection. The service watches window-change package
names (to hide the HUD when Minecraft isn't focused) and, on Android 14+, raw touch
motion (for Keystrokes region mapping and CPS). It never reads screen content or text.
