package fr.perso.mescalories.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import fr.perso.mescalories.data.DayTotal
import fr.perso.mescalories.data.Profile
import fr.perso.mescalories.data.Repository
import fr.perso.mescalories.data.WeightEntry
import fr.perso.mescalories.ui.NumberField
import fr.perso.mescalories.ui.appViewModel
import fr.perso.mescalories.ui.dayLabel
import fr.perso.mescalories.ui.fmt0
import fr.perso.mescalories.ui.fmt1
import fr.perso.mescalories.ui.toNumber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(private val repo: Repository) : ViewModel() {
    val period = MutableStateFlow(7)
    val weights = repo.weights.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profile = repo.profileStore.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile())
    val totals = period.flatMapLatest { days ->
        val today = LocalDate.now().toEpochDay()
        repo.diary.totals(today - days + 1, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addWeight(day: LocalDate, kg: Double) = viewModelScope.launch {
        repo.weights.upsert(WeightEntry(day.toEpochDay(), kg))
    }

    fun deleteWeight(e: WeightEntry) = viewModelScope.launch { repo.weights.delete(e) }
}

@Composable
fun ProgressScreen() {
    val vm = appViewModel { ProgressViewModel(it) }
    val weights by vm.weights.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    var addWeight by remember { mutableStateOf(false) }

    val current = weights.lastOrNull()?.kg ?: profile.startWeightKg
    val goalKcal = profile.kcalGoal(current).toDouble()

    Scaffold(topBar = { TopAppBar(title = { Text("Progrès") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Poids", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            FilledTonalButton(onClick = { addWeight = true }) {
                                Icon(Icons.Filled.Add, null)
                                Text(" Peser")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val start = weights.firstOrNull()?.kg ?: profile.startWeightKg
                        val heightM = profile.heightCm / 100.0
                        val bmi = if (heightM > 0) current / (heightM * heightM) else 0.0
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Actuel", "${current.fmt1()} kg")
                            Stat("Évolution", (if (current - start > 0) "+" else "") + "${(current - start).fmt1()} kg")
                            Stat("Objectif", "${profile.targetWeightKg.fmt1()} kg")
                            Stat("IMC", bmi.fmt1())
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(bmiLabel(bmi), style = MaterialTheme.typography.bodySmall)
                        if (weights.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            LineChart(weights.map { it.day to it.kg }, profile.targetWeightKg)
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text("Enregistrez votre poids régulièrement pour suivre votre courbe.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Calories", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val options = listOf(7 to "7 jours", 30 to "30 jours", 90 to "3 mois")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { i, (days, label) ->
                                SegmentedButton(
                                    selected = period == days,
                                    onClick = { vm.period.value = days },
                                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                ) { Text(label) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val today = LocalDate.now()
                        val byDay = totals.associateBy { it.day }
                        val series = (period - 1 downTo 0).map { back ->
                            val d = today.minusDays(back.toLong())
                            d to (byDay[d.toEpochDay()]?.kcal ?: 0.0)
                        }
                        BarChart(series, goalKcal)
                        Spacer(Modifier.height(12.dp))
                        Averages(totals, goalKcal)
                    }
                }
            }

            if (weights.isNotEmpty()) {
                item { Text("Historique des pesées", style = MaterialTheme.typography.titleMedium) }
                items(weights.reversed(), key = { it.day }) { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabel(LocalDate.ofEpochDay(w.day)), Modifier.weight(1f))
                        Text("${w.kg.fmt1()} kg", fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { vm.deleteWeight(w) }) { Icon(Icons.Filled.Close, "Supprimer") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (addWeight) {
        WeightDialog(current, onDismiss = { addWeight = false }) { day, kg ->
            vm.addWeight(day, kg)
            addWeight = false
        }
    }
}

@Composable
private fun Averages(totals: List<DayTotal>, goal: Double) {
    // Moyenne sur les jours renseignés uniquement.
    if (totals.isEmpty()) {
        Text("Aucune donnée sur cette période.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val n = totals.size
    val kcal = totals.sumOf { it.kcal } / n
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("Moyenne", "${kcal.fmt0()} kcal")
        Stat("Glucides", "${(totals.sumOf { it.carbs } / n).fmt0()} g")
        Stat("Protéines", "${(totals.sumOf { it.protein } / n).fmt0()} g")
        Stat("Lipides", "${(totals.sumOf { it.fat } / n).fmt0()} g")
    }
    Spacer(Modifier.height(4.dp))
    val diff = kcal - goal
    Text(
        "$n jour(s) renseigné(s) · " +
            if (diff > 0) "${diff.fmt0()} kcal au-dessus de l'objectif en moyenne" else "${(-diff).fmt0()} kcal sous l'objectif en moyenne",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun bmiLabel(bmi: Double) = when {
    bmi <= 0 -> ""
    bmi < 18.5 -> "IMC : insuffisance pondérale"
    bmi < 25 -> "IMC : corpulence normale"
    bmi < 30 -> "IMC : surpoids"
    else -> "IMC : obésité"
}

@Composable
private fun WeightDialog(initial: Double, onDismiss: () -> Unit, onSave: (LocalDate, Double) -> Unit) {
    var kg by remember { mutableStateOf(initial.fmt1()) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle pesée") },
        text = {
            Column {
                NumberField(kg, { kg = it }, "Poids", suffix = "kg")
                TextButton(onClick = { pickDate = true }) { Text("Date : ${dayLabel(day)}") }
            }
        },
        confirmButton = {
            val v = kg.toNumber()
            TextButton(enabled = v != null && v in 20.0..400.0, onClick = { onSave(day, v!!) }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }
}
