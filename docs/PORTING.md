# Porting checklist

PRD section 7.6 requires this list. It names every place Audio Zone touches version-specific Minecraft code. When porting to a new Minecraft version (PRD 10.5), work through it top to bottom, then re-run the listed tests on every supported version.

Supported versions: **26.2, 26.3**. Each version gets its own jar from one source tree (Decisions Log, M0):

```
./gradlew build runClientGameTest                                                         # 26.3 (default)
./gradlew build runClientGameTest -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
```

### Where version-specific code goes

Most code compiles unchanged against every supported version, because each version gets its own jar. Only when the
*source code* has to differ does a class go in `src/main/versions/<minecraft_version>/java/`, one copy per version,
with the same class name and public API. Gradle adds the folder for the version being built and fails if it is
missing. Today that is `audiozone.mc.compat.NarrationCompat`, `audiozone.mc.compat.KeyEventCompat` and `audiozone.mc.compat.FolderCompat`. When adding a
version, copy the nearest version's folder and fix what does not compile.

## Version-sensitive touch points

Add a row whenever you add a Mixin, an access widener entry or a call into version-sensitive Minecraft API.

| Since | Touch point | Minecraft API used | Re-test |
| --- | --- | --- | --- |
| M0 | Open-screen key (`AudioZoneClient`) | `KeyMapping(String, int, KeyMapping.Category)`, `KeyMapping.Category.register(Identifier)`, Fabric `KeyMappingHelper`, `InputConstants.KEY_*` | M0 game test |
| M0 | Opening a screen (`AudioZoneClient.onEndTick`) | `Minecraft.gui.screen()`, `Minecraft.gui.setScreen(Screen)` | M0 game test |
| M0 | Main screen (`audiozone.mc.AudioZoneScreen`) | `Screen.extractRenderState(GuiGraphicsExtractor, …)`, `GuiGraphicsExtractor.centeredText` | M0 game test (screenshot) |
| M1 | **Mixin** `SoundEngineMixin`: sound start | `@Inject(cancellable = true)` into `SoundEngine.play(SoundInstance)` before its call to `calculateVolume(float, SoundSource)`; since M4 a muted sound returns `SoundEngine.PlayResult.NOT_STARTED` there | M1 and M4 game tests; log has no Mixin errors |
| M5 | **Mixin** `SoundEngineMixin`: volume | MixinExtras `@ModifyExpressionValue` on the same `calculateVolume(float, SoundSource)` call in `play` (start volume), and `@ModifyReturnValue` on `SoundEngine.calculateVolume(SoundInstance)`, which `tickInGameSound` and `refreshCategoryVolume` call. `play` must keep calling the two-argument method directly, or the factor would be applied twice | M5 game test (factors at start and on recalculation); log has no Mixin errors |
| M1 | Sound data (`audiozone.mc.SoundHook`) | `SoundInstance.getSound/getVolume/getAttenuation/isRelative/getIdentifier/getSource/getX/Y/Z`, `Sound.getAttenuationDistance()`, `SoundManager.getListenerTransform().position()` | M1 game test |
| M2 | **Drawing adapter** `audiozone.mc.ui.Draw`: all UI drawing | `GuiGraphicsExtractor.fill/outline/text/centeredText/enableScissor/disableScissor/setTooltipForNextFrame/textRendererForWidget`, `blitSprite(RenderPipelines.GUI_TEXTURED, …)` | M2 game test (screenshots) |
| M2 | UI kit widgets (`audiozone.mc.ui.Az*`) | Extend `AbstractButton` (`extractContents`, `extractDefaultSprite`, `extractDefaultLabel`), `AbstractSliderButton`, `EditBox`; narration via `updateWidgetNarration`/`createNarrationMessage`, `NarratedElementType`; `KeyEvent.key()/hasShiftDown()/hasControlDown()` with `InputConstants.KEY_*` | M2 game test |
| M2 | Screen base and dialog (`AzScreen`, `AzDialog`) | `Screen.init()` then vanilla calls `setInitialFocus()` (overridden), `extractBackground`, `addRenderableWidget`, `rebuildWidgets`, `getNarrationMessage`, `Minecraft.gui.setScreen` | M2 game test |
| M2 | Tooltip timing (`Tooltips`) | `AbstractWidget.setTooltipDelay`, `Minecraft.getLastInputType()`; relies on vanilla showing tooltips on hover or keyboard focus (`WidgetTooltipHolder`) | Manual: hover vs Tab |
| M2 | Icons | GUI atlas sprites at `assets/audiozone/textures/gui/sprites/icon/*.png` (made by `tools/make_icons.py`) | M2 screenshots |
| M2b | Scrolling list `AzList` | Extends `ContainerObjectSelectionList` (`replaceEntries`, `scrollAmount/setScrollAmount`, `scrollRate`, `scrollToEntry`, `getRowWidth`, `extractListBackground/Separators`, `nextFocusPath`, `narrateListElementPosition`); rows extend `ContainerObjectSelectionList.Entry` (`extractContent`, `children`, `narratables`, `getContentX/Y/Width/Height`); narration via `NarrationElementOutput.nest()` | M2b game test |
| M2b | Overlays and toasts in `AzScreen` | `Screen.children()` (overridden to trap focus), `addRenderableOnly`, `removeWidget`, `changeFocus(ComponentPath)`, `mouseClicked(MouseButtonEvent, boolean)`, `tick`, `Util.getMillis` | M2b game test |
| M2b | Dropdown, color picker, toast widgets | `AbstractButton` subclasses drawing through `Draw`; `GameNarrator.saySystemQueued` for the toast | M2b game test, screenshots |
| M3 | Recently played feed (`audiozone.mc.RecentLog`) | `SoundObservation` from the M1 hook, `SoundSource.getName()`, `Util.getMillis()`, Fabric `ClientPlayConnectionEvents.JOIN/DISCONNECT` | M3 game test |
| M3 | Recently Played tab (`audiozone.mc.AudioZoneScreen`) | `SoundManager.getSoundEvent(Identifier)` and `WeighedSoundEvents.getSubtitle()` for subtitles; vanilla translation keys `soundCategory.<name>` for category names; `Level.getGameTime()` in the test | M3 game test (screenshots) |
| M4 | Profile, Bypass, fail-open (`ProfileManager`, `SoundHook`, `BypassLabel`, `Notices`) | `FabricLoader.getConfigDir()`, Fabric `ClientLifecycleEvents.CLIENT_STARTED/STOPPING`, `HudElementRegistry.addLast`, `SystemToast.addOrUpdate(Minecraft.gui.toastManager(), …)` (the toast manager moved to `Gui` in 26.x), `KeyMapping.matches(KeyEvent)`, `KeyMapping.getTranslatedKeyMessage()` | M4 game test (screenshots) |
| M5 | Copy trace, Copy report (`TraceScreen`, `AudioZoneScreen`) | `Minecraft.keyboardHandler.setClipboard(String)` (Windows stores line breaks as CRLF on the clipboard) | M5b game test |
| M7 | Profile per world (`WorldSession`) | Fabric `ClientPlayConnectionEvents.JOIN/DISCONNECT`, `Minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT)`, `getWorldData().getLevelName()`, `Minecraft.getCurrentServer().ip`, `ClientLevel.dimension().identifier()` | M7 game test (world key, `audiozone_id.txt`) |
| M7 | **Mixin accessors** `SoundManagerAccessor` (`SoundManager.soundEngine`), `SoundEngineAccessor` (`SoundEngine.instanceToChannel`): per-tick re-check (`PlayingChannels`) | `ChannelAccess.ChannelHandle.execute(Channel::setVolume)`; vanilla removes a stopped channel from `instanceToChannel` some ticks after `stop`, and tracking follows that map | M7 game test (zone added and removed while a loop plays; stop) |
| M7 | Zone outlines (`ZoneOutlines`) | Fabric `LevelRenderEvents.END_EXTRACTION`; vanilla `Gizmos.line(Vec3, Vec3, int, float)`, `Gizmos.billboardText(String, Vec3, TextGizmo.Style)`, `GizmoProperties.setAlwaysOnTop()`; `GameRenderer.mainCamera().position()`. Vanilla drains the frame's gizmos in `LevelRenderer.submitFeatures`, so gizmos added in `BEFORE_GIZMOS` are not drawn | M7c game test (screenshot `m7c-outline-always` must show the sphere and its label) |
| M8 | **Mixin** `MinecraftMixin`: left-click marks a zone corner while marking (`ZoneMarking`) | `@Inject(cancellable = true)` at the head of `Minecraft.startAttack()Z` and `continueAttack(Z)V`, active only while marking | ZoneTools game test (left-click on the ground in Creative marks it and does not break it) |
| M2 | Version-specific: `NarrationCompat`, `KeyEventCompat` | `ScreenNarrationCollector.update` (26.3 adds a `NarrationTrigger`); `KeyEvent(int, int, int)` (second field is a scancode in 26.2, a layout keycode in 26.3) | M2 game test |
| M15 | Version-specific: `FolderCompat` (Open folder for exports and imports) | `Util.getPlatform().openPath(Path)` in 26.2; `Blaze3D.openPath(Path)` in 26.3 | Manual: Export → Open folder |
| M15 | File drop on the Profiles tab (`AudioZoneScreen.onFilesDrop`) | `Screen.onFilesDrop(List<Path>)` | ImportExport game test |
| M1 | Range formula (`audiozone.core.SoundRange`) | Must match vanilla: `max(volume, 1) × attenuation distance`, infinite if relative or `NONE`; audible when strictly closer (`Vec3.closerThan`, used by `SubtitleOverlay$Subtitle.isAudibleFrom`) | `SoundRangeTest`, M1 game test |

