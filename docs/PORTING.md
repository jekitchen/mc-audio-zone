# Porting checklist

PRD section 7.6 requires this list. It names every place Sound Sculptor touches version-specific Minecraft code. When porting to a new Minecraft version (PRD 10.5), work through it top to bottom, then re-run the listed tests on every supported version.

Supported versions: **26.2, 26.3** (`supported_minecraft_versions` in `gradle.properties`). One jar,
`soundsculptor-<version>+mc26.2-26.3.jar`, runs on all of them. It is still built and tested against each version:

```
./gradlew build runClientGameTest                                                         # 26.3 (default)
./gradlew build runClientGameTest -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
```

### One jar for every version

**Rule: main code must compile to the same bytes against every supported version.** Then the jar does not depend on
the version it was built against, and each version's game tests ran exactly the bytes that ship. CI builds every
version and fails if the jars differ (`same jar for every Minecraft version`); check by hand with:

```
java tools/CompareJars.java <jar built for 26.2> <jar built for 26.3>
```

It prints `IDENTICAL` or lists every entry that differs (the `-sources` jars are out of scope). The build already
keeps the build version out of the jar: its name, its `fabric.mod.json` version and `minecraft` range
(`["~26.2", "~26.3"]`: releases only, no snapshots or pre-releases) and the manifest's `Fabric-Minecraft-Version` all come from `supported_minecraft_versions`. Things
in code that make a build differ, and what to do instead:

- **Inlined constants.** javac copies `static final` primitives and strings into the class that uses them. Key codes
  are the trap: `InputConstants.KEY_*` are GLFW codes in 26.2 and SDL3 codes in 26.3, so a jar would have the right keys
  on one version only. Main code uses `soundsculptor.mc.compat.Keys` (values read from `InputConstants` at startup) and
  never `InputConstants.KEY_*`, `KEYCODE_*`, `MOD_*` or numbers; compare keys with if/else, since `switch` needs
  constants. Add a field to `Keys` (and to `OneJarClientGameTest`) when you need a new key.
- **A type that moved or changed.** A method whose parameter type moved package (`RenderPipeline` in 26.3) compiles to
  a different reference, and a nested class that became `final` changes the using class's `InnerClasses` entry
  (`Util.OS` in 26.3). Look such members up by name once, when the class loads, with
  `soundsculptor.mc.compat.RuntimeLookup`, and call the cached `MethodHandle` (`Draw.sprite`, `FolderCompat`). Never look
  up per frame or per sound.
- **An API that differs.** Pick at runtime, once, which method exists (`FolderCompat`: `Blaze3D.openPath` in 26.3,
  `Util.getPlatform().openPath` in 26.2), and keep one public API.

`OneJarClientGameTest` checks on every version that these lookups find what that version's compiler would have used.

Game-test code is not shipped, so test helpers whose source has to differ live in
`src/gametest/versions/<minecraft_version>/java/`, one copy per version with the same class names and public API:
today `soundsculptor.gametest.compat.NarrationCompat` and `KeyEventCompat`. Gradle adds the folder for the version
being built and fails if it is missing. Main code has no such folder.

**Adding a version:** add it to `supported_minecraft_versions` (keep them consecutive: the jar's range runs from the
oldest to the newest), to `modMenuVersions` in `build.gradle`, to the matrices in both workflows and to `ModJsonTest`;
copy the nearest `src/gametest/versions/` folder; build every version and run `CompareJars` until it prints
`IDENTICAL`.

## Version-sensitive touch points

Add a row whenever you add a Mixin, an access widener entry or a call into version-sensitive Minecraft API.

