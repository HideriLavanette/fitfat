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
    /** Карточка продукта: лист со строками — открыть, а не править. */
    // Виды занятий («Тренировки», карточки-теги)
    val Cardio = strokeIcon("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10ZM5 12h3l2-3 2 5 2-2h5")
    val Run = strokeIcon("M13.5 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM9 21l2.5-5.5L9.5 12l3-3.5 2.5 3h3.5M6.5 11.5l3-3M11.5 15.5l3 2.5V21")
    val Swim = strokeIcon("M3 17c2 0 2-1.5 4.5-1.5S9.5 17 12 17s2-1.5 4.5-1.5S18.5 17 21 17M3 21c2 0 2-1.5 4.5-1.5S9.5 21 12 21s2-1.5 4.5-1.5S18.5 21 21 21M7 13l4.5-5.5 3.5 3.5M16 7.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z")
    val Fight = strokeIcon("M7 10V7a3 3 0 0 1 3-3h5a3 3 0 0 1 3 3v6a5 5 0 0 1-5 5h-2a4 4 0 0 1-4-4v-4ZM7 10h6v3M8.5 18v3h6.5v-3")
    val Team = strokeIcon("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM3.5 9.5h17M3.5 14.5h17M12 3c-2.5 3-2.5 15 0 18M12 3c2.5 3 2.5 15 0 18")
    val Bike = strokeIcon("M6 13a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM18 13a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM6 17l4-8h5l3 8M10 9 9 6H7M15 9l1-3h2")
    val Yoga = strokeIcon("M12 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM12 8.5V14M5 11l7 3 7-3M6 19.5c2-2 4-3 6-3s4 1 6 3Z")
    /** Копия в облаке. */
    val Cloud = strokeIcon("M7 18h10a4 4 0 0 0 .6-8 6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7 18Z")
    /** Отправить: стрелка вверх (макет Assistant). */
    val ArrowUp = strokeIcon("M12 19V5M5 12l7-7 7 7")
    /** Подсказка: лампочка. */
    val Bulb = strokeIcon("M9 18h6M10 21h4M12 3a6 6 0 0 0-3.6 10.8c.7.5 1.1 1.3 1.1 2.2h5c0-.9.4-1.7 1.1-2.2A6 6 0 0 0 12 3Z")
    val Card = strokeIcon("M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM8 8h8M8 12h8M8 16h5")
    val Search = strokeIcon("M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14ZM20 20l-4-4")
    val Calendar = strokeIcon("M4 6h16v14H4ZM4 10h16M8 3v4M16 3v4")
    val Pieces = strokeIcon("M4 7.5 12 3.5l8 4v9l-8 4-8-4ZM4 7.5l8 4 8-4M12 11.5v9")
    val Sweet = strokeIcon("M4 8h16v8H4ZM4 12h16M8 8V5M16 19v-3")
    val Clock = strokeIcon("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17ZM12 7.5V12l3 2")
    val Barcode = strokeIcon("M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16M8 8.5v7M11 8.5v7M13.5 8.5v7M16 8.5v7M3 12h18")
    val Camera = strokeIcon("M4 8h3l2-3h6l2 3h3v11H4ZM12 10a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z")
    val Gallery = strokeIcon("M4 5h16v14H4ZM4 16l5-5 4 4 3-3 4 4M15.5 7.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z")
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
