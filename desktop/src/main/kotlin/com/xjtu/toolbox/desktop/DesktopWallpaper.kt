package com.xjtu.toolbox.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.ui.theme.LocalIsDarkTheme
import java.io.File
import org.jetbrains.skia.Image as SkiaImage

/**
 * 窗口最底下那一层壁纸。
 *
 * 它铺在整个界面**后面**，上面所有面板都靠「半透明底色 + 这一层被磨砂」透出颜色，
 * 这就是毛玻璃在桌面上的做法：不用给每个屏挂采样源（共享屏三百多处底色写的是
 * `MiuixTheme.colorScheme.surface`，一处一处改到猴年马月），而是把**背景本身**先糊掉，
 * 面板只要半透明就能看到糊过的颜色 —— 视觉上与真采样 blur 没有区别，成本却只有一层。
 *
 * [blurRadius] 为 0 时就是普通渐变壁纸（玻璃关掉的情形）。
 */
@Composable
internal fun DesktopWallpaperLayer(modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val wallpaper = remember { Wallpapers.byId(DesktopAppearance.wallpaper) ?: Wallpapers.NONE }
    val dim = DesktopAppearance.wallpaperDim
    val blur = if (DesktopAppearance.isGlassOn()) DesktopAppearance.glassBlur else 0
    val image = remember(wallpaper.id, DesktopAppearance.wallpaperImagePath) {
        if (wallpaper.shape == WallpaperShape.IMAGE) loadWallpaperImage() else null
    }

    val base = wallpaper.colors(dark)
    // 遮罩：亮色盖白、暗色盖黑 —— 同一个 dim 值在两种模式下都是"把壁纸压淡"。
    val veil = (if (dark) Color.Black else Color.White).copy(alpha = dim)

    Box(
        modifier
            .fillMaxSize()
            .then(if (blur > 0) Modifier.blur(blur.dp) else Modifier)
            .drawBehind {
                if (image != null) return@drawBehind
                val brush = when (wallpaper.shape) {
                    WallpaperShape.LINEAR -> Brush.linearGradient(
                        colors = base,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    WallpaperShape.RADIAL -> Brush.radialGradient(
                        colors = base,
                        center = Offset(size.width * 0.3f, size.height * 0.18f),
                        radius = size.maxDimension * 0.85f,
                    )
                    // 四角色块：两块大光斑叠出来，比线性渐变更像「有光」。
                    WallpaperShape.MESH -> Brush.linearGradient(
                        colors = base + base.reversed(),
                        start = Offset(size.width * 0.1f, 0f),
                        end = Offset(size.width * 0.9f, size.height),
                    )
                    WallpaperShape.IMAGE -> Brush.linearGradient(base)
                }
                drawRect(brush)
                if (wallpaper.shape == WallpaperShape.MESH) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(base.first().copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.9f),
                            radius = size.maxDimension * 0.7f,
                        ),
                    )
                }
            }
            .background(veil),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(veil))
        }
    }
}

/** 读用户选的那张图；读不出来（文件被删、不是图片）就当没选，不影响启动。 */
private fun loadWallpaperImage(): ImageBitmap? {
    val path = DesktopAppearance.wallpaperImagePath
    if (path.isBlank()) return null
    return try {
        val file = File(path)
        if (!file.isFile) null
        else SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    } catch (e: Throwable) {
        null
    }
}

/** 备忘用：给 `Size` 补一个「长边」。 */
private val Size.maxDimension: Float get() = maxOf(width, height)
