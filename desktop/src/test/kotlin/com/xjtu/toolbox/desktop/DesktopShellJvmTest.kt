package com.xjtu.toolbox.desktop

import com.xjtu.toolbox.nav.AppRoute
import com.xjtu.toolbox.shell.ShellTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 桌面这一端接共享壳的那条缝（`DesktopShell.kt`）：六格、以及「当前页」两个方向的换算。 */
class DesktopShellJvmTest {

    @Test
    fun `六格的顺序文字与底栏从左到右一致`() {
        assertEquals(
            listOf("首页", "校历", "图书馆", "黄页", "游戏", "全部", "个性化"),
            desktopTabs().tabs.map { it.label },
        )
        assertEquals(
            listOf("main", "school_calendar", "library", "yellow_page", "games", "all", "appearance"),
            desktopTabs().ids,
        )
        assertEquals(7, desktopTabs().size)
    }

    @Test
    fun `六格的 id 与目标都不重复（一格指向一页）`() {
        val tabs = desktopTabs()
        assertTrue(tabs.ids.all { it.isNotBlank() }, "id 不能是空串：${tabs.ids}")
        assertEquals(tabs.size, tabs.ids.toSet().size, "id 重复了：${tabs.ids}")
        val targets = tabs.tabs.map { it.target }
        assertEquals(tabs.size, targets.toSet().size, "两格指向了同一页：$targets")
    }

    @Test
    fun `六格指向的页与搬迁前逐字相同`() {
        assertEquals(
            listOf(
                ShellTarget.App(AppRoute.Main),
                ShellTarget.App(AppRoute.SchoolCalendar),
                ShellTarget.App(AppRoute.Library),
                ShellTarget.App(AppRoute.YellowPage),
                ShellTarget.App(AppRoute.Games),
                ShellTarget.AllPages,
                ShellTarget.Custom(ID_APPEARANCE),
            ),
            desktopTabs().tabs.map { it.target },
        )
    }

    @Test
    fun `AllPages 只在最后一格里，其余五格都是共享路由表里的一屏`() {
        val tabs = desktopTabs()
        assertEquals(listOf("all"), tabs.tabs.filter { it.target == ShellTarget.AllPages }.map { it.id })
        // 「全部」不是一条路由（shell 的 AllPages 那一档）⇒ 它不会出现在任何一个 App 目标里
        assertTrue(
            tabs.tabs
                .filter { it.target != ShellTarget.AllPages && it.target != ShellTarget.Custom(ID_APPEARANCE) }
                .all { it.target is ShellTarget.App },
            "除「全部」之外每一格都该指向共享路由表里的一屏：${tabs.tabs.map { it.target }}",
        )
        assertTrue(tabs.byId("all")?.target == ShellTarget.AllPages)
    }

    @Test
    fun `当前页两个方向往返一致`() {
        listOf(
            DesktopTarget.App(AppRoute.Main),
            DesktopTarget.App(AppRoute.Fitness),
            DesktopTarget.App(AppRoute.Schedule),
            DesktopTarget.Routes,
        ).forEach { target ->
            assertEquals(target, target.toShell().toDesktop(), "$target 换算一圈变了")
        }
        // 方向也一致：壳里那条路由就是这一端那条
        assertEquals(AppRoute.Fitness, (DesktopTarget.App(AppRoute.Fitness).toShell() as ShellTarget.App).route)
        assertEquals(ShellTarget.AllPages, DesktopTarget.Routes.toShell())
    }

    @Test
    fun `壳里有而这一端没有的那一档如实返回 null（Custom = 端自己的页）`() {
        // 这一端现在自己也有 Custom 了（个性化那一页），所以 `toDesktop` 是全函数；
        // `toShell` 方向仍然全函数。两向对 Custom 的处理必须一致 —— 否则点那一格就没反应。
        val custom = ShellTarget.Custom(ID_APPEARANCE)
        assertEquals(DesktopTarget.Custom(ID_APPEARANCE), custom.toDesktop())
        assertEquals(custom, DesktopTarget.Custom(ID_APPEARANCE).toShell())
    }

    @Test
    fun `个性化那一格指向这一端自己的页而不是共享路由`() {
        val tab = desktopTabs().byId("appearance")
        assertTrue(tab != null, "底栏 / 侧栏里没有个性化这一格")
        assertEquals(ShellTarget.Custom(ID_APPEARANCE), tab!!.target)
        assertEquals(DesktopTarget.Custom(ID_APPEARANCE), tab.target.toDesktop())
    }

    @Test
    fun `每一格都能被换算回这一端的当前页（点哪一格都有反应）`() {
        desktopTabs().tabs.forEach { tab ->
            assertTrue(
                tab.target.toDesktop() != null,
                "第 ${tab.id} 格的目标 ${tab.target} 换算不回这一端的目标（点它就不会有反应）",
            )
            // 往返一致：点下去存下的目标，再换算回壳里还是同一格
            assertEquals(
                tab.target,
                tab.target.toDesktop().toShell(),
                "第 ${tab.id} 格往返一趟变了",
            )
        }
    }
}