| Since | Touch point | Minecraft API used | Re-test |
| --- | --- | --- | --- |
| M0 | Open-screen key (`SoundSculptorClient`) | `KeyMapping(String, int, KeyMapping.Category)`, `KeyMapping.Category.register(Identifier)`, Fabric `KeyMappingHelper`, default keys from `Keys` | M0 game test |
| M0 | Opening a screen (`SoundSculptorClient.onEndTick`) | `Minecraft.gui.screen()`, `Minecraft.gui.setScreen(Screen)` | M0 game test |
| M0 | Main screen (`soundsculptor.mc.SoundSculptorScreen`) | `Screen.extractRenderState(GuiGraphicsExtractor, …)`, `GuiGraphicsExtractor.centeredText` | M0 game test (screenshot) |
| M1 | **Mixin** `SoundEngineMixin`: sound start | `@Inject(cancellable = true)` into `SoundEngine.play(SoundInstance)` before its call to `calculateVolume(float, SoundSource)`; since M4 a muted sound returns `SoundEngine.PlayResult.NOT_STARTED` there | M1 and M4 game tests; log has no Mixin errors |
| M5 | **Mixin** `SoundEngineMixin`: volume | MixinExtras `@ModifyExpressionValue` on the same `calculateVolume(float, SoundSource)` call in `play` (start volume), and `@ModifyReturnValue` on `SoundEngine.calculateVolume(SoundInstance)`, which `tickInGameSound` and `refreshCategoryVolume` call. `play` must keep calling the two-argument method directly, or the factor would be applied twice | M5 game test (factors at start and on recalculation); log has no Mixin errors |
| M1 | Sound data (`soundsculptor.mc.SoundHook`) | `SoundInstance.getSound/getVolume/getAttenuation/isRelative/getIdentifier/getSource/getX/Y/Z`, `Sound.getAttenuationDistance()`, `SoundManager.getListenerTransform().position()` | M1 game test |
| M2 | **Drawing adapter** `soundsculptor.mc.ui.Draw`: all UI drawing | `GuiGraphicsExtractor.fill/outline/text/centeredText/enableScissor/disableScissor/setTooltipForNextFrame/textRendererForWidget`, `blitSprite(RenderPipelines.GUI_TEXTURED, …)` through a `MethodHandle` found by name (the pipeline type moved in 26.3) | M2 game test (screenshots), OneJar game test |
| M2 | UI kit widgets (`soundsculptor.mc.ui.Az*`) | Extend `AbstractButton` (`extractContents`, `extractDefaultSprite`, `extractDefaultLabel`), `AbstractSliderButton`, `EditBox`; narration via `updateWidgetNarration`/`createNarrationMessage`, `NarratedElementType`; `KeyEvent.key()/hasShiftDown()/hasControlDown()` with `Keys` | M2 game test, OneJar game test |
| M2 | Screen base and dialog (`SculptScreen`, `SculptDialog`) | `Screen.init()` then vanilla calls `setInitialFocus()` (overridden), `extractBackground`, `addRenderableWidget`, `rebuildWidgets`, `getNarrationMessage`, `Minecraft.gui.setScreen` | M2 game test |
| M2 | Tooltip timing (`Tooltips`) | `AbstractWidget.setTooltipDelay`, `Minecraft.getLastInputType()`; relies on vanilla showing tooltips on hover or keyboard focus (`WidgetTooltipHolder`) | Manual: hover vs Tab |
| M2 | Icons | GUI atlas sprites at `assets/soundsculptor/textures/gui/sprites/icon/*.png` (made by `tools/make_icons.py`) | M2 screenshots |
| M2b | Scrolling list `SculptList` | Extends `ContainerObjectSelectionList` (`replaceEntries`, `scrollAmount/setScrollAmount`, `scrollRate`, `scrollToEntry`, `getRowWidth`, `extractListBackground/Separators`, `nextFocusPath`, `narrateListElementPosition`); rows extend `ContainerObjectSelectionList.Entry` (`extractContent`, `children`, `narratables`, `getContentX/Y/Width/Height`); narration via `NarrationElementOutput.nest()` | M2b game test |
| M2b | Overlays and toasts in `SculptScreen` | `Screen.children()` (overridden to trap focus), `addRenderableOnly`, `removeWidget`, `changeFocus(ComponentPath)`, `mouseClicked(MouseButtonEvent, boolean)`, `tick`, `Util.getMillis` | M2b game test |
| M2b | Dropdown, color picker, toast widgets | `AbstractButton` subclasses drawing through `Draw`; `GameNarrator.saySystemQueued` for the toast | M2b game test, screenshots |
| M3 | Recently played feed (`soundsculptor.mc.RecentLog`) | `SoundObservation` from the M1 hook, `SoundSource.getName()`, `Util.getMillis()`, Fabric `ClientPlayConnectionEvents.JOIN/DISCONNECT` | M3 game test |
| M3 | Recently Played tab (`soundsculptor.mc.SoundSculptorScreen`) | `SoundManager.getSoundEvent(Identifier)` and `WeighedSoundEvents.getSubtitle()` for subtitles; vanilla translation keys `soundCategory.<name>` for category names; `Level.getGameTime()` in the test | M3 game test (screenshots) |
| M4 | Profile, Bypass, fail-open (`ProfileManager`, `SoundHook`, `BypassLabel`, `Notices`) | `FabricLoader.getConfigDir()`, Fabric `ClientLifecycleEvents.CLIENT_STARTED/STOPPING`, `HudElementRegistry.addLast`, `SystemToast.addOrUpdate(Minecraft.gui.toastManager(), …)` (the toast manager moved to `Gui` in 26.x), `KeyMapping.matches(KeyEvent)`, `KeyMapping.getTranslatedKeyMessage()` | M4 game test (screenshots) |
| M5 | Copy trace, Copy report (`TraceScreen`, `SoundSculptorScreen`) | `Minecraft.keyboardHandler.setClipboard(String)` (Windows stores line breaks as CRLF on the clipboard) | M5b game test |
| M7 | Profile per world (`WorldSession`) | Fabric `ClientPlayConnectionEvents.JOIN/DISCONNECT`, `Minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT)`, `getWorldData().getLevelName()`, `Minecraft.getCurrentServer().ip`, `ClientLevel.dimension().identifier()` | M7 game test (world key, `soundsculptor_id.txt`) |
| M7 | **Mixin accessors** `SoundManagerAccessor` (`SoundManager.soundEngine`), `SoundEngineAccessor` (`SoundEngine.instanceToChannel`): per-tick re-check (`PlayingChannels`) | `ChannelAccess.ChannelHandle.execute(Channel::setVolume)`; vanilla removes a stopped channel from `instanceToChannel` some ticks after `stop`, and tracking follows that map | M7 game test (zone added and removed while a loop plays; stop) |
| M7 | Zone outlines (`ZoneOutlines`) | Fabric `LevelRenderEvents.END_EXTRACTION`; vanilla `Gizmos.line(Vec3, Vec3, int, float)`, `Gizmos.billboardText(String, Vec3, TextGizmo.Style)`, `GizmoProperties.setAlwaysOnTop()`; `GameRenderer.mainCamera().position()`. Vanilla drains the frame's gizmos in `LevelRenderer.submitFeatures`, so gizmos added in `BEFORE_GIZMOS` are not drawn | M7c game test (screenshot `m7c-outline-always` must show the sphere and its label) |
| M8 | **Mixin** `MinecraftMixin`: left-click marks a zone corner while marking (`ZoneMarking`) | `@Inject(cancellable = true)` at the head of `Minecraft.startAttack()Z` and `continueAttack(Z)V`, active only while marking | ZoneTools game test (left-click on the ground in Creative marks it and does not break it) |
| M2 | Game-test helpers per version (`src/gametest/versions/`): `NarrationCompat`, `KeyEventCompat` | `ScreenNarrationCollector.update` (26.3 adds a `NarrationTrigger`); `KeyEvent(int, int, int)` (second field is a scancode in 26.2, a layout keycode in 26.3) | M2 game test |
| M15 | `FolderCompat` (Open folder for exports and imports), chosen at runtime | `Util.getPlatform().openPath(Path)` in 26.2; `Blaze3D.openPath(Path)` in 26.3 | OneJar game test (method found); manual: Export → Open folder on each version |
| M15 | File drop on the Profiles tab (`SoundSculptorScreen.onFilesDrop`) | `Screen.onFilesDrop(List<Path>)` | ImportExport game test |
| M1 | Range formula (`soundsculptor.core.SoundRange`) | Must match vanilla: `max(volume, 1) × attenuation distance`, infinite if relative or `NONE`; audible when strictly closer (`Vec3.closerThan`, used by `SubtitleOverlay$Subtitle.isAudibleFrom`) | `SoundRangeTest`, M1 game test |

