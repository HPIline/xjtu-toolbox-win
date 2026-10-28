package com.xjtu.toolbox.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjtu.toolbox.settings.SettingsStore
import com.xjtu.toolbox.ui.theme.LocalIsDarkTheme
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 个性化：明暗、主题色、壁纸、玻璃。
 *
 * 与「设置」分开的理由见 [DesktopTarget.Custom] —— 那一屏一半的开关在桌面上是空的。
 *
 * 每一改动都直接落盘（[DesktopAppearance] / [desktopSettings]）并回调 [onChanged]，
 * 外壳据此让主题与壁纸层重组 —— 不必「保存」，改完立刻能看到。
 */
@Composable
internal fun DesktopAppearanceScreen(
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    // 一改就重组的是这几样，读进本地 state 保证滑块跟手（store 是同步读的，
    // 但滑块每帧都在读会拖慢；这里仅作拖动中的显示值）。
    var dim by remember { mutableStateOf(DesktopAppearance.wallpaperDim) }
    var blur by remember { mutableStateOf(DesktopAppearance.glassBlur) }
    var opacity by remember { mutableStateOf(DesktopAppearance.glassOpacity) }
    var glass by remember { mutableStateOf(DesktopAppearance.glass) }
    var wallpaperId by remember { mutableStateOf(DesktopAppearance.wallpaper) }
    var wallpaperPath by remember { mutableStateOf(DesktopAppearance.wallpaperImagePath) }
    var accentTick by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "个性化",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "改动立即生效，直接落盘",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    MiniTextButton("返回", onBack)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "电脑上没法下拉刷新，这些偏好在底栏「个性化」里随时能改。" ,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        // ── 亮暗 ───────────────────────────────────────────────────
        item { SectionTitle("亮暗模式", Icons.Filled.SettingsBrightness) }
        item {
            SettingsCard {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ModeChip("跟随系统", SettingsStore.DARK_MODE_SYSTEM, Icons.Filled.SettingsBrightness, Modifier.weight(1f)) {
                        desktopSettings.darkMode = SettingsStore.DARK_MODE_SYSTEM
                        onChanged()
                    }
                    ModeChip("亮色", SettingsStore.DARK_MODE_LIGHT, Icons.Filled.LightMode, Modifier.weight(1f)) {
                        desktopSettings.darkMode = SettingsStore.DARK_MODE_LIGHT
                        onChanged()
                    }
                    ModeChip("暗色", SettingsStore.DARK_MODE_DARK, Icons.Filled.DarkMode, Modifier.weight(1f)) {
                        desktopSettings.darkMode = SettingsStore.DARK_MODE_DARK
                        onChanged()
                    }
                }
            }
        }

        // ── 主题色 ─────────────────────────────────────────────────
        item { SectionTitle("主题色", Icons.Filled.Brush) }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val current = if (desktopSettings.customColorEnabled) desktopSettings.themeSeedHex else ""
                        ACCENT_PRESETS.forEach { preset ->
                            val selected = current.equals(preset.hex, ignoreCase = true)
                            AccentSwatch(preset, selected) {
                                desktopSettings.customColorEnabled = true
                                desktopSettings.customColorHex = preset.hex
                                onChanged()
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (desktopSettings.customColorEnabled) "自定义：${desktopSettings.themeSeedHex}"
                            else "当前用默认配色",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        MiniTextButton("恢复默认") {
                            desktopSettings.customColorEnabled = false
                            desktopSettings.dynamicColor = false
                            onChanged()
                        }
                    }
                }
            }
        }

        // ── 壁纸 ───────────────────────────────────────────────────
        item { SectionTitle("壁纸", Icons.Filled.Wallpaper) }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    WallpaperGrid(
                        wallpapers = Wallpapers.ALL,
                        selectedId = wallpaperId,
                        customImagePath = wallpaperPath,
                    ) { wp ->
                        wallpaperId = wp.id
                        DesktopAppearance.wallpaper = wp.id
                        onChanged()
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (wallpaperId == "custom") {
                                wallpaperPath.ifBlank { "还没选图片" }
                            } else {
                                "自选一张本地图片也可以（jpg / png / webp）"
                            },
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        MiniTextButton("选择图片…") {
                            pickWallpaperImage()?.let { path ->
                                wallpaperPath = path
                                wallpaperId = "custom"
                                DesktopAppearance.wallpaperImagePath = path
                                DesktopAppearance.wallpaper = "custom"
                                onChanged()
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SliderRow(
                        label = "壁纸遮罩",
                        value = dim,
                        valueText = "${(dim * 100).toInt()}%",
                        onValueChange = {
                            dim = it
                            DesktopAppearance.wallpaperDim = it
                        },
                        onValueChangeFinished = onChanged,
                    )
                }
            }
        }

        // ── 玻璃 ───────────────────────────────────────────────────
        item { SectionTitle("玻璃效果", Icons.Filled.Photo) }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassChip("关闭", DesktopAppearance.GLASS_OFF, glass, Modifier.weight(1f)) {
                            glass = DesktopAppearance.GLASS_OFF
                            DesktopAppearance.glass = it
                            onChanged()
                        }
                        GlassChip("毛玻璃", DesktopAppearance.GLASS_FROSTED, glass, Modifier.weight(1f)) {
                            glass = DesktopAppearance.GLASS_FROSTED
                            DesktopAppearance.glass = it
                            onChanged()
                        }
                        GlassChip("液态玻璃", DesktopAppearance.GLASS_LIQUID, glass, Modifier.weight(1f)) {
                            glass = DesktopAppearance.GLASS_LIQUID
                            DesktopAppearance.glass = it
                            onChanged()
                        }
                    }
                    if (glass != DesktopAppearance.GLASS_OFF) {
                        Spacer(Modifier.height(4.dp))
                        SliderRow(
                            label = "磨砂强度",
                            value = blur.toFloat(),
                            valueText = "${blur}dp",
                            range = 0f..60f,
                            onValueChange = { blur = it.toInt() },
                            onValueChangeFinished = {
                                DesktopAppearance.glassBlur = blur
                                onChanged()
                            },
                        )
                        SliderRow(
                            label = "面板透明度",
                            value = opacity * 100f,
                            valueText = "${(opacity * 100).toInt()}%",
                            range = 20f..100f,
                            onValueChange = { opacity = it / 100f },
                            onValueChangeFinished = {
                                DesktopAppearance.glassOpacity = opacity
                                onChanged()
                            },
                        )
                        Text(
                            glassHint(glass),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "壁纸与玻璃是桌面独有的偏好，存在 desktop_appearance 里；" +
                    "明暗与主题色与 Android 共用同一份设置。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
            )
        }
    }
}

