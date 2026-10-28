package com.xjtu.toolbox.zyxf

import com.xjtu.toolbox.network.HttpClients
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 仲英学辅资料站（zyxf.top）的只读接口。
 *
 * 后端开源在 [Guochaoo/zyxf](https://github.com/Guochaoo/zyxf)，`/api` 下检索、目录树、
 * 目录内容、取文件直链这几条都是**匿名可读**的（写操作才要管理员），所以这里不带任何
 * 凭据。
 *
 * 只做"查"和"读"两件事：
 * - 查：[search] / [listFolder]，把资料站当成一棵可检索的目录树；
 * - 读：[readText] 只认纯文本（txt/csv/md）。PDF、Office 走服务端的 WebOffice 预览，
 *   这类文件就给出直链，让用户去资料页看或下载。
 *
 * 移植说明（Android → KMP）：原版在 `:app`，用 `org.json` + `android.util.Log`。
 * 这里换成 kotlinx.serialization 和无副作用的 `runCatching`；
 * 落盘下载（原 `download(Context, Int)`）不在这里，桌面端用 `ZyxfDownloadTarget` 自己接。
 */
object ZyxfApi {

    const val SITE = "https://zyxf.top"
    private const val API = "$SITE/api"

    /**
     * 能走 WebOffice 在线预览的扩展名。
     *
     * 与服务端 `extPolicy.js` 的 PREVIEWABLE_EXTS 对齐（允许上传的类型减去压缩包）。
     * 这里多留一份是为了在点开之前就知道该不该给"预览"入口——
     * 不然只能先打一次请求再拿 415 回来，白占人家的限流配额。
     */
    private val PREVIEWABLE_EXTS = setOf(
        "doc", "dot", "wps", "wpt", "docx", "dotx", "rtf",
        "ppt", "pptx", "ppsx", "pps", "potx", "dpt", "dps",
        "xls", "xlt", "et", "xlsx", "xltx", "csv",
        "pdf", "txt",
    )

    /** 能直接读成文字的扩展名。其余一律只给链接。 */
    private val TEXT_EXTS = setOf("txt", "csv", "md", "markdown", "tex", "json", "log")

    /** 读文本的字节上限。资料站单个 txt 可以很大，整份灌进对话只会把上下文撑爆。 */
    private const val MAX_TEXT_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy {
        HttpClients.base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class Entry(
        val id: Int,
        val name: String,
        /** 目录路径，如 `高等数学/历年卷`；检索结果才有，浏览目录时为空。 */
        val path: String = "",
        val isFolder: Boolean,
        val sizeBytes: Long = 0,
        val ext: String = "",
        /** 上传时间，毫秒。服务端 `created_at` 是 INTEGER 秒级；0 表示没拿到。 */
        val createdAt: Long = 0,
    ) {
        /** 「3天前」这种相对时间。太久远的直接给日期，省得用户心算。 */
        val timeText: String
            get() {
                if (createdAt <= 0) return ""
                val days = (nowMillis() - createdAt) / 86_400_000L
                return when {
                    days < 0 -> ""
                    days == 0L -> "今天"
                    days == 1L -> "昨天"
                    days < 30 -> "${days}天前"
                    else -> formatDate(createdAt)
                }
            }

        /** 一周内上传的：列表里打「新」标，翻资料时一眼看出哪些是刚传的。 */
        val isFresh: Boolean
            get() = !isFolder && createdAt > 0 && (nowMillis() - createdAt) in 0 until 7 * 86_400_000L

        val extNormalized: String get() = ext.lowercase().removePrefix(".")
        val readable: Boolean get() = !isFolder && extNormalized in TEXT_EXTS

        /** 人读的大小。目录没有 size，返回空串。 */
        val sizeText: String
            get() = when {
                isFolder || sizeBytes <= 0 -> ""
                sizeBytes < 1024 -> "${sizeBytes}B"
                sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024}KB"
                else -> {
                    val tenths = sizeBytes * 10 / (1024 * 1024)
                    "${tenths / 10}.${tenths % 10}MB"
                }
            }
    }

    data class SearchResult(
        val entries: List<Entry>,
        /** 服务端截断了结果。命中太多时要提示用户把关键词写细一点。 */
        val truncated: Boolean,
    )

    data class FileLink(
        val url: String,
        val name: String,
        val ext: String,
        val mimeType: String,
        val sizeBytes: Long,
    )

    /** 服务端明确拒绝（带了错误说明）；和连不上的网络错误区分开，前者的说明可以直接给用户看。 */
    class ServerError(message: String) : RuntimeException(message)

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("Referer", "$SITE/")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // 服务端错误体是 {"error": "..."}，把它原样带出去比"HTTP 400"有用得多。
                val msg = runCatching {
                    json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw ServerError(msg?.takeIf { it.isNotBlank() } ?: "资料站出错了（HTTP ${resp.code}）")
            }
            return body
        }
    }

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun JsonObject.long(key: String, fallback: Long = 0L): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: fallback

    private fun JsonObject.str(key: String, fallback: String = ""): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: fallback

    private fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: fallback

    private fun JsonElement.objOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
    private fun JsonElement.arrOrNull(): JsonArray? = runCatching { jsonArray }.getOrNull()

    private fun folderOf(o: JsonObject) = Entry(
        id = o.int("id"),
        name = o.str("name"),
        path = o.str("folder_path"),
        isFolder = true,
        createdAt = epochMillis(o),
    )

    /**
     * 服务端 `created_at` 是 INTEGER。历史数据里秒和毫秒都出现过，
     * 按量级判断——2001 年以后的毫秒时间戳都大于 1e12，而秒级要到公元 33658 年才够。
     */
    private fun epochMillis(o: JsonObject): Long {
        val raw = o.long("created_at")
        return when {
            raw <= 0L -> 0L
            raw > 1_000_000_000_000L -> raw
            else -> raw * 1000L
        }
    }

    private fun fileOf(o: JsonObject) = Entry(
        id = o.int("id"),
        name = o.str("name"),
        path = o.str("folder_path"),
        isFolder = false,
        sizeBytes = o.long("size"),
        ext = o.str("ext"),
        createdAt = epochMillis(o),
    )

    private fun JsonArray?.mapObjects(block: (JsonObject) -> Entry): List<Entry> {
        val arr = this ?: return emptyList()
        return arr.mapNotNull { it.objOrNull()?.let(block) }
    }

    /** 全站检索。目录排在文件前，与网页端一致。 */
    fun search(keyword: String): SearchResult {
        val q = percentEncode(keyword.trim())
        val obj = json.parseToJsonElement(get("$API/search?q=$q")).jsonObject
        return SearchResult(
            entries = obj["folders"]?.arrOrNull().mapObjects(::folderOf) +
                obj["files"]?.arrOrNull().mapObjects(::fileOf),
            truncated = obj.bool("truncated"),
        )
    }

    /** 排序字段。与服务端 `SORT_FIELDS` 一一对应，别自造。 */
    enum class Sort(val key: String, val label: String) {
        /** 管理员手工排的顺序，也是网页版的默认。 */
        MANUAL("manual", "默认"),
        NAME("name", "名称"),
        TIME("created_at", "时间"),
        SIZE("size", "大小"),
    }

    /**
     * 列出一个目录。
     *
     * 排序交给服务端做（`?sort=&order=`）：目录内容可能很多，而接口本来就支持，
     * 本地再排一遍既多余，也会和"默认顺序"（管理员手工排的 sort_order）对不上。
     *
     * @param folderId 0 表示根目录（后端就是这么约定的，不是"缺省值"）
     */
    fun listFolder(
        folderId: Int = 0,
        sort: Sort = Sort.MANUAL,
        desc: Boolean = false,
    ): List<Entry> {
        val q = "?sort=${sort.key}&order=" + if (desc) "desc" else "asc"
        val obj = json.parseToJsonElement(get("$API/folders/$folderId/contents$q")).jsonObject
        return obj["folders"]?.arrOrNull().mapObjects(::folderOf) +
            obj["files"]?.arrOrNull().mapObjects(::fileOf)
    }

    /** 面包屑，用来告诉用户这份资料在哪个目录下。 */
    fun breadcrumb(folderId: Int): String = runCatching {
        val arr = json.parseToJsonElement(get("$API/folders/$folderId/contents"))
            .jsonObject["breadcrumb"]?.arrOrNull()
        arr.orEmpty()
            .mapNotNull { it.objOrNull()?.str("name") }
            .filter { it.isNotBlank() }
            .joinToString("/")
    }.getOrDefault("")

    /**
     * 取文件的临时直链。
     *
     * 服务端签的是 30 分钟有效的 OSS 链接，并且**按 IP 限流**（每分钟 60 次、每小时 240 次），
     * 所以别拿它当稳定地址缓存起来，也别在循环里连着要。
     */
    fun fileLink(fileId: Int, forDownload: Boolean = false): FileLink {
        val obj = json.parseToJsonElement(
            get("$API/files/$fileId/url" + if (forDownload) "?download=1" else "")
        ).jsonObject
        return FileLink(
            url = obj.str("url"),
            name = obj.str("name"),
            ext = obj.str("ext"),
            mimeType = obj.str("mime_type", "application/octet-stream"),
            sizeBytes = obj.long("size"),
        )
    }

    /**
     * 下载直链。
     *
     * 走 `download=1` 取链：服务端会给 OSS 加 `attachment` 头并记一次下载量，
     * 和网页端点下载按钮是同一条路径——不绕开站点的统计。
     * 返回 null 表示连直链都没拿到。落盘由调用方决定（桌面端落用户下载目录）。
     */
    fun downloadUrl(fileId: Int): FileLink? =
        runCatching { fileLink(fileId, forDownload = true) }
            .getOrNull()
            ?.takeIf { it.url.isNotBlank() }

    /**
     * WebOffice 预览凭证。
     *
     * 服务端转发阿里云 IMM 的 GenerateWebofficeToken，返回一个 `WebofficeURL` 加一枚
     * 30 分钟有效的 access token；真正把 doc/ppt/pdf 渲染出来的是阿里云的 JS-SDK，
     * 不是资料站也不是我们。所以预览这一层**只能在 WebView 里跑**，
     * 我们借的就是这套解析渲染。
     */
    data class Weboffice(
        val url: String,
        val token: String,
        val refreshToken: String,
    )

    /** 取预览凭证。415 表示这个类型本来就不支持预览（压缩包之类）。 */
    fun webofficeToken(fileId: Int): Weboffice? = runCatching {
        val obj = json.parseToJsonElement(get("$API/files/$fileId/weboffice-token")).jsonObject
        val url = obj.str("url")
        val token = obj.str("token")
        if (url.isBlank() || token.isBlank()) null
        else Weboffice(url, token, obj.str("refresh_token"))
    }.getOrNull()

    /**
     * 续期预览凭证。
     *
     * access token 只活 30 分钟，SDK 会在到期前回调续期；refresh token 活一天，
     * 它也过期时返回 null，调用方重新 [webofficeToken] 即可。
     */
    fun webofficeRefresh(fileId: Int, accessToken: String, refreshToken: String): Weboffice? = runCatching {
        val payload = buildString {
            append("{\"access_token\":")
            append(quote(accessToken))
            append(",\"refresh_token\":")
            append(quote(refreshToken))
            append('}')
        }
        val req = Request.Builder()
            .url("$API/files/$fileId/weboffice-refresh")
            .header("Accept", "application/json")
            .header("Referer", "$SITE/")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return null
            val obj = json.parseToJsonElement(body).jsonObject
            Weboffice("", obj.str("token"), obj.str("refresh_token"))
        }
    }.getOrNull()

    /** 这个类型能不能走 WebOffice 预览。和服务端 PREVIEWABLE_EXTS 保持一致。 */
    fun previewable(ext: String): Boolean =
        ext.lowercase().removePrefix(".") in PREVIEWABLE_EXTS

    /**
     * 把纯文本资料读成字符串，最多 [MAX_TEXT_BYTES]。
     *
     * 非文本格式返回 null——调用方该给链接，而不是把二进制硬解成乱码丢给模型。
     */
    fun readText(entry: Entry): String? {
        if (!entry.readable) return null
        val link = runCatching { fileLink(entry.id) }.getOrNull() ?: return null
        val req = Request.Builder().url(link.url).get().build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                val src = body.source()
                val cap = minOf(
                    MAX_TEXT_BYTES.toLong(),
                    body.contentLength().takeIf { it > 0 } ?: MAX_TEXT_BYTES.toLong(),
                )
                String(src.readByteArray(cap), Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /** 整个文件的字节（屁岱读 PDF / Office 用），超过 [maxBytes] 返回 null。 */
    fun readBytes(fileId: Int, maxBytes: Long = 40L * 1024 * 1024): Pair<String, ByteArray>? {
        val link = runCatching { fileLink(fileId) }.getOrNull() ?: return null
        if (link.sizeBytes > maxBytes) return null
        val req = Request.Builder().url(link.url).get().build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                if (body.contentLength() > maxBytes) return null
                link.name to body.bytes()
            }
        }.getOrNull()
    }
}

