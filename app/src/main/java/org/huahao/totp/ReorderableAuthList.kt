package org.huahao.totp

import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
fun ReorderableAuthList(authEntries: List<AuthEntry>, authStore: AuthStore) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var order by remember { mutableStateOf<List<String>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val byId = authEntries.associateBy { it.id }
    val displayed = order?.let { ids ->
        val known = ids.toSet()
        ids.mapNotNull { byId[it] } + authEntries.filter { it.id !in known }
    } ?: authEntries
    val latestEntries by rememberUpdatedState(displayed)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val maxScroll = with(LocalDensity.current) { 12.dp.toPx() }

    LaunchedEffect(authEntries, draggingId, saving) {
        if (draggingId == null && !saving && order == authEntries.map { it.id }) order = null
        if (draggingId != null && authEntries.none { it.id == draggingId }) {
            draggingId = null
            order = null
        }
    }

    fun updateOrder() {
        val id = draggingId ?: return
        val center = dragTop + dragHeight / 2f
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == id } ?: return
        val target = visible.firstOrNull {
            it.key != id && center >= it.offset && center <= it.offset + it.size &&
                ((center > current.offset + current.size / 2f && center > it.offset + it.size / 2f) ||
                    (center < current.offset + current.size / 2f && center < it.offset + it.size / 2f))
        } ?: return
        val ids = (order ?: latestEntries.map { it.id }).toMutableList()
        val from = ids.indexOf(id)
        val to = ids.indexOf(target.key as? String ?: return)
        // Wait for layout to reflect the previous move before considering another one.
        if (from >= 0 && to >= 0 && current.index == from && target.index == to) {
            ids.add(to, ids.removeAt(from))
            order = ids
        }
    }

    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val speed = when {
                dragTop < layout.viewportStartOffset + edge ->
                    -maxScroll * ((layout.viewportStartOffset + edge - dragTop) / edge).coerceIn(0f, 1f)
                dragTop + dragHeight > layout.viewportEndOffset - edge ->
                    maxScroll * ((dragTop + dragHeight - layout.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) listState.scrollBy(speed)
            updateOrder()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
            .pointerInput(listState) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        if (!saving) {
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                position.y >= it.offset && position.y <= it.offset + it.size
                            }
                            if (item != null) {
                                draggingId = item.key as String
                                dragTop = item.offset.toFloat()
                                dragHeight = item.size
                                order = latestEntries.map { it.id }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onDrag = { change, amount ->
                        if (draggingId != null) {
                            change.consume()
                            val layout = listState.layoutInfo
                            dragTop = (dragTop + amount.y).coerceIn(
                                layout.viewportStartOffset.toFloat(),
                                (layout.viewportEndOffset - dragHeight)
                                    .coerceAtLeast(layout.viewportStartOffset).toFloat()
                            )
                            updateOrder()
                        }
                    },
                    onDragCancel = {
                        draggingId = null
                        order = null
                    },
                    onDragEnd = {
                        if (draggingId != null) {
                            val ids = order ?: latestEntries.map { it.id }
                            draggingId = null
                            saving = true
                            scope.launch {
                                try {
                                    authStore.reorderAuthEntries(ids)
                                } catch (_: IOException) {
                                    order = null
                                    Toast.makeText(context, "排序保存失败，请重试", Toast.LENGTH_SHORT).show()
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        items(displayed, key = { it.id }) { entry ->
            val dragging = draggingId == entry.id
            Box(
                Modifier.zIndex(if (dragging) 1f else 0f).graphicsLayer {
                    translationY = if (dragging) {
                        val offset = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == entry.id }?.offset ?: 0
                        dragTop - offset
                    } else 0f
                    scaleX = if (dragging) 1.02f else 1f
                    scaleY = if (dragging) 1.02f else 1f
                    shadowElevation = if (dragging) 8.dp.toPx() else 0f
                }
            ) {
                AuthCodeCard(entry = entry, authStore = authStore)
            }
        }
    }
}
