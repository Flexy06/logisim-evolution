/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cburch.logisim.gui.generic.AttrTable
import com.cburch.logisim.gui.generic.AttrTableModel
import com.cburch.logisim.gui.generic.AttrTableModelEvent
import com.cburch.logisim.gui.generic.AttrTableModelListener
import com.cburch.logisim.gui.generic.AttrTableModelRow
import com.cburch.logisim.gui.generic.AttrTableSetException
import com.cburch.logisim.util.JInputComponent
import com.cburch.logisim.util.JInputDialog
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities

// ---------------------------------------------------------------------------------------------
// UI model
// ---------------------------------------------------------------------------------------------

/** How a single attribute is edited. Derived from the Swing editor the attribute provides. */
internal sealed interface EditorKind {
  data object ReadOnly : EditorKind

  data object Text : EditorKind

  /** Opens a dialog (color chooser, font chooser, HDL editor, ...). */
  data object Dialog : EditorKind

  data object Unsupported : EditorKind

  data class Toggle(val checked: Boolean) : EditorKind

  data class Choice(val items: List<Any?>, val labels: List<String>, val selected: Int) :
      EditorKind
}

internal data class RowUi(
    val index: Int,
    val row: AttrTableModelRow,
    val label: String,
    val value: String,
    val kind: EditorKind,
)

internal class AttrPanelState {
  var title by mutableStateOf<String?>(null)
  var rows by mutableStateOf<List<RowUi>>(emptyList())

  /** Incremented whenever the model or its structure changes; part of the list item keys. */
  var generation by mutableIntStateOf(0)
  val errors = mutableStateMapOf<Int, String>()
}

internal interface RowActions {
  fun commit(row: RowUi, value: Any?)

  fun openDialog(row: RowUi)
}

// ---------------------------------------------------------------------------------------------
// Swing host
// ---------------------------------------------------------------------------------------------

/**
 * Compose replacement for [AttrTable]. Lives inside the Swing frame (via [ComposePanel]) and
 * mirrors the model of the (hidden) legacy table.
 */
