package app.preciseplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.preciseplayer.Ctl
import app.preciseplayer.Prefs
import app.preciseplayer.Slot
import app.preciseplayer.ui.player.ctlIcon

/** Add, remove, reorder and move the player's buttons between the top bar, centre and bottom bar. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ControlsEditor(onBack: () -> Unit) {
    var adding by remember { mutableStateOf<Slot?>(null) }
    var moving by remember { mutableStateOf<Pair<Slot, Ctl>?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Customize controls") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                TextButton(onClick = {
                    Slot.values().forEach { Prefs.buttons(it).value = Prefs.buttons(it).default }
                }) { Text("Reset") }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item { Preview() }
            for (slot in Slot.values()) {
                item {
                    Text(slot.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp))
                    if (slot == Slot.TOP) Text("The ⋮ menu is always at the end of the top bar, so every action stays reachable.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp))
                }
                val pref = Prefs.buttons(slot)
                val list = pref.value
                if (list.isEmpty()) item {
                    Text("Empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                list.forEachIndexed { i, c ->
                    item(key = "${slot.name}-${c.name}") {
                        ListItem(
                            headlineContent = { Text(c.label) },
                            leadingContent = { Icon(ctlIcon(c), null) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { pref.value = list.swap(i, i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                                    IconButton(onClick = { pref.value = list.swap(i, i + 1) }, enabled = i < list.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                                    IconButton(onClick = { moving = slot to c }) { Icon(Icons.Rounded.SwapHoriz, "Move to another bar") }
                                    IconButton(onClick = { pref.value = list - c }) { Icon(Icons.Rounded.Close, "Remove") }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                item {
                    OutlinedButton(onClick = { adding = slot }, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add button")
                    }
                }
            }
        }
    }

    adding?.let { slot ->
        val pref = Prefs.buttons(slot)
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Add to ${slot.label.lowercase()}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Ctl.values().filter { it !in pref.value }.forEach { c ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pref.value = pref.value + c; adding = null }
                            .padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(ctlIcon(c), null)
                            Spacer(Modifier.width(16.dp))
                            Text(c.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = null }) { Text("Close") } },
        )
    }

    moving?.let { (from, c) ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move “${c.label}” to") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slot.values().filter { it != from }.forEach { to ->
                        FilterChip(selected = false, onClick = {
                            Prefs.buttons(from).value = Prefs.buttons(from).value - c
                            if (c !in Prefs.buttons(to).value) Prefs.buttons(to).value = Prefs.buttons(to).value + c
                            moving = null
                        }, label = { Text(to.label) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
        )
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> {
    if (a !in indices || b !in indices) return this
    val m = toMutableList(); val t = m[a]; m[a] = m[b]; m[b] = t; return m
}

/** A small mock of the player showing where each button ends up. */
@Composable
private fun Preview() {
    Box(
        Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().height(190.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFF050506)),
    ) {
        val tint = Color.White
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.weight(1f))
            Prefs.topButtons.value.forEach { Icon(ctlIcon(it), null, tint = tint, modifier = Modifier.padding(horizontal = 4.dp).size(18.dp)) }
            Icon(Icons.Rounded.MoreVert, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Prefs.centerButtons.value.forEach { Icon(ctlIcon(it), null, tint = tint, modifier = Modifier.size(if (it == Ctl.PLAY_PAUSE) 30.dp else 22.dp)) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(0.4f).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Prefs.showFrameRow.value) {
                    Icon(ctlIcon(Ctl.FRAME_BACK), null, tint = tint, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.10f)))
                    Icon(ctlIcon(Ctl.FRAME_FWD), null, tint = tint, modifier = Modifier.size(18.dp))
                } else Spacer(Modifier.weight(1f))
                Prefs.bottomButtons.value.forEach { Icon(ctlIcon(it), null, tint = tint, modifier = Modifier.padding(start = 8.dp).size(18.dp)) }
            }
        }
    }
}
