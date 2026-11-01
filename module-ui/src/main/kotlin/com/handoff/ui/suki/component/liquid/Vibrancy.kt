// SPDX-License-Identifier: GPL-3.0-only
// Imported from SukiSU-Ultra 8e89c3797b91b957b15d1760fc57591adfecc563.
// Copyright SukiSU-Ultra contributors. Original Apache-2.0 notices retained below.
// Handoff changes: namespace, system theme and Compose frame clock. See LICENSING.md.
// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).
// Mirrored from compose-miuix-ui example.

package com.handoff.ui.suki.component.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
