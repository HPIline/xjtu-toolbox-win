package com.xjtu.toolbox.platform

/** 桌面（JVM）：鼠标拖不出 overscroll，下拉刷新物理上不可达 ⇒ 必须补可见按钮。 */
actual val pullsByGesture: Boolean = false
