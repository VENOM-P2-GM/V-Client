# Extending V Client

## Adding a module

1. Create a class in `app/src/main/java/dev/vclient/modules/`:

```kotlin
class HitDelayModule : HudModule(ID, NAME, "Shows your attack cooldown.") {

    // Settings: reactive, auto-persisted, auto-rendered in V Menu & launcher.
    private val showPercent by settingOf(BoolSetting("show_percent", "Show percent", true))

    // Optional per-frame work (state sampling, computation).
    override fun onTick(ctx: TickContext) { /* ... */ }

    // The HUD element: measure + draw on the overlay canvas.
    override val hud = Element()

    private inner class Element : HudElement(ID) {
        override fun measure(frame: HudFrame): SizeF =
            HudTexts.measureSized(frame, listOf("Cooldown 100%"), textSizePx(frame.density))

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            HudTexts.draw(canvas, frame, rect, listOf("Cooldown 100%"),
                textSizePx(frame.density), textColorValue(), textShadowEnabled())
        }
    }

    companion object { const val ID = "hit_delay"; const val NAME = "Hit Delay" }
}
```

2. Register it in `ModuleRegistry.createAll()` — one line.

That's it. Registration gives you: V Menu + launcher UI (rows, detail page, search,
category grouping), profile persistence (enabled state + every setting + HUD layout),
the per-module crash guard, and HUD-editor drag/scale.

### Non-visual (effect) modules

Extend `Module` directly and drive side effects through `ClientHooks` — see
`ZoomModule`, `FullbrightModule`, `MotionBlurModule`. Declare a `var xxxRequest:
((Boolean) -> Unit)?` in `ClientHooks`, set it in `OverlayService.wireHooks()`, invoke
it from `onEnabled()`/`onDisabled()`.

### Custom settings

All setting types live in `core/settings/Settings.kt`. To add one:

1. Subclass `Setting<T>` (id, name, description, default + reactive value).
2. Add a branch to `encodeJson()` / `decodeJson()` in the same file.
3. Add a branch to `SettingRow` in `ui/widgets/SettingWidgets.kt`.

Profiles stay forward/backward compatible automatically (unknown ids are ignored,
invalid values fall back to defaults).

## Adding HUD features to the editor

The editor manipulates `HudElement.anchor / offsetX / offsetY / scale` generically, so
new HUD elements are editable with zero extra code. For custom editor affordances,
extend `HudEditorController.drawOverlays()` and `onTouch()`.

## Supporting a new Minecraft version

Overlay features are version-independent (nothing hooks the game), so "supporting" a
release means *validating* it:

- **Ship-time**: add an entry to `app/src/main/assets/compat/versions.json`
  (`{"version": "1.22.0", "status": "supported"}`).
- **At runtime (no app update)**: drop a `user_versions.json` with the same shape into
  `<isolated>/compat/` — `VersionRegistry.loadUserOverrides()` merges it over the
  bundled list.

Versions newer than the newest registry entry resolve to *Experimental* automatically,
with a note that overlay features should still work. `MinecraftVersion` compares
segments numerically (`1.21.100 > 1.21.9`), so ordering needs no maintenance.

## Adding a data source

Implement `GameDataSource` (a `poll(nowMs): GameState`), add a `DataSourceKind`, and
wire it in `GameSession.applyDataSource()`. `BridgeDataSource` shows where a future
native/Shizuku transport plugs in; see ARCHITECTURE.md#the-bridge.

## Native runtime

`cpp/vclient/` is plain C++20 behind one JNI object (`NativeBridge`). To add a native
helper: implement it under `vclient/`, add the `external fun` + wrapper in
`NativeBridge.kt`, and the `Java_dev_vclient_core_runtime_NativeBridge_*` binding in
`vclient_jni.cpp`. Keep JNI thin — logic belongs in `vc::` classes so it can be
unit-checked on the desktop (`g++ -std=c++20 -fsyntax-only`).

## Conventions

- Module ids are stable snake_case strings — they key profile state; never rename one.
- Setting ids are stable per module; changing defaults is safe, renaming ids orphans
  persisted values (old value ignored, default used).
- Everything user-visible writes to `IsolatedPaths` — never to shared storage.
