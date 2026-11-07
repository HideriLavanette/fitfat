package app.fitfat.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Иконки из макетов (`docs/mockups`): линейные SVG 24×24, скруглённые концы.
 * Цвет задаётся через `tint` у Icon, толщина — как в макете.
 */
fun strokeIcon(d: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

object MockIcons {
    val ChevronLeft = strokeIcon("M15 5l-7 7 7 7")
    val ChevronRight = strokeIcon("M9 5l7 7-7 7")
    val Close = strokeIcon("M6 6l12 12M18 6 6 18")
    val Plus = strokeIcon("M12 5v14M5 12h14")
    val PlusBold = strokeIcon("M12 5v14M5 12h14", 2.2f)
    val Minus = strokeIcon("M5 12h14")
    val Check = strokeIcon("M5 12l5 5 9-10")
    val Repeat = strokeIcon("M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3")
    val Delete = strokeIcon("M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13")
    val Edit = strokeIcon("M4 20h4L19 9l-4-4L4 16v4ZM13.5 6.5l4 4")
    val Clock = strokeIcon("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17ZM12 7.5V12l3 2")
    val Barcode = strokeIcon("M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16M8 8.5v7M11 8.5v7M13.5 8.5v7M16 8.5v7M3 12h18")
    val Dish = strokeIcon("M4 10h16v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4ZM2 10h20M9 6c0-1 1-1 1-2M14 6c0-1 1-1 1-2")

    // Типы дня
    val Sun = strokeIcon("M12 4v2M12 18v2M4 12h2M18 12h2M6.3 6.3l1.4 1.4M16.3 16.3l1.4 1.4M6.3 17.7l1.4-1.4M16.3 7.7l1.4-1.4M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z")
    val Dumbbell = strokeIcon("M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11")
    val Drop = strokeIcon("M12 3.5c3 4 6 7.2 6 10.5a6 6 0 0 1-12 0c0-3.3 3-6.5 6-10.5Z")

    // Навбар
    val NavToday = strokeIcon("M4 6h16M4 12h16M4 18h10")
    val NavWeight = strokeIcon("M3 17l6-6 4 4 8-8")
    val NavAssistant = strokeIcon("M12 3l2.2 5.8L20 11l-5.8 2.2L12 19l-2.2-5.8L4 11l5.8-2.2Z")
    val NavProfile = strokeIcon("M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM4 21a8 8 0 0 1 16 0")
}
