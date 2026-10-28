package com.xjtu.toolbox.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrowserUpdated
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ConnectWithoutContact
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Grading
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjtu.toolbox.nav.AppRoute
import com.xjtu.toolbox.ui.components.AppSearchBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「全部」那一页：一排排小图配名字的宫格，而不是原来那串居中的文字按钮。
 *
 * 原来那一版把 40 项竖着堆成一行一条，一屏看得到十来项、还得滚好久，而每一项之间没有任何
 * 视觉区别 —— 找东西只能靠读完整行。桌面窗口宽，横向空间是富余的：宫格在同样的高度里能放
 * 四五倍的入口，图标本身就是最快的识别线索。
 *
 * 分组照 `:app` 服务表的叫法走（教务 / 生活 / 学习 / 更多），搜索框按名字和 id 双通道过滤。
 */
@Composable
internal fun RoutesPage(auth: DesktopAuth, onNavigate: (DesktopTarget) -> Unit) {
    var query by remember { mutableStateOf("") }

    val all = remember {
        DESKTOP_SUPPORTED_ROUTES.map { (route, label) -> DesktopEntry(route, label) }
    }
    val q = query.trim()
    val groups = remember(q) {
        if (q.isEmpty()) DesktopEntry.groups(all)
        else listOf(DesktopGroup("搜索结果", all.filter { it.matches(q) })).filter { it.entries.isNotEmpty() }
    }

    Column(Modifier.fillMaxSize()) {
        // ── 页头 ──────────────────────────────────────────────────
        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "全部功能",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${all.size} 个入口 · 屏与 Android 是同一份源码，取数直接从学校站点走",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                }
                // 个性化就放在这一页右上角：它是「应用长相」的入口，和「有哪些功能」同属一屏。
                DesktopEntryTile(
                    entry = DesktopEntry(AppRoute.Settings, "个性化"),
                    icon = Icons.Filled.Palette,
                    accent = MiuixTheme.colorScheme.primary,
                    compact = true,
                    onClick = { onNavigate(DesktopTarget.Custom("appearance")) },
                )
            }
            Spacer(Modifier.height(14.dp))
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                label = "找功能（名字或 id）",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }

        // ── 宫格 ──────────────────────────────────────────────────
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没找到「$q」", color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "换个说法，或者清空搜索看全部 ${all.size} 项",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(text = "清空", onClick = { query = "" }, minWidth = 88.dp)
                }
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            groups.forEach { group ->
                item(key = "h-${group.title}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, bottom = 6.dp)) {
                        Text(
                            group.title,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${group.entries.size}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                    }
                }
                items(group.entries, key = { "e-${it.route.id}" }) { entry ->
                    DesktopEntryTile(
                        entry = entry,
                        onClick = { onNavigate(DesktopTarget.App(entry.route)) },
                    )
                }
            }
        }
    }
}

