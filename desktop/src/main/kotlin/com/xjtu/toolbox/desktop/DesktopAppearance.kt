package com.xjtu.toolbox.desktop

import androidx.compose.ui.graphics.Color
import com.xjtu.toolbox.platform.keyValueStore

/**
 * 桌面这一端的外观偏好：壁纸、玻璃、明暗、主题色。
 *
 * 单独一个 store，不塞进 `:model` 的 `SettingsStore` —— 那两份键是 Android 与桌面共用的
 * （导航栏样式、底栏标签、震动……），而壁纸与玻璃是桌面独有的三样，塞进去只会让 Android
 * 侧多出一堆永远读不到的键。
 *
 * 明暗那两档仍然复用 `SettingsStore.darkMode` / `dynamicColor` / `customColorHex`：
 * 设置屏已经在改它们，另起一套会出现「两个开关改同一件事」。
 */
internal object DesktopAppearance {

    private val store by lazy { keyValueStore(FILE) }

    // ── 壁纸 ──────────────────────────────────────────────────────

    /** 壁纸 preset 的 id；[WALLPAPER_NONE] = 纯色背景（等于不要壁纸）。 */
    var wallpaper: String
        get() = store.getString(KEY_WALLPAPER)?.takeIf { id -> Wallpapers.byId(id) != null } ?: Wallpapers.DEFAULT_ID
        set(value) = store.putString(KEY_WALLPAPER, value)

    /** 自定义图片的绝对路径；空串 = 没选过。 */
    var wallpaperImagePath: String
        get() = store.getString(KEY_WALLPAPER_IMAGE).orEmpty()
        set(value) = store.putString(KEY_WALLPAPER_IMAGE, value.ifEmpty { null })

    /** 遮罩强度 0..1：越大壁纸越淡，内容越好读。 */
    var wallpaperDim: Float
        get() = store.getInt(KEY_WALLPAPER_DIM, 32).coerceIn(0, 90) / 100f
        set(value) = store.putInt(KEY_WALLPAPER_DIM, (value * 100).toInt().coerceIn(0, 90))

    // ── 玻璃 ──────────────────────────────────────────────────────

    /** 玻璃形态：[GLASS_OFF] / [GLASS_FROSTED]（毛玻璃）/ [GLASS_LIQUID]（液态玻璃）。 */
    var glass: String
        get() = store.getString(KEY_GLASS)?.takeIf { it in GLASS_STYLES } ?: GLASS_FROSTED
        set(value) = store.putString(KEY_GLASS, value.takeIf { it in GLASS_STYLES } ?: GLASS_FROSTED)

    /** 壁纸那一层被磨砂的程度（dp）。毛玻璃档用得重，液态玻璃档用得轻。 */
    var glassBlur: Int
        get() = store.getInt(KEY_GLASS_BLUR, 24).coerceIn(0, 60)
        set(value) = store.putInt(KEY_GLASS_BLUR, value.coerceIn(0, 60))

    /** 面板透明度 0..1：越大越"实"，越小越透。 */
    var glassOpacity: Float
        get() = store.getInt(KEY_GLASS_OPACITY, 62).coerceIn(20, 100) / 100f
        set(value) = store.putInt(KEY_GLASS_OPACITY, (value * 100).toInt().coerceIn(20, 100))

    fun isGlassOn(): Boolean = glass != GLASS_OFF

    /** 面板底色的 alpha：玻璃关掉时给 1（完全不透明）。 */
    fun panelAlpha(): Float = if (!isGlassOn()) 1f else glassOpacity

    const val FILE = "desktop_appearance"

    private const val KEY_WALLPAPER = "wallpaper"
    private const val KEY_WALLPAPER_IMAGE = "wallpaper_image"
    private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
    private const val KEY_GLASS = "glass"
    private const val KEY_GLASS_BLUR = "glass_blur"
    private const val KEY_GLASS_OPACITY = "glass_opacity"

    const val GLASS_OFF = "off"
    const val GLASS_FROSTED = "frosted"
    const val GLASS_LIQUID = "liquid"
    val GLASS_STYLES = setOf(GLASS_OFF, GLASS_FROSTED, GLASS_LIQUID)
}

/** 一张内置壁纸：两到三个色标 + 画法。亮暗各一套色，切模式不会变成"晚上也在晒太阳"。 */
internal data class Wallpaper(
    val id: String,
    val label: String,
    /** [WallpaperShape.LINEAR] 斜向渐变 / [WallpaperShape.RADIAL] 中心光晕 / [WallpaperShape.MESH] 四角色块。 */
    val shape: WallpaperShape,
    val light: List<Color>,
    val dark: List<Color>,
) {
    fun colors(isDark: Boolean): List<Color> = if (isDark) dark else light
}