internal class ModernAttrPanel(private val parent: Window, legacy: AttrTable) :
    JPanel(BorderLayout()), AttrTableModelListener {

  private val state = AttrPanelState()
  private var model: AttrTableModel = legacy.attrTableModel

  /** Cached editor kinds per row (reset on structure change). */
  private var kinds: MutableList<EditorKind?> = mutableListOf()

  /** Nullable because updateUI() is already called from the JPanel constructor. */
  private var themeKey: MutableState<Int>? = null

  private val actions =
      object : RowActions {
        override fun commit(row: RowUi, value: Any?) {
          state.errors.remove(row.index)
          if (value == null) return
          try {
            row.row.setValue(parent, value)
          } catch (e: AttrTableSetException) {
            state.errors[row.index] = e.message ?: "Invalid value"
          }
        }

        override fun openDialog(row: RowUi) {
          state.errors.remove(row.index)
          val editor = runCatching { row.row.getEditor(parent) }.getOrNull() ?: return
          val result: Any? =
              when (editor) {
                is JInputDialog -> {
                  val dialog = editor as JInputDialog
                  dialog.setVisible(true) // modal, blocks until closed
                  dialog.value
                }
                is JInputComponent -> {
                  val choice =
                      JOptionPane.showConfirmDialog(
                          parent,
                          editor,
                          row.label,
                          JOptionPane.OK_CANCEL_OPTION,
                          JOptionPane.PLAIN_MESSAGE,
                      )
                  if (choice != JOptionPane.OK_OPTION) return
                  (editor as JInputComponent).value
                }
                else -> return
              }
          commit(row, result)
        }
      }

  init {
    val key = mutableStateOf(0)
    themeKey = key
    val compose = ComposePanel()
    compose.setContent { LogisimTheme(key.value) { AttrPanelContent(state, actions) } }
    add(compose, BorderLayout.CENTER)
    preferredSize = Dimension(260, 320)

    model.addAttrTableModelListener(this)
    reload(structureChanged = true)
    legacy.addModelChangeListener { newModel -> switchModel(newModel) }
  }

  override fun updateUI() {
    super.updateUI()
    // Look & feel switched (e.g. light -> dark in the preferences): rebuild the color scheme.
    themeKey?.let { it.value++ }
  }

  private fun switchModel(newModel: AttrTableModel) {
    if (newModel !== model) {
      model.removeAttrTableModelListener(this)
      model = newModel
      newModel.addAttrTableModelListener(this)
    }
    reload(structureChanged = true)
  }

  private fun reload(structureChanged: Boolean) {
    val m = model
    val count = m.rowCount
    if (structureChanged || kinds.size != count) {
      kinds = MutableList(count) { null }
      state.generation++
      state.errors.clear()
    }
    state.title = m.title
    state.rows =
        (0 until count).map { i ->
          val row = m.getRow(i)
          val cached = kinds[i]
          val kind =
              when {
                !row.isValueEditable -> EditorKind.ReadOnly
                // combo boxes are cheap and carry the current selection -> always refresh
                cached == null || cached is EditorKind.Choice || cached is EditorKind.Toggle ->
                    classify(row).also { kinds[i] = it }
                else -> cached
              }
          RowUi(i, row, row.label ?: "", row.value ?: "", kind)
        }
  }

  private fun classify(row: AttrTableModelRow): EditorKind {
    val editor = runCatching { row.getEditor(parent) }.getOrNull()
    return when (editor) {
      is JComboBox<*> -> {
        val items = (0 until editor.itemCount).map { editor.getItemAt(it) }
        if (items.size == 2 && items.all { it is Boolean }) {
          EditorKind.Toggle(checked = editor.selectedItem == true)
        } else {
          val labels = items.mapIndexed { i, item -> renderLabel(editor, item, i) }
          EditorKind.Choice(items, labels, editor.selectedIndex)
        }
      }
      is JTextField -> EditorKind.Text
      is JInputComponent -> {
        (editor as? Window)?.dispose()
        EditorKind.Dialog
      }
      else -> {
        (editor as? Window)?.dispose()
        EditorKind.Unsupported
      }
    }
  }

  /** Uses the combo box's own renderer so we get the same (localized) text as the old table. */
  private fun renderLabel(box: JComboBox<*>, item: Any?, index: Int): String {
    @Suppress("UNCHECKED_CAST") val renderer = box.renderer as? ListCellRenderer<Any?>
    val comp: Component? =
        runCatching {
              renderer?.getListCellRendererComponent(JList<Any?>(), item, index, false, false)
            }
            .getOrNull()
    return (comp as? JLabel)?.text?.takeIf { it.isNotBlank() } ?: item?.toString() ?: ""
  }

  // AttrTableModelListener --------------------------------------------------------------------

  override fun attrStructureChanged(event: AttrTableModelEvent) =
      onEdt(event) { reload(structureChanged = true) }

  override fun attrTitleChanged(event: AttrTableModelEvent) = onEdt(event) { state.title = model.title }

  override fun attrValueChanged(event: AttrTableModelEvent) =
      onEdt(event) { reload(structureChanged = false) }

  private fun onEdt(event: AttrTableModelEvent, block: () -> Unit) {
    if (event.source !== model) return
    if (SwingUtilities.isEventDispatchThread()) {
      block()
    } else {
      SwingUtilities.invokeLater { if (event.source === model) block() }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Compose UI
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AttrPanelContent(state: AttrPanelState, actions: RowActions) {
  var filter by remember { mutableStateOf("") }
  val colors = MaterialTheme.colorScheme

  Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      state.title?.let {
        Text(
            it,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
        )
      }
      if (state.rows.size > 6) {
        SearchField(filter) { filter = it }
      }

      val visible =
          if (filter.isBlank()) {
            state.rows
          } else {
            state.rows.filter {
              it.label.contains(filter, ignoreCase = true) ||
                  it.value.contains(filter, ignoreCase = true)
            }
          }

      if (visible.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
              if (state.rows.isEmpty()) "No attributes" else "No match",
              style = MaterialTheme.typography.bodySmall,
              color = colors.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(visible, key = { "${state.generation}-${it.index}" }) { row ->
            AttributeRow(row, state.errors[row.index], actions)
          }
        }
      }
    }
  }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(10.dp)
  Box(
      Modifier.fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 4.dp)
          .clip(shape)
          .background(colors.surfaceContainerHigh)
          .padding(horizontal = 10.dp, vertical = 7.dp)
  ) {
    if (value.isEmpty()) {
      Text(
          "Filter attributes…",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.onSurfaceVariant,
      )
    }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun AttributeRow(row: RowUi, error: String?, actions: RowActions) {
  val colors = MaterialTheme.colorScheme
  var editing by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          row.label,
          style = MaterialTheme.typography.labelMedium,
          color = colors.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      Box(Modifier.weight(1.3f), contentAlignment = Alignment.CenterEnd) {
        when (val kind = row.kind) {
          is EditorKind.Toggle ->
              Switch(
                  checked = kind.checked,
                  onCheckedChange = { checked -> actions.commit(row, checked) },
                  modifier = Modifier.scale(0.7f),
              )

          is EditorKind.Choice -> {
            ValuePill(row.value, trailing = "▾") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              kind.labels.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = {
                      Text(
                          label,
                          fontWeight =
                              if (i == kind.selected) FontWeight.Bold else FontWeight.Normal,
                      )
                    },
                    onClick = {
                      menuOpen = false
                      actions.commit(row, kind.items[i])
                    },
                )
              }
            }
          }

          EditorKind.Text ->
              if (editing) {
                InlineTextEditor(
                    initial = row.value,
                    onCommit = {
                      editing = false
                      actions.commit(row, it)
                    },
                    onCancel = { editing = false },
                )
              } else {
                ValuePill(row.value, trailing = null) { editing = true }
              }

          EditorKind.Dialog -> ValuePill(row.value, trailing = "…") { actions.openDialog(row) }

          EditorKind.ReadOnly,
          EditorKind.Unsupported ->
              Text(
                  row.value,
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
        }
      }
    }
    if (error != null) {
      Text(
          error,
          style = MaterialTheme.typography.labelSmall,
          color = colors.error,
          modifier = Modifier.padding(start = 12.dp, top = 2.dp),
      )
    }
  }
}