/** 宫格里的一格：图标 + 名字，鼠标移上去才出底色（40 个格子全铺底色会糊成一片）。 */
@Composable
private fun DesktopEntryTile(
    entry: DesktopEntry,
    onClick: () -> Unit,
    icon: ImageVector = entry.icon,
    accent: Color = entry.accent,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val cs = MiuixTheme.colorScheme

    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(
                when {
                    hovered -> cs.surfaceContainerHigh
                    // 紧凑版（页头那颗「个性化」）常驻底色，不然它混在标题里看不出来是个按钮。
                    compact -> cs.surfaceVariant
                    else -> Color.Transparent
                },
            )
            .padding(vertical = if (compact) 8.dp else 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(if (compact) 34.dp else 44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            )
        }
        if (!compact) {
            Spacer(Modifier.height(8.dp))
            Text(
                entry.label,
                color = cs.onSurface,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Text(entry.label, color = cs.onSurface, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** 宫格里的一个入口：路由 + 名字 + 图标 + 强调色。 */
internal data class DesktopEntry(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector = iconOf(route.id),
    val accent: Color = accentOf(route.id),
) {
    fun matches(query: String): Boolean =
        label.contains(query, ignoreCase = true) ||
            route.id.contains(query, ignoreCase = true) ||
            aliases.contains(query, ignoreCase = true)

    private val aliases: String
        get() = when (route.id) {
            "school_calendar" -> "日历 放假 教学周"
            "school_course" -> "全校 开课 班级"
            "empty_room" -> "自习 空教室 找教室"
            "score_report" -> "绩点 gpa 成绩单"
            "campus_card" -> "饭卡 余额 充值"
            "yellow_page" -> "电话 办公 常用号码"
            "fitness" -> "体测 体育"
            "venue" -> "预约 羽毛球 篮球 健身"
            "inbox" -> "消息 通知聚合"
            "judge" -> "评教 问卷"
            "transcript" -> "成绩单 打印"
            "coupon" -> "加餐券 优惠"
            "lms" -> "思源 网课 作业"
            "agent" -> "屁岱 ai 助手"
            "zyxf" -> "资料 历年卷 讲义 学辅"
            "webvpn_converter" -> "内网 校外访问"
            "attendance" -> "考勤 打卡"
            else -> ""
        }

    companion object {
        /**
         * 分组。顺序照 `:app` 服务表的组织方式：每天要用的在前，工具类在后。
         *
         * 分组本身按 id 硬编码，没读 `:core` 的服务表 —— 那一份是 Android 侧的组织方式
         * （带分类名与排序），桌面这一端要的只是一层筛选，二十来行常量比引一层映射更直白。
         */
        val ORDER = listOf(
            "常用" to setOf("main", "schedule", "school_calendar", "library", "score_report", "campus_card"),
            "教务" to setOf(
                "school_course", "empty_room", "judge", "transcript", "exam", "jwapp", "iclassface",
                "attendance", "graduation", "credit",
            ),
            "学习" to setOf("lms", "jiaocai", "jiaocai1", "zyxf", "faculty", "fitness"),
            "生活" to setOf(
                "venue", "payment_code", "coupon", "meal", "yellow_page", "notification", "inbox",
                "bulletin", "download_manager", "match",
            ),
            "工具" to setOf(
                "settings", "accounts", "agent", "community", "feedback", "browser", "webvpn_converter",
                "update", "about",
            ),
        )

        fun groups(entries: List<DesktopEntry>): List<DesktopGroup> {
            val buckets = LinkedHashMap<String, MutableList<DesktopEntry>>()
            ORDER.forEach { (title, _) -> buckets[title] = mutableListOf() }
            buckets["其他"] = mutableListOf()
            entries.forEach { entry ->
                val title = ORDER.firstOrNull { entry.route.id in it.second }?.first ?: "其他"
                buckets.getValue(title).add(entry)
            }
            return buckets.mapNotNull { (title, list) ->
                if (list.isEmpty()) null else DesktopGroup(title, list)
            }
        }
    }
}

internal data class DesktopGroup(val title: String, val entries: List<DesktopEntry>)

/** 路由 id → 图标。桌面上宫格的识别速度全靠这一列。 */
private fun iconOf(id: String): ImageVector = when (id) {
    "main" -> Icons.Filled.Home
    "schedule" -> Icons.Filled.Schedule
    "school_calendar" -> Icons.Filled.EventNote
    "library" -> Icons.Filled.LocalLibrary
    "fitness" -> Icons.Filled.FitnessCenter
    "school_course" -> Icons.Filled.TableChart
    "score_report" -> Icons.Filled.Grading
    "judge" -> Icons.Filled.RateReview
    "campus_card" -> Icons.Filled.CreditCard
    "empty_room" -> Icons.Filled.MeetingRoom
    "venue" -> Icons.Filled.Stadium
    "inbox" -> Icons.Filled.Inbox
    "notification" -> Icons.Filled.Notifications
    "yellow_page" -> Icons.Filled.Phone
    "faculty" -> Icons.Filled.PersonSearch
    "transcript" -> Icons.Filled.Description
    "coupon" -> Icons.Filled.ConfirmationNumber
    "download_manager" -> Icons.Filled.Download
    "attendance" -> Icons.Filled.DoneAll
    "lms" -> Icons.Filled.MenuBook
    "jwapp_score", "iclassface" -> Icons.Filled.Assessment
    "community" -> Icons.Filled.Forum
    "feedback" -> Icons.Filled.Feedback
    "jiaocai", "jiaocai1" -> Icons.Filled.School
    "games", "game_2048", "game_blocks", "game_gomoku", "game_hop", "game_go", "game_xiangqi",
    "game_merge",
    -> Icons.Filled.SportsEsports
    "payment_code" -> Icons.Filled.Casino
    "webvpn_converter" -> Icons.Filled.BrowserUpdated
    "settings" -> Icons.Filled.Settings
    "accounts" -> Icons.Filled.ManageAccounts
    "agent" -> Icons.Filled.SmartToy
    "zyxf" -> Icons.Filled.MenuBook
    "browser" -> Icons.Filled.BrowserUpdated
    "match" -> Icons.Filled.ConnectWithoutContact
    else -> Icons.Filled.AutoAwesome
}

/** 分组内图标底色。教务走蓝、学习走青、生活走橙，一眼能分出区域（不用读分组名）。 */
private fun accentOf(id: String): Color = when (id) {
    in setOf("main", "schedule", "school_calendar", "library", "score_report", "campus_card") ->
        Color(0xFF2F7CFF)
    in setOf(
        "school_course", "empty_room", "judge", "transcript", "jwapp_score", "iclassface",
        "attendance", "fitness",
    ) -> Color(0xFF2FB6A6)
    in setOf("lms", "jiaocai", "jiaocai1", "zyxf", "faculty") -> Color(0xFF7A5AF8)
    in setOf(
        "venue", "payment_code", "coupon", "yellow_page", "notification", "inbox",
        "download_manager", "match",
    ) -> Color(0xFFE8892B)
    else -> Color(0xFF6B7280)
}

/** 「这一屏还没搬到桌面」的占位页。 */
@Composable
internal fun NotPortedScreen(route: AppRoute, onNavigate: (DesktopTarget) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("这一屏还没搬到桌面", color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("AppRoute.${route::class.simpleName ?: "?"}", color = cs.primary, fontSize = 13.sp)
        Text("路由 id：${route.id}", color = cs.onBackgroundVariant, fontSize = 12.sp)
        Text(
            if (route.loginType != null)
                "它需要 ${route.loginType} 的站点会话 —— 那个站点的类与它的 *Login 还在 :app，" +
                    "没搬进 :data。搬一个站点就有数据源。"
            else
                "它的取数还在 :app / campus-api 的只读端，没进 :core / :data。",
            color = cs.onBackgroundVariant,
            fontSize = 12.sp,
        )
        TextButton(
            text = "回全部页面",
            onClick = { onNavigate(DesktopTarget.Routes) },
            minWidth = 120.dp,
        )
    }
}

/** 「这一屏卡的不是取数，而是宿主的某一项能力」的说明。 */
@Composable
internal fun HostUnavailableScreen(
    route: AppRoute,
    title: String,
    lines: List<String>,
    onNavigate: (DesktopTarget) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Column(
        // 自己画底：窗口底色在离屏证据图里没有，不画就是「浅字透明底」。
        modifier = Modifier.fillMaxSize().background(cs.background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("AppRoute.${route::class.simpleName ?: "?"}", color = cs.primary, fontSize = 13.sp)
        Text("路由 id：${route.id}", color = cs.onBackgroundVariant, fontSize = 12.sp)
        lines.forEach { line -> Text(line, color = cs.onBackgroundVariant, fontSize = 12.sp) }
        TextButton(
            text = "回全部页面",
            onClick = { onNavigate(DesktopTarget.Routes) },
            minWidth = 120.dp,
        )
    }
}

/** 「没有身份就取不了数」的说明。 */
@Composable
internal fun MissingIdentityPage(
    onNavigate: (DesktopTarget) -> Unit,
    title: String,
    lines: List<String>,
) {
    val cs = MiuixTheme.colorScheme
    Surface(color = cs.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            lines.forEachIndexed { index, line ->
                Text(
                    line,
                    color = cs.onBackgroundVariant,
                    fontSize = if (index == 0) 13.sp else 12.sp,
                )
            }
            TextButton(
                text = "回全部页面",
                onClick = { onNavigate(DesktopTarget.Routes) },
                minWidth = 120.dp,
            )
        }
    }
}