private fun glassHint(glass: String): String = when (glass) {
    DesktopAppearance.GLASS_FROSTED ->
        "毛玻璃：把壁纸整层磨砂，面板半透明透出糊过的颜色。性能开销最低。"
    DesktopAppearance.GLASS_LIQUID ->
        "液态玻璃：磨砂更轻、面板更透，壁纸的颜色与走向都还看得见，接近 macOS 那种观感。"
    else -> ""
}

// ── 小组件 ─────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
    Row(
        Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = MiuixTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = panelAlpha()),
        ),
    ) {
        Column(content = content)
    }
}

/** 玻璃关掉时面板要完全不透明，否则面板透出的是页面底色而不是卡片色。 */
private fun panelAlpha(): Float = DesktopAppearance.panelAlpha()

@Composable
private fun MiniTextButton(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            color = MiuixTheme.colorScheme.primary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 一枚亮暗模式按钮：选中的用主色描边 + 主色底。 */
@Composable
private fun ModeChip(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val selected = desktopSettings.darkMode == value
    val fg = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = fg, fontSize = 12.sp)
        }
    }
}

/** 主题色小圆点。 */
@Composable
private fun AccentSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    val color = remember(preset.hex) {
        preset.hex.removePrefix("#").toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: Color.Gray
    }
    Box(
        Modifier
            .size(if (selected) 44.dp else 38.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
    }
}

@Composable
private fun WallpaperGrid(
    wallpapers: List<Wallpaper>,
    selectedId: String,
    customImagePath: String,
    onSelect: (Wallpaper) -> Unit,
) {
    // 一行两个，桌面宽屏下刚好；行数不定，用 Column 而不是 LazyVerticalGrid
    // （它在外层 LazyColumn 里会因为未知高度约束崩掉）。
    val rows = wallpapers.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { wp ->
                    WallpaperTile(
                        wallpaper = wp,
                        selected = wp.id == selectedId,
                        customImagePath = customImagePath,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(wp) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WallpaperTile(
    wallpaper: Wallpaper,
    selected: Boolean,
    customImagePath: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    val colors = wallpaper.colors(dark)
    val border = if (selected) MiuixTheme.colorScheme.primary else Color.Transparent
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when (wallpaper.shape) {
                        WallpaperShape.LINEAR -> Brush.linearGradient(colors)
                        WallpaperShape.RADIAL -> Brush.radialGradient(colors)
                        WallpaperShape.MESH -> Brush.linearGradient(colors + colors.reversed())
                        WallpaperShape.IMAGE -> Brush.linearGradient(colors)
                    },
                )
                .clickable(onClick = onClick)
                // 选中描边：两圈 —— 外圈主色、内圈留白，深色壁纸上也看得见
                .then(
                    if (selected) Modifier.background(border.copy(alpha = 0.22f))
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                )
            }
            if (wallpaper.shape == WallpaperShape.IMAGE && customImagePath.isBlank()) {
                Icon(
                    Icons.Filled.Photo,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            wallpaper.label,
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GlassChip(label: String, value: String, current: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    val selected = current == value
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        modifier = modifier.clickable { onClick(value) },
    ) {
        Text(
            label,
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MiuixTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            colors = SliderDefaults.sliderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 选一张本地图片。取消返回 null。 */
private fun pickWallpaperImage(): String? {
    val dialog = FileDialog(null as Frame?, "选择壁纸", FileDialog.LOAD).apply {
        filenameFilter = java.io.FilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".gif")
        }
        isVisible = true
    }
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(dir, file).absolutePath
}
