package com.xjtu.toolbox.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.platform.pullsByGesture
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.PullToRefreshState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * miuix 下拉刷新，带中文提示。[scrollBehavior] 传了就让顶栏先展开大标题、展开完才算下拉；
 * [topPadding] 是指示器让出的顶栏高度。
 *
 * **桌面 / Web 上多一个看得见的刷新按钮。** 这两端没有触屏，miuix 的 `PullToRefresh` 挂的是
 * nestedScroll —— 鼠标拖不出 overscroll，滚轮也不行，所以「下拉刷新」在那里物理上不可达。
 * 共享屏有 21 处在用它当唯一刷新入口，不补这一颗按钮等于桌面上没法刷新（只能退出重进）。
 *
 * 按钮画在右上角（内容之上，不占排版），点下去转一圈、刷新中转半圈慢转 ——
 * 让「点了有没有反应」看得见，桌面上没有下拉指示器可看。
 */
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    topPadding: Dp = 0.dp,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable () -> Unit,
) {
    if (pullsByGesture) {
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            pullToRefreshState = state,
            contentPadding = PaddingValues(top = topPadding),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = RefreshTexts,
            content = content,
        )
        return
    }

    Box(modifier.fillMaxSize()) {
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            pullToRefreshState = state,
            contentPadding = PaddingValues(top = topPadding),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = RefreshTexts,
            content = content,
        )
        // 放右下角，不放右上角：右上角在各屏都正好压着「标题行下面第一张卡片」的角，
        // 看起来像图层撞了（票券页尤其明显）。右下角是列表尾部，浮一枚圆钮是通行做法，
        // 也不会和每屏各不相同的顶栏高度打架。
        RefreshButton(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 28.dp),
        )
    }
}

/**
 * 那颗看得见的刷新按钮。
 *
 * 转圈用 [animateFloatAsState] 驱动：点一次转满一圈，刷新中由调用方再叠半圈 ——
 * 桌面上没有下拉指示器，点击到底有没有生效只看这一下。
 *
 * 底色与形状自己画，不走 `IconButton` 的 `colors` / `cornerRadius` 重载：miuix 0.9.4 那个
 * 重载收的是裸 Color 加一组尺寸浮点，参数名各版本不稳，自绘反而更可控。
 */
@Composable
private fun RefreshButton(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var turns by remember { mutableStateOf(0) }
    val spin by animateFloatAsState(
        targetValue = turns * 360f,
        animationSpec = tween(durationMillis = if (turns == 0) 0 else 550),
        label = "refreshSpin",
    )
    // 转完那一圈把它藏回起点，否则点几次之后角度一直累积。
    if (turns > 0 && !isRefreshing && spin >= turns * 360f) turns = 0

    // 浮在内容之上，所以要画成一颗「明确浮着」的圆钮：实心底 + 细描边 + 一点投影。
    // 半透明底在列表页会和底下卡片的圆角糊在一起，看起来像两个图层撞了。
    Box(
        modifier
            .size(34.dp)
            .shadow(elevation = 4.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surface)
            .border(1.dp, MiuixTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = !isRefreshing) {
                turns += 1
                onRefresh()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = if (isRefreshing) "正在刷新" else "刷新",
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).rotate(spin),
        )
    }
}

// miuix 默认是英文；顺序固定：下拉中、可松手、刷新中、刷新完成
private val RefreshTexts = listOf("下拉刷新", "松手刷新", "正在刷新…", "都是最新的了")
