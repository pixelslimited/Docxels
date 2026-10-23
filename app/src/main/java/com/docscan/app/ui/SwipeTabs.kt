package com.docscan.app.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Swipe sideways to move between the tabs of a screen: left goes to the next tab, right to the previous one.
 * Only drags that nothing inside the tab has used count (a list, slider or pinch gesture wins), and a short drag is ignored.
 * Turn it off ([enabled] = false) on a tab whose own content is dragged sideways everywhere.
 */
fun Modifier.swipeTabs(index: Int, count: Int, enabled: Boolean = true, onChange: (Int) -> Unit): Modifier =
    if (!enabled) this else pointerInput(index, count) {
        val distance = 72.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { total = 0f },
            onDragEnd = {
                if (total < -distance && index < count - 1) onChange(index + 1)
                else if (total > distance && index > 0) onChange(index - 1)
                total = 0f
            },
        ) { _, dx -> total += dx }
    }