@Composable
private fun ValuePill(text: String, trailing: String?, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
      Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(colors.surfaceContainerHigh)
          .clickable(onClick = onClick)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text.ifEmpty { "—" },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (trailing != null) {
      Text(trailing, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
  }
}

@Composable
private fun InlineTextEditor(initial: String, onCommit: (String) -> Unit, onCancel: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(8.dp)
  var text by remember { mutableStateOf(initial) }
  var hadFocus by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf(false) }
  val focus = remember { FocusRequester() }

  fun finish(commit: Boolean) {
    if (done) return
    done = true
    if (commit && text != initial) onCommit(text) else onCancel()
  }

  BasicTextField(
      value = text,
      onValueChange = { text = it },
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
      cursorBrush = SolidColor(colors.primary),
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(colors.surface)
              .border(1.5.dp, colors.primary, shape)
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .focusRequester(focus)
              .onFocusChanged {
                if (it.isFocused) {
                  hadFocus = true
                } else if (hadFocus) {
                  finish(commit = true)
                }
              }
              .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                  Key.Enter,
                  Key.NumPadEnter -> {
                    finish(commit = true)
                    true
                  }
                  Key.Escape -> {
                    finish(commit = false)
                    true
                  }
                  else -> false
                }
              },
  )
  LaunchedEffect(Unit) { focus.requestFocus() }
}