## Known differences between 26.2 and 26.3

Found at M0 by comparing the two game jars. Code that touches any of these needs a per-version implementation, or must use an API that exists in both.

| Area | 26.2 | 26.3 | Affects |
| --- | --- | --- | --- |
| Hearing range | Computed inline in `SoundEngine.play`: `max(volume, 1) × Sound.getAttenuationDistance()`, infinite if relative or attenuation `NONE` | Same formula, split into `Sound.getAttenuationDistance(float)` and `private static float SoundEngine.getRange(boolean, Attenuation, float)` | Audio Zone recomputes the range itself (`SoundRange`), so it does not depend on either |
| Subtitle listeners | Notified inline in `play` | Moved into `SoundEngine.notifyListeners(SoundInstance, WeighedSoundEvents, float)` | M1 log/range consistency, M9 cues |
| Resolving a sound | `SoundInstance.resolve(SoundManager)` | Renamed `getOrResolve(SoundManager)`; new `getSoundEvent()` | Anything reading the resolved sound |
| Key types | `InputConstants.Type.KEYSYM`, `SCANCODE`, `MOUSE` | `KEYBOARD`, `MOUSE` (`SCANCODE` removed) | Use `KeyMapping(String, int, Category)` and `InputConstants.getKey(String)`, which exist in both |
| Screen narration | `updateNarratorStatus(boolean)` | Takes a `NarrationTrigger` argument; new `scheduleNarration()` | M2 UI kit (narration) |
| World rendering | — | `LevelRenderer` changed substantially (75 signature lines differ) | M7 zone outlines |
| **Keyboard input** | GLFW codes: `KEY_TAB` = 258, `MOD_SHIFT` = 1, `MOD_CONTROL` = 2; `KeyEvent(key, scancode, modifiers)` | **SDL3** codes: `KEY_TAB` = 43, `MOD_SHIFT` = 3, `MOD_CONTROL` = 192; `KeyEvent(key, keycode, modifiers)`, and vanilla matches shortcuts on the new layout `keycode` (`shortcutKey()`), with `KEYCODE_*` constants | Always use `InputConstants` names (values are compiled per version), never numbers. Building `KeyEvent`s goes through `KeyEventCompat` |
| Render pipelines | `com.mojang.blaze3d.pipeline.RenderPipeline` | moved to `com.mojang.renderpearl.api.pipeline.RenderPipeline`; 26.3 also tries OpenGL then Vulkan | Source is unchanged as long as code names `RenderPipelines.GUI_TEXTURED` and never the type |
| Narration collector | `ScreenNarrationCollector.update(Consumer)` | `update(Consumer, NarrationTrigger)`; `NarrationElementOutput` gains `narrationTrigger()` | `NarrationCompat`; never implement `NarrationElementOutput` directly |
| Vanilla tab bar | `TabButton` is abstract; only vanilla's menu tab bar has a concrete one | same | Audio Zone uses its own `AzTabButton` |
| List narration | `ContainerObjectSelectionList` narrates the row under the mouse before the focused row | prefers the focused row when narration comes from the keyboard (`narrationTrigger()`) | `AzList` builds its own narration from the focused row, so both versions read the keyboard focus |
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
