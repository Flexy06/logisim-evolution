/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.modern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cburch.logisim.gui.generic.ZoomControl
import com.cburch.logisim.gui.generic.ZoomModel
import java.awt.BorderLayout
import java.awt.Dimension
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal class ZoomState {
  var enabled by mutableStateOf(false)
  var autoEnabled by mutableStateOf(false)
  var showGrid by mutableStateOf(false)
  var label by mutableStateOf("×1")
  var index by mutableIntStateOf(0)
  var count by mutableIntStateOf(1)
}

internal interface ZoomActions {
  fun zoomIn()

  fun zoomOut()

  fun zoomTo(index: Int)

  fun zoomToFactor(factor: Double)

  fun autoZoom()

  fun toggleGrid()
}

/**
 * Compose replacement for the content of [ZoomControl]: fit button, −/+ buttons, slider, zoom
 * menu and grid toggle in one slim row. All logic stays in [ZoomControl].
 */
class ModernZoomBar(private val control: ZoomControl) : JPanel(BorderLayout()), PropertyChangeListener {
  private val state = ZoomState()
  private var model: ZoomModel? = null

  /** Nullable because updateUI() is already called from the JPanel constructor. */
  private var themeKey: MutableState<Int>? = null

  private val actions =
      object : ZoomActions {
        override fun zoomIn() {
          if (model != null) control.zoomIn()
        }

        override fun zoomOut() {
          if (model != null) control.zoomOut()
        }

        override fun zoomTo(index: Int) {
          if (model != null) control.zoomTo(index)
        }

        override fun zoomToFactor(factor: Double) {
          control.zoomToFactor(factor)
        }

        override fun autoZoom() {
          if (model != null) control.autoZoom()
        }

        override fun toggleGrid() {
          val m = model ?: return
          m.showGrid = !m.showGrid
        }
      }

  init {
    isOpaque = false
    val key = mutableStateOf(0)
    themeKey = key
    val compose = ComposePanel()
    compose.setContent { LogisimTheme(key.value) { ZoomBarContent(state, actions) } }
    add(compose, BorderLayout.CENTER)
    preferredSize = Dimension(240, 44)
    minimumSize = Dimension(180, 44)
    bind(control.zoomModel)
  }

  override fun updateUI() {
    super.updateUI()
    themeKey?.let { it.value++ }
  }

  /** Called by [ZoomControl] whenever its zoom model changes. */
  fun bind(newModel: ZoomModel?) {
    model?.let {
      it.removePropertyChangeListener(ZoomModel.ZOOM, this)
      it.removePropertyChangeListener(ZoomModel.SHOW_GRID, this)
    }
    model = newModel
    newModel?.let {
      it.addPropertyChangeListener(ZoomModel.ZOOM, this)
      it.addPropertyChangeListener(ZoomModel.SHOW_GRID, this)
    }
    refresh()
  }

  /** Re-reads everything from the model / control. */
  fun refresh() {
    val m = model
    state.enabled = m != null
    state.autoEnabled = m != null && control.isAutoZoomEnabled
    if (m == null) return
    val options = m.zoomOptions
    val percent = m.zoomFactor * 100.0
    var nearest = 0
    for (i in options.indices) {
      if (abs(options[i] - percent) < abs(options[nearest] - percent)) nearest = i
    }
    state.count = max(1, options.size)
    state.index = nearest
    state.label = control.zoomString()
    state.showGrid = m.showGrid
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    if (SwingUtilities.isEventDispatchThread()) refresh() else SwingUtilities.invokeLater { refresh() }
  }
}

