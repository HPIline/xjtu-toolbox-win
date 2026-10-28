package com.xjtu.toolbox.platform

/**
 * 这一端能不能靠「下拉」刷新。
 *
 * Android / iOS 有触屏，把手势往下拖就是刷新 —— 21 个共享屏的下拉刷新在那边是唯一的刷新入口，
 * 用户早就习惯了。
 *
 * 桌面（JVM）没有这个手势：miuix 的 `PullToRefresh` 挂的是 nestedScroll，鼠标拖不出 overscroll，
 * 滚轮更不行。所以桌面上「下拉刷新」在物理上不可达 —— 光靠共享屏原来那套，桌面端就**没有任何
 * 刷新入口**，用户只能退出重进。
 *
 * 于是这个开关反过来用：`false` 的一端由 [com.xjtu.toolbox.ui.components.AppPullToRefresh]
 * 自己补一个看得见的刷新按钮，`true` 的一端维持原样（多一个按钮反而占地方）。
 */
expect val pullsByGesture: Boolean

/**
 * 空 / 出错状态下那句「怎么重试」的提示。
 *
 * 共享屏里写死了「下拉可刷新重试」—— 在桌面上这是句谎话：鼠标拖不出 overscroll，
 * 滚轮也不行，用户真去拉一下只会发现屏没反应。所以统一走这里：手势端照旧，
 * 非手势端改成指向右上角那颗看得见的刷新按钮。
 */
fun refreshHintText(
    gesture: String = "下拉可刷新重试",
    button: String = "点右下角刷新按钮重试",
): String = if (pullsByGesture) gesture else button
