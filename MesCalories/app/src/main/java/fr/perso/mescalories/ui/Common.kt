package fr.perso.mescalories.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.perso.mescalories.MesCaloriesApp
import fr.perso.mescalories.data.Repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Crée un ViewModel lié à la destination courante, avec accès au dépôt de données. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (Repository) -> VM,
): VM {
    val repo = (LocalContext.current.applicationContext as MesCaloriesApp).repository
    return viewModel(key = key, factory = viewModelFactory { initializer { create(repo) } })
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
    }
}

private val FR = Locale.FRANCE

fun Double.fmt0(): String = roundToInt().toString()

fun Double.fmt1(): String =
    if (this >= 100 || this == Math.floor(this)) roundToInt().toString()
    else String.format(FR, "%.1f", this)

/** Analyse un nombre saisi au clavier (virgule ou point). */
fun String.toNumber(): Double? = replace(',', '.').trim().toDoubleOrNull()

fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        today.plusDays(1) -> "Demain"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", FR)).replaceFirstChar { it.titlecase(FR) }
    }
}

fun shortDate(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("d MMM", FR))

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }) },
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { text -> @Composable { Text(text) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Anneau de progression des calories. */
@Composable
fun CalorieRing(
    progress: Float,
    size: Dp,
    color: Color,
    track: Color,
    content: @Composable () -> Unit,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                color, 135f, 270f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun MacroBar(label: String, value: Double, goal: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f },
            color = if (value > goal * 1.05 && goal > 0) MaterialTheme.colorScheme.error else color,
            trackColor = color.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text("${value.fmt0()} / ${goal.fmt0()} g", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun NutrientRow(label: String, value: String, bold: Boolean = false, indent: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = if (indent) 16.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}
