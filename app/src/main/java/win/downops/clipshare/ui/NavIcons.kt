package win.downops.clipshare.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** Draws a 24dp icon that picks up the current content color (selected state). */
@Composable
private inline fun navIcon(crossinline content: DrawScope.(tint: Color) -> Unit) {
    val tint = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) { content(tint) }
}

@Composable
fun NavHomeIcon() = navIcon { tint ->
    val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.13f)
        lineTo(size.width * 0.87f, size.height * 0.42f)
        lineTo(size.width * 0.87f, size.height * 0.87f)
        lineTo(size.width * 0.13f, size.height * 0.87f)
        lineTo(size.width * 0.13f, size.height * 0.42f)
        close()
    }
    drawPath(path, tint, style = stroke)
    drawLine(tint, Offset(size.width * 0.5f, size.height * 0.88f), Offset(size.width * 0.5f, size.height * 0.55f), stroke.width, StrokeCap.Round)
}

@Composable
fun NavCaptureIcon() = navIcon { tint ->
    val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round)
    val outline = Rect(size.width * 0.05f, size.height * 0.22f, size.width * 0.95f, size.height * 0.78f)
    drawOval(tint, topLeft = outline.topLeft, size = outline.size, style = stroke)
    drawCircle(tint, radius = size.minDimension * 0.15f, center = center, style = stroke)
}

@Composable
fun NavLogsIcon() = navIcon { tint ->
    val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
    for (fraction in listOf(0.3f, 0.5f, 0.7f)) {
        val y = size.height * fraction
        drawLine(tint, Offset(size.width * 0.16f, y), Offset(size.width * 0.84f, y), stroke.width, StrokeCap.Round)
    }
}

@Composable
fun NavSaveIcon() = navIcon { tint ->
    val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width * 0.7f
    val h = size.height * 0.8f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    drawRoundRect(
        tint,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(size.minDimension * 0.04f),
        style = stroke,
    )
    drawLine(tint, Offset(left + w * 0.28f, top), Offset(left + w * 0.28f, top + h * 0.42f), stroke.width, StrokeCap.Round)
    drawLine(tint, Offset(left + w * 0.28f, top + h * 0.42f), Offset(left + w * 0.72f, top + h * 0.42f), stroke.width, StrokeCap.Round)
    drawRoundRect(
        tint,
        topLeft = Offset(left + w * 0.2f, top + h * 0.62f),
        size = Size(w * 0.6f, h * 0.24f),
        cornerRadius = CornerRadius(size.minDimension * 0.02f),
        style = stroke,
    )
}

@Composable
fun NavSettingsIcon() = navIcon { tint ->
    val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round)
    drawCircle(tint, radius = size.minDimension * 0.24f, center = center, style = stroke)
    val toothWidth = size.minDimension * 0.13f
    val toothHeight = size.minDimension * 0.15f
    val toothCenterR = size.minDimension * 0.30f
    for (i in 0 until 8) {
        rotate(degrees = i * 45f - 90f, pivot = center) {
            drawRoundRect(
                tint,
                topLeft = Offset(center.x - toothWidth / 2f, center.y - toothCenterR - toothHeight / 2f),
                size = Size(toothWidth, toothHeight),
                cornerRadius = CornerRadius(toothWidth / 2f),
                style = stroke,
            )
        }
    }
}