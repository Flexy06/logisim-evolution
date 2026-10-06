/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.modern

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.formdev.flatlaf.FlatLaf
import javax.swing.UIManager

private fun uiColor(vararg keys: String): Color? {
  for (key in keys) {
    val c = UIManager.getColor(key) ?: continue
    return Color(c.red, c.green, c.blue, c.alpha)
  }
  return null
}

/**
 * Builds a Material 3 color scheme from the active FlatLaf theme, so Compose and Swing parts of
 * the window look like one app (same background, foreground and accent color).
 */
internal fun currentColorScheme(): ColorScheme {
  val dark = runCatching { FlatLaf.isLafDark() }.getOrDefault(false)
  val base = if (dark) darkColorScheme() else lightColorScheme()
  val bg = uiColor("Panel.background") ?: base.background
  val fg = uiColor("Label.foreground") ?: base.onBackground
  val accent =
      uiColor("Component.accentColor", "Component.focusColor", "Button.default.background")
          ?: base.primary
  val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White

  return base.copy(
      primary = accent,
      onPrimary = onAccent,
      background = bg,
      onBackground = fg,
      surface = bg,
      onSurface = fg,
      surfaceVariant = lerp(bg, fg, 0.08f),
      onSurfaceVariant = lerp(fg, bg, 0.35f),
      surfaceContainerLowest = bg,
      surfaceContainerLow = lerp(bg, fg, 0.03f),
      surfaceContainer = lerp(bg, fg, 0.05f),
      surfaceContainerHigh = lerp(bg, fg, 0.09f),
      surfaceContainerHighest = lerp(bg, fg, 0.13f),
      outline = lerp(bg, fg, 0.30f),
      outlineVariant = lerp(bg, fg, 0.15f),
  )
}

/** Material theme that follows the Swing look & feel. Bump [themeKey] after a L&F change. */
@Composable
internal fun LogisimTheme(themeKey: Int, content: @Composable () -> Unit) {
  val scheme = remember(themeKey) { currentColorScheme() }
  MaterialTheme(colorScheme = scheme, content = content)
}
