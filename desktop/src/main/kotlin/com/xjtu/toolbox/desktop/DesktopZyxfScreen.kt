package com.xjtu.toolbox.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjtu.toolbox.ui.components.AppSearchBar
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.zyxf.ZyxfApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * 仲英学辅资料站（zyxf.top）的桌面屏。
 *
 * 对应 Android 的 `app/.../zyxf/ZyxfBrowseScreen.kt`（671 行）。这一端不重画它那套
 * 移动端的手势/下拉刷新，改成桌面更好用的形态：
 * - 面包屑可点回退，右侧独立的刷新按钮（桌面没有下拉手势）；
 * - 顶部搜索框回车即搜，清空即回到目录浏览；
 * - 文件行右侧直接给「预览」「下载」两个动作，不引入预览器。
 *
 * WebOffice 预览（doc/ppt/pdf 的阿里云 JS-SDK 渲染）**这一端没有**：Compose Desktop
 * 的渲染栈是 skiko/Skia，没有 WebView 也没有 JS 引擎。这类文件给「预览」，走系统默认
 * 浏览器去看资料站自己的预览页 —— 和 `:app` 的取舍一致，只是换了个出口。
 */
@Composable
internal fun DesktopZyxfScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MiuixTheme.colorScheme

    data class Crumb(val id: Int, val name: String)

    val stack = remember { mutableStateListOf(Crumb(0, "全部资料")) }
    var entries by remember { mutableStateOf<List<ZyxfApi.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var truncated by remember { mutableStateOf(false) }

    var sort by remember { mutableStateOf(ZyxfApi.Sort.MANUAL) }
    var desc by remember { mutableStateOf(false) }

    val downloadState = remember { mutableStateMapOf<Int, String>() }
    var toast by remember { mutableStateOf<String?>(null) }

    var job by remember { mutableStateOf<Job?>(null) }

    // 参数在调用线程上取好再交给 IO：组合里读 stack 会拿到快照，IO 线程读全局快照看到空列表。
    fun load(fetch: () -> Pair<List<ZyxfApi.Entry>, Boolean>) {
        job?.cancel()
        loading = true
        error = null
        job = scope.launch {
            try {
                val (list, cut) = withContext(Dispatchers.IO) { fetch() }
                entries = list
                truncated = cut
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = when (e) {
                    is ZyxfApi.ServerError -> e.message
                    else -> if (searching) "检索失败，检查网络后重试" else "连不上资料站，检查网络后重试"
                }
            }
            loading = false
        }
    }

    fun reload() {
        val folder = stack.last().id
        val s = sort
        val d = desc
        load { ZyxfApi.listFolder(folder, s, d) to false }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        load { ZyxfApi.search(q).let { it.entries to it.truncated } }
    }

    fun refresh() {
        if (searching) runSearch() else reload()
    }

    fun clearSearch() {
        if (!searching && query.isEmpty()) return
        query = ""
        searching = false
        truncated = false
        reload()
    }

    LaunchedEffect(Unit) { reload() }

    fun download(entry: ZyxfApi.Entry) {
        if (downloadState[entry.id] != null) return
        downloadState[entry.id] = "准备中…"
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val link = ZyxfApi.downloadUrl(entry.id) ?: return@runCatching null
                    val dir = File(System.getProperty("user.home"), "Downloads")
                    dir.mkdirs()
                    // 重名就加 (2)、 (3)，不覆盖已有文件。
                    var target = File(dir, link.name.ifBlank { entry.name })
                    if (target.exists()) {
                        val base = target.nameWithoutExtension
                        val ext = target.extension
                        var i = 2
                        while (target.exists()) {
                            val name = if (ext.isBlank()) "$base ($i)" else "$base ($i).$ext"
                            target = File(dir, name)
                            i++
                        }
                    }
                    downloadState[entry.id] = "下载中…"
                    java.net.URI(link.url).toURL().openStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.absolutePath
                }.getOrNull()
            }
            downloadState.remove(entry.id)
            toast = if (saved != null) "已保存到 $saved" else "下载失败"
        }
    }

    Surface(color = cs.surface) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {

            // ── 标题栏 ──────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = cs.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("仲英学辅资料站", style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
                    Text(
                        "历年卷、讲义、笔记 · 匿名可读",
                        style = MiuixTheme.textStyles.footnote1,
                        color = cs.onSurfaceVariantSummary,
                    )
                }
                // 桌面没有下拉手势 ⇒ 刷新必须是看得见的按钮。
                IconButton(onClick = { refresh() }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                TextButton(text = "网页版", onClick = { openInBrowser(ZyxfApi.SITE) }, minWidth = 80.dp)
            }

            Spacer(Modifier.height(14.dp))

            // ── 搜索框 ──────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AppSearchBar(
                        query = query,
                        onQueryChange = {
                            query = it
                            // 清空输入框 = 退出检索回到目录，和 Android 版一致。
                            if (it.isBlank() && searching) clearSearch()
                        },
                        label = "搜资料名、目录名……",
                        onSearch = { runSearch() },
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(text = "搜索", onClick = { runSearch() }, minWidth = 72.dp)
            }

            Spacer(Modifier.height(12.dp))

            // ── 面包屑 + 排序 ───────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!searching) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        stack.forEachIndexed { index, crumb ->
                            if (index > 0) {
                                Text("  /  ", style = MiuixTheme.textStyles.footnote1, color = cs.onSurfaceVariantSummary)
                            }
                            val last = index == stack.lastIndex
                            Text(
                                crumb.name,
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (last) cs.onSurface else cs.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (last) Modifier else Modifier
                                    .clickable {
                                        while (stack.size > index + 1) stack.removeAt(stack.lastIndex)
                                        searching = false
                                        reload()
                                    }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (truncated) "结果过多，已截断" else "检索结果（${entries.size}）",
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (truncated) cs.error else cs.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(text = "回到目录", onClick = { clearSearch() }, minWidth = 88.dp)
                    }
                }

                if (!searching) {
                    ZyxfApi.Sort.entries.forEach { s ->
                        val active = s == sort
                        Text(
                            s.label + if (active) (if (desc) " ↓" else " ↑") else "",
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) cs.primary else cs.onSurfaceVariantSummary,
                            modifier = Modifier
                                .clickable {
                                    if (active) desc = !desc else {
                                        sort = s; desc = false
                                    }
                                    reload()
                                }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── 列表 ────────────────────────────────────────────────
            Card(Modifier.weight(1f).fillMaxWidth(), cornerRadius = 20.dp) {
                Box(Modifier.fillMaxSize()) {
                    when {
                        loading -> Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("正在取资料…", style = MiuixTheme.textStyles.footnote1, color = cs.onSurfaceVariantSummary)
                        }

                        error != null -> Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = cs.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(error!!, style = MiuixTheme.textStyles.body2, color = cs.onSurfaceVariantSummary)
                            Spacer(Modifier.height(10.dp))
                            TextButton(text = "重试", onClick = { refresh() }, minWidth = 96.dp)
                        }

                        entries.isEmpty() -> Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (searching) "没搜到相关资料" else "这个目录是空的",
                                style = MiuixTheme.textStyles.body2,
                                color = cs.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(text = "刷新", onClick = { refresh() }, minWidth = 96.dp)
                        }

                        else -> LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                            items(entries, key = { "${it.isFolder}-${it.id}" }) { entry ->
                                ZyxfRow(
                                    entry = entry,
                                    downloading = downloadState[entry.id],
                                    onClick = {
                                        if (entry.isFolder) {
                                            query = ""
                                            searching = false
                                            stack.add(Crumb(entry.id, entry.name))
                                            reload()
                                        } else {
                                            download(entry)
                                        }
                                    },
                                    onPreview = { openInBrowser("${ZyxfApi.SITE}/file/${entry.id}") },
                                    canPreview = ZyxfApi.previewable(entry.ext),
                                    onDownload = { download(entry) },
                                )
                            }
                        }
                    }
                }
            }

            toast?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        it,
                        Modifier.weight(1f),
                        style = MiuixTheme.textStyles.footnote1,
                        color = cs.onSurfaceVariantSummary,
                    )
                    TextButton(text = "知道了", onClick = { toast = null }, minWidth = 80.dp)
                }
            }
        }
    }
}

