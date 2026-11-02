# Source and distribution licenses

Handoff's existing code remains under the [Apache License 2.0](LICENSE).

This working tree also contains a GPL-3.0-only adaptation of SukiSU-Ultra's Material floating bottom bar:

- Source project: https://github.com/SukiSU-Ultra/SukiSU-Ultra
- Pinned revision: `8e89c3797b91b957b15d1760fc57591adfecc563`
- Original file: [`manager/app/src/main/java/com/sukisu/ultra/ui/component/bottombar/BottomBarMaterial.kt`](https://github.com/SukiSU-Ultra/SukiSU-Ultra/blob/8e89c3797b91b957b15d1760fc57591adfecc563/manager/app/src/main/java/com/sukisu/ultra/ui/component/bottombar/BottomBarMaterial.kt)
- Adapted file: [`module-ui/src/main/kotlin/com/handoff/ui/SukiMaterialNavigation.kt`](module-ui/src/main/kotlin/com/handoff/ui/SukiMaterialNavigation.kt)
- Copyright: SukiSU-Ultra contributors.
- Changes: Handoff destinations and callbacks; Haze backdrop integration; motion preferences; accessibility tags; large-font fallback. Root/native feature gates, badges and pager dependencies were omitted. This file remains as the reduced-motion/legacy fallback for the liquid-glass branch below.
- Full license: [GPL-3.0](LICENSES/GPL-3.0.txt).

The liquid-glass branch additionally imports `FloatingBottomBar.kt`, the four
`component/liquid/*.kt` helpers, `DampedDragAnimation.kt`, `InteractiveHighlight.kt`
and `DragGestureInspector.kt` from that same pinned revision, under
`module-ui/src/main/kotlin/com/handoff/ui/suki/`. Namespace imports, the system-theme
lookup and the frame-clock bridge are adapted for Handoff. Existing Apache-2.0
origin notices for compose-miuix-ui contributors and Kyant0/AndroidLiquidGlass
are retained. The copied SukiSU adaptations are included in this GPL-3.0 combination.
Original liquid-glass project: https://github.com/Kyant0/AndroidLiquidGlass .

**Combined applications containing this adaptation must be distributed under GPL-3.0, with corresponding source and the required notices.** The original Apache-2.0 files retain their license and attribution; Apache-2.0 is compatible with GPL-3.0 for this combination. The repository's historical Apache-only releases are unaffected.

The Miuix components are used through the `top.yukonga.miuix.kmp:miuix-ui-android:0.9.4` dependency, licensed under Apache-2.0 by compose-miuix-ui contributors. See https://github.com/miuix-kotlin-multiplatform/miuix . These public components are used directly; SukiSU's Miuix wrapper and native/root functionality are not copied.

License texts and attribution are packaged as APK assets from `LICENSES/`.

## v0.8.1 corresponding source

The v0.8.1 combined application is distributed under GPL-3.0. Its complete
project source, Gradle wrapper, build configuration, license texts and notices
are supplied as `Handoff-v0.8.1-source.zip` alongside the APK at:
https://github.com/gretae-King-1122/Handoff/releases/tag/v0.8.1 .
The same source is identified by Git tag `v0.8.1`. Build instructions are in
`README.md`; dependencies resolve from the repositories and versions specified
in the project. Private publishing keys, passwords and machine-local SDK paths
are not included. Without publishing credentials, build a Debug APK or supply
your own Release signing configuration.
