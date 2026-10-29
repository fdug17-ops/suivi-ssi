package fr.perso.mescalories.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.perso.mescalories.ui.fmt0
import fr.perso.mescalories.ui.fmt1
import fr.perso.mescalories.ui.shortDate
import java.time.LocalDate

/** Courbe simple : x = jour (epochDay), y = valeur. */
@Composable
fun LineChart(points: List<Pair<Long, Double>>, target: Double?, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val targetColor = MaterialTheme.colorScheme.tertiary
    val values = points.map { it.second } + listOfNotNull(target)
    val minY = values.min() - 1
    val maxY = values.max() + 1
    val minX = points.first().first
    val maxX = points.last().first.coerceAtLeast(minX + 1)

    Column(modifier) {
        Row {
            Column(Modifier.height(180.dp).padding(end = 4.dp)) {
                Text(maxY.fmt1(), style = MaterialTheme.typography.labelSmall)
                Text("", Modifier.weight(1f))
                Text(minY.fmt1(), style = MaterialTheme.typography.labelSmall)
            }
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                fun x(d: Long) = ((d - minX).toFloat() / (maxX - minX)) * size.width
                fun y(v: Double) = size.height - ((v - minY) / (maxY - minY)).toFloat() * size.height
                for (i in 0..4) {
                    val gy = size.height * i / 4
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                }
                if (target != null) {
                    drawLine(
                        targetColor, Offset(0f, y(target)), Offset(size.width, y(target)), strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                    )
                }
                if (points.size == 1) {
                    drawCircle(line, 6.dp.toPx(), Offset(size.width / 2, y(points[0].second)))
                } else {
                    val path = Path()
                    points.forEachIndexed { i, (d, v) ->
                        if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v))
                    }
                    drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    points.forEach { (d, v) -> drawCircle(line, 3.5.dp.toPx(), Offset(x(d), y(v))) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 32.dp)) {
            Text(shortDate(LocalDate.ofEpochDay(minX)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(
                shortDate(LocalDate.ofEpochDay(points.last().first)),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Histogramme des calories par jour, avec la ligne d'objectif. */
@Composable
fun BarChart(values: List<Pair<LocalDate, Double>>, goal: Double, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val goalColor = MaterialTheme.colorScheme.tertiary
    val maxY = (values.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(goal) * 1.1
    Column(modifier) {
        Text("${maxY.fmt0()} kcal", style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            if (values.isEmpty() || maxY <= 0) return@Canvas
            val slot = size.width / values.size
            val w = (slot * 0.65f).coerceAtLeast(2f)
            values.forEachIndexed { i, (_, v) ->
                val h = (v / maxY).toFloat() * size.height
                drawRoundRect(
                    color = if (v > goal * 1.05) over else bar,
                    topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 4, w / 4),
                )
            }
            val gy = size.height - (goal / maxY).toFloat() * size.height
            drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
        }
        if (values.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                Text(shortDate(values.first().first), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(shortDate(values.last().first), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }
}
