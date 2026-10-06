/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.modern

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cburch.draw.toolbar.Toolbar
import com.cburch.draw.toolbar.ToolbarClickableItem
import com.cburch.draw.toolbar.ToolbarItem
import com.cburch.draw.toolbar.ToolbarSeparator
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.max

internal data class ToolbarEntry(
    val item: ToolbarItem,
    val selected: Boolean,
    val width: Int,
    val height: Int,
    val separator: Boolean,
    val interactive: Boolean,
    val tooltip: String?,
)

internal class ToolbarState {
  var entries by mutableStateOf<List<ToolbarEntry>>(emptyList())
  var vertical by mutableStateOf(false)

  /** Bumped on every refresh so icons are re-rendered (tool icons can change). */
  var revision by mutableIntStateOf(0)
}

private const val BUTTON_PADDING = 6 // dp around each icon
private const val BUTTON_GAP = 2 // dp between buttons
private const val BAR_PADDING = 4 // dp around the whole bar

/**
 * Compose replacement for the content of [Toolbar]. The Swing [Toolbar] stays the public
 * component (other code registers key bindings on it, changes its model, ...) and simply hosts
 * this panel instead of its old buttons.
 */
class ModernToolbar(private val toolbar: Toolbar) : JPanel(BorderLayout()) {
  private val state = ToolbarState()

  /** Nullable because updateUI() is already called from the JPanel constructor. */
  private var themeKey: MutableState<Int>? = null

  init {
    isOpaque = false
    val key = mutableStateOf(0)
    themeKey = key
    val compose = ComposePanel()
    compose.setContent {
      LogisimTheme(key.value) { ToolbarContent(state, toolbar, ::onItemClicked) }
    }
    add(compose, BorderLayout.CENTER)
  }

  override fun updateUI() {
    super.updateUI()
    themeKey?.let { it.value++ }
  }

  /** Re-reads items, selection and orientation from the Swing toolbar. */
  fun refresh() {
    val model = toolbar.toolbarModel
    val orientation = toolbar.orientation
    val vertical = orientation == Toolbar.VERTICAL
    val entries =
        if (model == null) {
          emptyList()
        } else {
          model.items.map { item ->
            val dim = item.getDimension(orientation)
            ToolbarEntry(
                item = item,
                selected = model.isSelected(item),
                width = dim.width,
                height = dim.height,
                separator = item is ToolbarSeparator,
                interactive = item.isSelectable || item is ToolbarClickableItem,
                tooltip = runCatching { item.toolTip }.getOrNull(),
            )
          }
        }
    state.vertical = vertical
    state.entries = entries
    state.revision++
    updateSize(entries, vertical)
  }

  private fun updateSize(entries: List<ToolbarEntry>, vertical: Boolean) {
    var along = 2 * BAR_PADDING
    var across = 0
    for (e in entries) {
      val a = if (vertical) e.height else e.width
      val c = if (vertical) e.width else e.height
      along += a + 2 * BUTTON_PADDING + BUTTON_GAP
      across = max(across, c + 2 * BUTTON_PADDING)
    }
    across += 2 * BAR_PADDING
    val size = if (vertical) Dimension(across, along) else Dimension(along, across)
    preferredSize = size
    minimumSize = size
    revalidate()
  }

  private fun onItemClicked(entry: ToolbarEntry) {
    val model = toolbar.toolbarModel ?: return
    val item = entry.item
    if (item.isSelectable) {
      model.itemSelected(item)
    } else if (item is ToolbarClickableItem) {
      item.clicked()
    }
    refresh()
  }
}

@Composable
private fun ToolbarContent(
    state: ToolbarState,
    host: Component,
    onClick: (ToolbarEntry) -> Unit,
) {
  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    if (state.vertical) {
      Column(
          modifier = Modifier.padding(BAR_PADDING.dp),
          verticalArrangement = Arrangement.spacedBy(BUTTON_GAP.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        state.entries.forEach { ToolbarButton(it, state.revision, true, host, onClick) }
      }
    } else {
      Row(
          modifier = Modifier.padding(BAR_PADDING.dp),
          horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        state.entries.forEach { ToolbarButton(it, state.revision, false, host, onClick) }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarButton(
    entry: ToolbarEntry,
    revision: Int,
    vertical: Boolean,
    host: Component,
    onClick: (ToolbarEntry) -> Unit,
) {
  val colors = MaterialTheme.colorScheme

  if (entry.separator) {
    val line =
        if (vertical) Modifier.padding(vertical = 4.dp).height(1.dp).width(24.dp)
        else Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp)
    Box(line.background(colors.outlineVariant))
    return
  }

  val shape = RoundedCornerShape(8.dp)
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  val background =
      when {
        entry.selected -> colors.primary.copy(alpha = 0.16f)
        hovered && entry.interactive -> colors.surfaceContainerHigh
        else -> Color.Transparent
      }

  val button: @Composable () -> Unit = {
    Box(
        modifier =
            Modifier.clip(shape)
                .background(background)
                .then(
                    if (entry.selected) Modifier.border(1.dp, colors.primary.copy(alpha = 0.6f), shape)
                    else Modifier
                )
                .hoverable(interaction)
                .clickable(enabled = entry.interactive) { onClick(entry) }
                .padding(BUTTON_PADDING.dp),
        contentAlignment = Alignment.Center,
    ) {
      ToolbarIcon(entry, revision, host)
    }
  }

  val tooltip = entry.tooltip
  if (tooltip.isNullOrBlank()) {
    button()
  } else {
    TooltipArea(
        tooltip = {
          Surface(
              shape = RoundedCornerShape(6.dp),
              color = colors.inverseSurface,
              shadowElevation = 4.dp,
          ) {
            Text(
                tooltip,
                color = colors.inverseOnSurface,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        },
        delayMillis = 600,
    ) {
      button()
    }
  }
}

@Composable
private fun ToolbarIcon(entry: ToolbarEntry, revision: Int, host: Component) {
  val density = LocalDensity.current.density
  val bitmap =
      remember(entry.item, revision, density, entry.width, entry.height) {
        renderIcon(entry, host, density)
      }
  Image(
      bitmap = bitmap,
      contentDescription = entry.tooltip,
      modifier = Modifier.size(entry.width.dp, entry.height.dp),
  )
}

/** Paints the (Swing) toolbar item into an image at the screen's pixel density. */
private fun renderIcon(entry: ToolbarEntry, host: Component, scale: Float): ImageBitmap {
  val w = max(1, ceil(entry.width * scale).toInt())
  val h = max(1, ceil(entry.height * scale).toInt())
  val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.scale(scale.toDouble(), scale.toDouble())
    entry.item.paintIcon(host, g)
  } catch (e: Exception) {
    // a broken icon must never break the toolbar
  } finally {
    g.dispose()
  }
  return image.toComposeImageBitmap()
}
