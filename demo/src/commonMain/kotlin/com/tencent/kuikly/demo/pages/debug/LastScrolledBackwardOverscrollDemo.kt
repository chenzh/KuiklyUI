/*
 * Repro demo: bottom overscroll bounce-back sets lastScrolledBackward=true on some devices.
 */
package com.tencent.kuikly.demo.pages.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.extension.bouncesEnable
import com.tencent.kuikly.compose.extension.contentOffset
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import kotlinx.coroutines.launch

@Page("LastScrollBackwardRepro")
internal class LastScrolledBackwardOverscrollDemo : ComposeContainer() {

    override fun willInit() {
        super.willInit()
        setContent {
            ReproContent()
        }
    }
}

@Composable
private fun ReproContent() {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemCount = 40

    var bouncesEnabled by remember { mutableStateOf(true) }
    var bugReproduced by remember { mutableStateOf(false) }
    var bottomBaselineOffset by remember { mutableIntStateOf(0) }
    var peakOffset by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }

    val eventLog = remember { mutableStateListOf<String>() }

    fun appendLog(message: String) {
        val line = message
        if (eventLog.isEmpty() || eventLog.first() != line) {
            eventLog.add(0, line)
        }
        if (eventLog.size > 30) {
            eventLog.removeAt(eventLog.lastIndex)
        }
    }

    LaunchedEffect(listState, bouncesEnabled) {
        bugReproduced = false
        bottomBaselineOffset = 0
        peakOffset = 0
        prevOffset = listState.contentOffset
        eventLog.clear()
        appendLog("开始监听。步骤：滚到底部 → 继续上拉 overscroll → 松手回弹")

        snapshotFlow {
            ScrollSnapshot(
                contentOffset = listState.contentOffset,
                lastScrolledForward = listState.lastScrolledForward,
                lastScrolledBackward = listState.lastScrolledBackward,
                canScrollForward = listState.canScrollForward,
                canScrollBackward = listState.canScrollBackward,
                isScrollInProgress = listState.isScrollInProgress,
                firstVisibleIndex = listState.firstVisibleItemIndex,
            )
        }.collect { snapshot ->
            val offset = snapshot.contentOffset
            val delta = offset - prevOffset

            if (!snapshot.canScrollForward) {
                if (bottomBaselineOffset == 0 || offset < bottomBaselineOffset) {
                    bottomBaselineOffset = offset
                }
                if (offset > peakOffset) {
                    peakOffset = offset
                }
            } else {
                bottomBaselineOffset = 0
                peakOffset = 0
            }

            if (delta != 0) {
                appendLog(
                    "offset $prevOffset→$offset (Δ=$delta) " +
                        "F=${snapshot.lastScrolledForward} B=${snapshot.lastScrolledBackward}"
                )
            }

            val overscrollHappened = peakOffset > bottomBaselineOffset
            val settledAtBottom = !snapshot.canScrollForward && !snapshot.isScrollInProgress
            if (settledAtBottom && overscrollHappened && snapshot.lastScrolledBackward) {
                if (!bugReproduced) {
                    bugReproduced = true
                    appendLog(
                        "【复现成功】底部回弹后 lastScrolledBackward=true " +
                            "(peak=$peakOffset, base=$bottomBaselineOffset)"
                    )
                }
            }

            prevOffset = offset
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatusBanner(bugReproduced = bugReproduced, bouncesEnabled = bouncesEnabled)

        Text(
            text = "操作：先点「滚到底部」，再用力上拉越界，松手等回弹结束",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = Color.DarkGray,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionChip(
                label = "滚到底部",
                color = Color(0xFF1976D2),
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(itemCount - 1)
                    }
                },
            )
            ActionChip(
                label = if (bouncesEnabled) "Bounce: ON" else "Bounce: OFF",
                color = if (bouncesEnabled) Color(0xFF388E3C) else Color(0xFF757575),
                onClick = { bouncesEnabled = !bouncesEnabled },
            )
            ActionChip(
                label = "清空日志",
                color = Color(0xFF546E7A),
                onClick = {
                    eventLog.clear()
                    bugReproduced = false
                    peakOffset = 0
                    bottomBaselineOffset = 0
                },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .bouncesEnable(bouncesEnabled),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((0 until itemCount).toList()) { index ->
                val bg = if (index == itemCount - 1) Color(0xFFFF7043) else Color(0xFF42A5F5)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(bg)
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = if (index == itemCount - 1) {
                            "最后一项 #$index（在此上拉 overscroll）"
                        } else {
                            "Item #$index"
                        },
                        color = Color.White,
                        fontWeight = if (index == itemCount - 1) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        StatePanel(
            listState = listState,
            peakOffset = peakOffset,
            bottomBaselineOffset = bottomBaselineOffset,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            item {
                Text("事件日志（最新在上）", fontSize = 11.sp, color = Color.Gray)
            }
            items(eventLog) { line ->
                Text(text = line, fontSize = 10.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusBanner(bugReproduced: Boolean, bouncesEnabled: Boolean) {
    val bg = when {
        !bouncesEnabled -> Color(0xFFEEEEEE)
        bugReproduced -> Color(0xFFFFCDD2)
        else -> Color(0xFFC8E6C9)
    }
    val text = when {
        !bouncesEnabled -> "Bounce 已关闭（对照组：不应出现底部 overscroll）"
        bugReproduced -> "已复现：底部回弹后 lastScrolledBackward = true"
        else -> "等待复现：底部 overscroll 回弹后观察 lastScrolledBackward"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatePanel(
    listState: LazyListState,
    peakOffset: Int,
    bottomBaselineOffset: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("实时状态", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("native contentOffset = ${listState.contentOffset} dp", fontSize = 11.sp)
        Text("peak / baseline = $peakOffset / $bottomBaselineOffset dp", fontSize = 11.sp)
        Text("lastScrolledForward = ${listState.lastScrolledForward}", fontSize = 11.sp)
        Text(
            text = "lastScrolledBackward = ${listState.lastScrolledBackward}",
            fontSize = 11.sp,
            color = if (listState.lastScrolledBackward) Color(0xFFC62828) else Color.Unspecified,
            fontWeight = if (listState.lastScrolledBackward) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = "canScrollForward = ${listState.canScrollForward}, " +
                "canScrollBackward = ${listState.canScrollBackward}",
            fontSize = 11.sp,
        )
        Text(
            text = "isScrollInProgress = ${listState.isScrollInProgress}, " +
                "firstVisibleIndex = ${listState.firstVisibleItemIndex}",
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

private data class ScrollSnapshot(
    val contentOffset: Int,
    val lastScrolledForward: Boolean,
    val lastScrolledBackward: Boolean,
    val canScrollForward: Boolean,
    val canScrollBackward: Boolean,
    val isScrollInProgress: Boolean,
    val firstVisibleIndex: Int,
)
