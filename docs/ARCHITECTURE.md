# Architecture

## Layering

```
┌────────────────────────────────────────────────────────────────┐
│ UI layer                                                       │
│   ui/          Launcher Compose UI                             │
│   overlay/     OverlayService, HudSurfaceView, VMenuWindow,    │
│                effect windows, accessibility service           │
├────────────────────────────────────────────────────────────────┤
│ Engine layer (pure Kotlin, no windows/services)                │
│   core/module      Module, HudModule, ModuleManager            │
│   core/hud         HudElement, HudAnchor, HudFrame, paint      │
│   core/settings    Setting hierarchy (reactive, serializable)  │
│   core/config      ProfileManager, ProfileSync, DTOs           │
│   core/version     MinecraftVersion, VersionRegistry           │
│   core/signature   SignatureManager (trust store)              │
│   core/game        GameSession, GameDataSource impls           │
│   core/runtime     NativeBridge (JNI), ClientHooks, stats      │
│   core/logging     VLogger, CrashReporter                      │
│   core/isolation   IsolatedPaths                               │
├────────────────────────────────────────────────────────────────┤
│ Native runtime (C++20, libvclient.so)                          │
│   Runtime (init/shutdown) · Logger · CrashHandler              │
│   FpsStats · Math/WaypointMath/MotionBlur · Bridge ABI         │
└────────────────────────────────────────────────────────────────┘
```

Rules that keep this scalable:

1. **Modules never touch windows or services.** They implement `onTick` and (for HUD
   modules) a `HudElement`. Side effects are requested through `ClientHooks`
   (`zoomRequest`, `filterRequest`, `blurRequest`), which the running `OverlayService`
   implements. With no session running, hooks are null and modules degrade gracefully.
2. **The engine is Android-UI-free** (only `android.graphics` paint types in the HUD
   helpers). Unit tests run on the JVM without Robolectric.
3. **One composition root** (`VClientCore`) wires everything; no DI framework needed at
   this size, and dependency order is explicit in its `init` block.

## Frame pipeline

`HudSurfaceView` owns a single Choreographer loop (one per display refresh):

```
doFrame(nanos)
  ├─ dt = nanos - last
  ├─ stats = NativeBridge.frameStats(dt)   // native FpsStats (or Kotlin fallback)
  ├─ state = GameSession.poll(now)          // Manual / Demo / Bridge data source
  ├─ TickContext(dt, slowTick?, state, stats, demo)
  ├─ ModuleManager.tick(ctx)                // per-module try/catch error guard
  └─ postInvalidateOnAnimation()
onDraw(canvas)
  ├─ skip if game not focused (a11y) or session inactive
  ├─ HudFrame(canvas, density, state, stats, InputTracker.snapshot(), demo)
  ├─ for each enabled HudModule:
  │    measure -> anchor.resolve + offset(dp)*density -> rect -> draw
  │    (wrapped in the same error guard)
  └─ editor overlays when the HUD editor is active
```

`slowTick` fires ~2×/s for expensive polling (CPU `/proc/stat` deltas, battery,
`ActivityManager` memory) so the render loop stays cheap.

## Overlay window topology

| Window | Touch | Focus | Purpose |
|---|---|---|---|
| HUD surface | NOT_TOUCHABLE (touchable in editor mode) | no | HUD rendering |
| Floating bubble | touchable | no | Open V Menu (tap) / editor (long-press) |
| V Menu | touchable | **focusable** (keyboard input) | In-game GUI |
| V Menu backdrop | touchable (tap = close) | no | Dim + blur-behind (Android 12+) |
| Fullbright filter | NOT_TOUCHABLE | no | Translucent light filter |
| Motion blur | NOT_TOUCHABLE | no | Cross-window blur-behind, gyro-driven |
| Zoom button | touchable | no | Magnification toggle |

All windows are `TYPE_APPLICATION_OVERLAY` behind the `SYSTEM_ALERT_WINDOW` permission.
The game keeps receiving input except while V Menu or the HUD editor is open.

## Data flow for game state

`GameSession` polls a `GameDataSource` each frame:

- **ManualDataSource** — coordinates typed once in V Menu (works on any device; powers
  Waypoints distances, Coordinates, demo-free layouts).
- **DemoDataSource** — simulated walk with effects/armor/inventory; powers demo mode and
  HUD-editor previews.
- **BridgeDataSource** — inert by design in this build (see below).

`InputTracker` complements state with real input: on Android 14+ the accessibility
service forwards raw touchscreen `MotionEvent`s; calibrated rectangles map game controls
to virtual keys (Keystrokes) and every tap feeds the CPS counter.

## The Bridge (future native data path)

Both sides are stubbed so live data can arrive later without redesign:

```
[future transport] ──► vc::BridgeRuntime (C++ ABI) ──► JNI ──► BridgeDataSource
                                                                    │
                                                     GameState (position, effects, armor…)
                                                                    ▼
                                              modules & renderer (unchanged)
```

`cpp/vclient/Bridge.hpp` documents the contract; `BridgeDataSource.available` is the only
switch to flip once a transport exists. Nothing else in the codebase changes — modules
already consume `GameState`.

## Threading

- **Main thread**: overlay windows, Choreographer loop, module ticks, HUD draw, Compose.
  Everything on this path is allocation-light (shared paints, no per-frame allocations).
- **Handler (main)**: debounced profile auto-save (600 ms).
- **Unbounded IO happens nowhere at runtime**; profile writes are small atomic renames.
- Native logging mutex-protected; crash handler is async-signal-safe (open/write only).

## Failure containment

| Failure | Containment |
|---|---|
| Module tick/draw throws | `ModuleManager.guard` logs it; 5 consecutive errors → module auto-disabled, reported in V Menu |
| Module lifecycle hook throws | Toggle aborted, state unchanged |
| Overlay process death | Foreground service restarts (`START_STICKY`); session state derives from profiles |
| Repeated crashes on boot | `CrashReporter` history → **Safe Mode**: profile applies with all modules disabled |
| Native crash | `CrashHandler` tombstone in isolated `crashes/`, then default handling |
| Corrupt profile | JSON parse failure → defaults, error logged, app continues |
| Signature mismatch | Launch blocked with an explicit warning + re-trust flow |