/** 秒级/毫秒级都归一化的当前时间。 */
private fun nowMillis(): Long = System.currentTimeMillis()

/** 只用来给超过 30 天的条目显示日期；跨平台不引 java.text。 */
private fun formatDate(millis: Long): String {
    val days = millis / 86_400_000L
    // Howard Hinnant 的 civil_from_days，纯整数运算，不依赖时区数据库。
    var z = days + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    val mm = if (m < 10) "0$m" else "$m"
    val dd = if (d < 10) "0$d" else "$d"
    z = 0
    return "$year-$mm-$dd"
}

/** RFC 3986 的 percent-encode，等价于 java.net.URLEncoder 但不在 KMP 公共代码里依赖 java.net。 */
private fun percentEncode(s: String): String {
    val out = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val safe = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code ||
            c in '0'.code..'9'.code || c == '-'.code || c == '_'.code ||
            c == '.'.code || c == '~'.code
        if (safe) out.append(c.toChar()) else out.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
    }
    return out.toString()
}

private const val HEX = "0123456789ABCDEF"

/** JSON 字符串字面量转义。 */
private fun quote(s: String): String {
    val out = StringBuilder(s.length + 2).append('"')
    for (ch in s) {
        when (ch) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (ch.code < 0x20) out.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            else out.append(ch)
        }
    }
    return out.append('"').toString()
}