@Composable
private fun ZoomBarContent(state: ZoomState, actions: ZoomActions) {
  val colors = MaterialTheme.colorScheme
  var menuOpen by remember { mutableStateOf(false) }

  Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      GlyphButton(enabled = state.autoEnabled, onClick = actions::autoZoom) { drawFit(it) }
      GlyphButton(enabled = state.enabled, onClick = actions::zoomOut) { drawMinus(it) }

      val maxIndex = max(1, state.count - 1)
      Slider(
          value = state.index.toFloat(),
          onValueChange = { v ->
            val i = v.roundToInt()
            if (i != state.index) actions.zoomTo(i)
          },
          valueRange = 0f..maxIndex.toFloat(),
          enabled = state.enabled,
          modifier = Modifier.weight(1f),
      )

      GlyphButton(enabled = state.enabled, onClick = actions::zoomIn) { drawPlus(it) }

      Box {
        Text(
            state.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier.widthIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainerHigh)
                    .clickable(enabled = state.enabled) { menuOpen = true }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          listOf("×½" to 0.5, "×1" to 1.0, "×2" to 2.0).forEach { (label, factor) ->
            DropdownMenuItem(
                text = { Text(label) },
                enabled = factor != 0.5 || state.autoEnabled,
                onClick = {
                  menuOpen = false
                  actions.zoomToFactor(factor)
                },
            )
          }
          DropdownMenuItem(
              text = { Text("Fit") },
              enabled = state.autoEnabled,
              onClick = {
                menuOpen = false
                actions.autoZoom()
              },
          )
        }
      }

      GlyphButton(enabled = state.enabled, selected = state.showGrid, onClick = actions::toggleGrid) {
        drawGrid(it)
      }
    }
  }
}

@Composable
private fun GlyphButton(
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  val background =
      when {
        selected -> colors.primary.copy(alpha = 0.16f)
        hovered && enabled -> colors.surfaceContainerHigh
        else -> Color.Transparent
      }
  val tint = if (selected) colors.primary else colors.onSurface
  Box(
      modifier =
          Modifier.size(30.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(background)
              .hoverable(interaction)
              .clickable(enabled = enabled, onClick = onClick)
              .alpha(if (enabled) 1f else 0.38f),
      contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(16.dp)) { glyph(tint) }
  }
}

// ---- tiny vector glyphs (no icon library needed) --------------------------------------------

private fun DrawScope.stroke(): Float = size.minDimension * 0.12f

private fun DrawScope.drawMinus(c: Color) {
  val y = size.height / 2
  drawLine(c, Offset(size.width * 0.15f, y), Offset(size.width * 0.85f, y), stroke(), StrokeCap.Round)
}

private fun DrawScope.drawPlus(c: Color) {
  drawMinus(c)
  val x = size.width / 2
  drawLine(c, Offset(x, size.height * 0.15f), Offset(x, size.height * 0.85f), stroke(), StrokeCap.Round)
}

private fun DrawScope.drawFit(c: Color) {
  val w = size.width
  val h = size.height
  val l = w * 0.3f
  val s = stroke()
  // four corner brackets
  drawLine(c, Offset(0f, 0f), Offset(l, 0f), s, StrokeCap.Round)
  drawLine(c, Offset(0f, 0f), Offset(0f, l), s, StrokeCap.Round)
  drawLine(c, Offset(w, 0f), Offset(w - l, 0f), s, StrokeCap.Round)
  drawLine(c, Offset(w, 0f), Offset(w, l), s, StrokeCap.Round)
  drawLine(c, Offset(0f, h), Offset(l, h), s, StrokeCap.Round)
  drawLine(c, Offset(0f, h), Offset(0f, h - l), s, StrokeCap.Round)
  drawLine(c, Offset(w, h), Offset(w - l, h), s, StrokeCap.Round)
  drawLine(c, Offset(w, h), Offset(w, h - l), s, StrokeCap.Round)
}

private fun DrawScope.drawGrid(c: Color) {
  val r = size.minDimension * 0.08f
  for (i in 0..2) {
    for (j in 0..2) {
      drawCircle(c, r, Offset(size.width * (0.15f + 0.35f * i), size.height * (0.15f + 0.35f * j)))
    }
  }
}
