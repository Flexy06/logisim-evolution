/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.modern

import com.cburch.logisim.gui.generic.AttrTable
import java.awt.Window
import javax.swing.JComponent

/**
 * Entry point for the Compose based "modern" UI layer of this fork.
 *
 * Java code only talks to this object, so the rest of the code base does not need to know
 * anything about Kotlin or Compose. Disable the modern UI with `-Dlogisim.modernUi=false`.
 */
object ModernUi {
  private const val PROPERTY = "logisim.modernUi"

  init {
    // Popups (dropdowns, tooltips) of small embedded ComposePanels would otherwise be clipped
    // to the panel bounds -> render them as separate undecorated windows.
    if (System.getProperty("compose.layers.type") == null) {
      System.setProperty("compose.layers.type", "WINDOW")
    }
  }

  @JvmStatic
  fun isEnabled(): Boolean = System.getProperty(PROPERTY)?.toBooleanStrictOrNull() ?: true

  /**
   * Creates the Compose attribute panel. It mirrors [legacy]: whatever model gets set on the
   * classic table (by the frame, the appearance editor, ...) is shown here as well, so all
   * existing code paths keep working unchanged.
   */
  @JvmStatic
  fun createAttributePanel(parent: Window, legacy: AttrTable): JComponent =
      ModernAttrPanel(parent, legacy)
}