## Known differences between 26.2 and 26.3

Found at M0 by comparing the two game jars. Code that touches any of these needs a per-version implementation, or must use an API that exists in both.

| Area | 26.2 | 26.3 | Affects |
| --- | --- | --- | --- |
| Hearing range | Computed inline in `SoundEngine.play`: `max(volume, 1) × Sound.getAttenuationDistance()`, infinite if relative or attenuation `NONE` | Same formula, split into `Sound.getAttenuationDistance(float)` and `private static float SoundEngine.getRange(boolean, Attenuation, float)` | Sound Sculptor recomputes the range itself (`SoundRange`), so it does not depend on either |
| Subtitle listeners | Notified inline in `play` | Moved into `SoundEngine.notifyListeners(SoundInstance, WeighedSoundEvents, float)` | M1 log/range consistency, M9 cues |
| Resolving a sound | `SoundInstance.resolve(SoundManager)` | Renamed `getOrResolve(SoundManager)`; new `getSoundEvent()` | Anything reading the resolved sound |
| Key types | `InputConstants.Type.KEYSYM`, `SCANCODE`, `MOUSE` | `KEYBOARD`, `MOUSE` (`SCANCODE` removed) | Use `KeyMapping(String, int, Category)` and `InputConstants.getKey(String)`, which exist in both |
| Screen narration | `updateNarratorStatus(boolean)` | Takes a `NarrationTrigger` argument; new `scheduleNarration()` | M2 UI kit (narration) |
| World rendering | — | `LevelRenderer` changed substantially (75 signature lines differ) | M7 zone outlines |
| **Keyboard input** | GLFW codes: `KEY_TAB` = 258, `MOD_SHIFT` = 1, `MOD_CONTROL` = 2; `KeyEvent(key, scancode, modifiers)` | **SDL3** codes: `KEY_TAB` = 43, `MOD_SHIFT` = 3, `MOD_CONTROL` = 192; `KeyEvent(key, keycode, modifiers)`, and vanilla matches shortcuts on the new layout `keycode` (`shortcutKey()`), with `KEYCODE_*` constants | Main code uses `Keys` (read at runtime), never `InputConstants.KEY_*` or numbers, which javac inlines. Tests build `KeyEvent`s through `KeyEventCompat` |
| Render pipelines | `com.mojang.blaze3d.pipeline.RenderPipeline` | moved to `com.mojang.renderpearl.api.pipeline.RenderPipeline`; 26.3 also tries OpenGL then Vulkan | Any call taking a pipeline compiles to a different reference, so `Draw` finds `blitSprite` by name; main code never passes a `RenderPipeline` |
| Narration collector | `ScreenNarrationCollector.update(Consumer)` | `update(Consumer, NarrationTrigger)`; `NarrationElementOutput` gains `narrationTrigger()` | `NarrationCompat` (game tests only); never implement `NarrationElementOutput` directly |
| Vanilla tab bar | `TabButton` is abstract; only vanilla's menu tab bar has a concrete one | same | Sound Sculptor uses its own `SculptTabButton` |
| List narration | `ContainerObjectSelectionList` narrates the row under the mouse before the focused row | prefers the focused row when narration comes from the keyboard (`narrationTrigger()`) | `SculptList` builds its own narration from the focused row, so both versions read the keyboard focus |
| Fabric test input | `pressKey` sends `KeyEvent(key, 0, 0)` | sends the SDL keycode too; neither sends modifier bits | Tests send Shift/Ctrl combinations through `KeyEventCompat` |

Unchanged between the two versions: `SoundEngine.play(SoundInstance)` and both `calculateVolume` overloads, `SoundSource` (categories), `ChannelAccess`, `SoundEngineExecutor`, `com.mojang.blaze3d.audio.Channel`/`Library`/`Listener`, `KeyMapping` signatures, `AbstractWidget`, `AbstractButton`, `AbstractSliderButton`, `EditBox`, `Tooltip`, and every `GuiGraphicsExtractor` method the drawing adapter uses (text, fill, outline, scissor, tooltip).

### How to compare two versions

Loom caches each game jar at `~/.gradle/caches/fabric-loom/<version>/minecraft-merged.jar` after a build for that version. Compare class signatures with `javap` from JDK 25:

```
javap -p -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar net.minecraft.client.sounds.SoundEngine > a.txt
javap -p -cp ~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar net.minecraft.client.sounds.SoundEngine > b.txt
diff a.txt b.txt
```

Add `-c` to compare bytecode, for example to see whether a method body you inject into changed.
