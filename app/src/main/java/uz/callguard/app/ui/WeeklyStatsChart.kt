package uz.callguard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.callguard.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun weeklyChartValues(data: List<Int>): List<Int> = data.takeLast(7).let {
    List(7 - it.size) { 0 } + it.map { count -> count.coerceAtLeast(0) }
}

/** Only Canvas drawing primitives and Canvas text: no chart library or layout-based bars. */
@Composable
fun WeeklyStatsChart(data: List<Int>) {
    val values = remember(data) { weeklyChartValues(data) }
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val labels = remember(today, locale) {
        val format = DateTimeFormatter.ofPattern("EE", locale)
        (6 downTo 0).map { today.minusDays(it.toLong()).format(format) }
    }
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val description = stringResource(R.string.weekly_chart_accessibility, labels.zip(values).joinToString { "${it.first}: ${it.second}" })
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Canvas(Modifier.fillMaxWidth().height(180.dp * fontScale).semantics { contentDescription = description }) {
        val dayText = labels.map { measurer.measure(it, style) }
        val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val ticks = listOf(0, maximum / 2, maximum).distinct()
        val tickText = ticks.map { measurer.measure(String.format(locale, "%d", it), style) }
        val left = (tickText.maxOfOrNull { it.size.width } ?: 0) + 10.dp.toPx()
        val top = (tickText.maxOfOrNull { it.size.height } ?: 0) / 2f + 8.dp.toPx()
        val bottom = size.height - (dayText.maxOfOrNull { it.size.height } ?: 0) - 12.dp.toPx()
        val right = size.width - 4.dp.toPx()
        if (right <= left || bottom <= top) return@Canvas
        val plotHeight = bottom - top
        ticks.forEachIndexed { index, tick ->
            val y = bottom - plotHeight * (tick.toDouble() / maximum).toFloat()
            drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            drawText(tickText[index], topLeft = Offset(left - tickText[index].size.width - 6.dp.toPx(), y - tickText[index].size.height / 2f))
        }
        drawLine(colors.outline, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
        drawLine(colors.outline, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        val slot = (right - left) / 7
        val width = slot * 0.56f
        values.forEachIndexed { index, count ->
            val height = plotHeight * (count.toDouble() / maximum).toFloat()
            val center = left + slot * (index + 0.5f)
            if (height > 0) drawRoundRect(if (index == 6) colors.tertiary else colors.primary,
                topLeft = Offset(center - width / 2, bottom - height), size = Size(width, height),
                cornerRadius = CornerRadius(minOf(4.dp.toPx(), height / 2, width / 2)))
            drawText(dayText[index], topLeft = Offset(center - dayText[index].size.width / 2, bottom + 8.dp.toPx()))
        }
    }
}