internal enum class WallpaperShape { LINEAR, RADIAL, MESH, IMAGE }

internal object Wallpapers {

    /** 纯色（默认）：等于不要壁纸，但保留「玻璃」能透出一点点页面底色。 */
    val NONE = Wallpaper(
        id = "none",
        label = "纯色",
        shape = WallpaperShape.LINEAR,
        light = listOf(Color(0xFFF0F1F3), Color(0xFFF0F1F3)),
        dark = listOf(Color(0xFF121316), Color(0xFF121316)),
    )

    val ALL = listOf(
        NONE,
        Wallpaper(
            id = "dawn",
            label = "晨雾",
            shape = WallpaperShape.LINEAR,
            light = listOf(Color(0xFFCFE4FF), Color(0xFFF3E7FF), Color(0xFFFFF1E6)),
            dark = listOf(Color(0xFF16213A), Color(0xFF241B33), Color(0xFF2A1E22)),
        ),
        Wallpaper(
            id = "dusk",
            label = "暮山",
            shape = WallpaperShape.LINEAR,
            light = listOf(Color(0xFFFFD9C0), Color(0xFFE7C7F2), Color(0xFFC9D7FF)),
            dark = listOf(Color(0xFF2B1A16), Color(0xFF231627), Color(0xFF141A2E)),
        ),
        Wallpaper(
            id = "aurora",
            label = "极光",
            shape = WallpaperShape.MESH,
            light = listOf(Color(0xFFB8F2E6), Color(0xFFAEDFF7), Color(0xFFE4D6FF), Color(0xFFC9F5D9)),
            dark = listOf(Color(0xFF0E3B36), Color(0xFF12314D), Color(0xFF2A1F4A), Color(0xFF10342A)),
        ),
        Wallpaper(
            id = "iris",
            label = "鸢尾",
            shape = WallpaperShape.RADIAL,
            light = listOf(Color(0xFFDCD3FF), Color(0xFFF4EFFF), Color(0xFFE9F0FF)),
            dark = listOf(Color(0xFF2A2350), Color(0xFF1A1730), Color(0xFF12121F)),
        ),
        Wallpaper(
            id = "jade",
            label = "青瓷",
            shape = WallpaperShape.RADIAL,
            light = listOf(Color(0xFFCDEEE4), Color(0xFFEFF8F4), Color(0xFFE3F1F7)),
            dark = listOf(Color(0xFF10322C), Color(0xFF12211F), Color(0xFF111A20)),
        ),
        Wallpaper(
            id = "ember",
            label = "暮色",
            shape = WallpaperShape.MESH,
            light = listOf(Color(0xFFFFE0CC), Color(0xFFFFD6E0), Color(0xFFE8D9FF), Color(0xFFFFF3DC)),
            dark = listOf(Color(0xFF3A2118), Color(0xFF331726), Color(0xFF221A38), Color(0xFF32271A)),
        ),
        Wallpaper(
            id = "graphite",
            label = "石墨",
            shape = WallpaperShape.LINEAR,
            light = listOf(Color(0xFFE3E5E9), Color(0xFFD6D9DF), Color(0xFFEDEFF2)),
            dark = listOf(Color(0xFF1A1C20), Color(0xFF23262B), Color(0xFF15171A)),
        ),
        Wallpaper(
            id = "custom",
            label = "自选图片",
            shape = WallpaperShape.IMAGE,
            light = listOf(Color(0xFFF0F1F3), Color(0xFFE4E6EA)),
            dark = listOf(Color(0xFF121316), Color(0xFF1B1D22)),
        ),
    )

    const val DEFAULT_ID = "dawn"

    fun byId(id: String): Wallpaper? = ALL.firstOrNull { it.id == id }
}

/** 主题色的几个预设（点一下换一个种子色）。与 `SettingsStore.customColorHex` 同一套格式。 */
internal data class AccentPreset(val label: String, val hex: String)

internal val ACCENT_PRESETS = listOf(
    AccentPreset("西交蓝", "#2F7CFF"),
    AccentPreset("青瓷", "#2FB6A6"),
    AccentPreset("鸢尾", "#7A5AF8"),
    AccentPreset("暖橙", "#E8892B"),
    AccentPreset("莓红", "#E0507A"),
    AccentPreset("松绿", "#3F9B5C"),
    AccentPreset("石墨", "#5B6373"),
    AccentPreset("琥珀", "#C99A2E"),
)