@Composable
private fun ZyxfRow(
    entry: ZyxfApi.Entry,
    downloading: String?,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    canPreview: Boolean,
    onDownload: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = downloading == null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (entry.isFolder) cs.primary.copy(alpha = 0.14f) else cs.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (entry.isFolder) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (entry.isFolder) cs.primary else cs.onSurfaceVariantSummary,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isFresh) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "新",
                        fontSize = 9.sp,
                        color = cs.error,
                        modifier = Modifier
                            .background(cs.error.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
            // 检索结果才有 path；目录浏览时省掉这一段，列表更干净。
            val meta = listOfNotNull(
                entry.path.takeIf { it.isNotBlank() },
                entry.sizeText.takeIf { it.isNotBlank() },
                entry.timeText.takeIf { it.isNotBlank() },
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MiuixTheme.textStyles.footnote1,
                    color = cs.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (downloading != null) {
            Text(
                downloading,
                style = MiuixTheme.textStyles.footnote1,
                color = cs.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else if (!entry.isFolder) {
            if (canPreview) {
                IconButton(onClick = onPreview) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = "预览",
                        modifier = Modifier.size(18.dp),
                        tint = cs.onSurfaceVariantSummary,
                    )
                }
            }
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "下载",
                    modifier = Modifier.size(18.dp),
                    tint = cs.primary,
                )
            }
        }
    }
}
