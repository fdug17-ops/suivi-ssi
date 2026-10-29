package fr.perso.mescalories.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.perso.mescalories.data.DiaryEntry
import fr.perso.mescalories.ui.NumberField
import fr.perso.mescalories.ui.fmt0
import fr.perso.mescalories.ui.fmt1
import fr.perso.mescalories.ui.toNumber

/** Ajout rapide : calories (et éventuellement macros) saisies directement. */
@Composable
fun QuickAddDialog(
    initial: DiaryEntry?,
    day: Long,
    meal: Int,
    onDismiss: () -> Unit,
    onSave: (DiaryEntry) -> Unit,
    onDelete: ((DiaryEntry) -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var kcal by rememberSaveable { mutableStateOf(initial?.kcal?.fmt0() ?: "") }
    var protein by rememberSaveable { mutableStateOf(initial?.protein?.fmt1() ?: "") }
    var carbs by rememberSaveable { mutableStateOf(initial?.carbs?.fmt1() ?: "") }
    var fat by rememberSaveable { mutableStateOf(initial?.fat?.fmt1() ?: "") }
    val kcalValue = kcal.toNumber()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ajout rapide" else "Modifier l'ajout rapide") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom (facultatif)") }, singleLine = true)
                NumberField(kcal, { kcal = it }, "Calories", suffix = "kcal", decimal = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(carbs, { carbs = it }, "Gluc.", Modifier.weight(1f), "g")
                    NumberField(protein, { protein = it }, "Prot.", Modifier.weight(1f), "g")
                    NumberField(fat, { fat = it }, "Lip.", Modifier.weight(1f), "g")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = kcalValue != null,
                onClick = {
                    val base = initial ?: DiaryEntry(
                        day = day, meal = meal, foodId = null, name = "", grams = 0.0,
                        kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0,
                    )
                    onSave(
                        base.copy(
                            name = name.ifBlank { "Ajout rapide" },
                            kcal = kcalValue ?: 0.0,
                            protein = protein.toNumber() ?: 0.0,
                            carbs = carbs.toNumber() ?: 0.0,
                            fat = fat.toNumber() ?: 0.0,
                        )
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

/** Activités courantes avec leur équivalent métabolique (MET). */
private data class ActivityPreset(val name: String, val met: Double)

private val presets = listOf(
    ActivityPreset("Marche", 3.5),
    ActivityPreset("Marche rapide", 5.0),
    ActivityPreset("Randonnée", 6.0),
    ActivityPreset("Course à pied", 9.8),
    ActivityPreset("Vélo", 7.5),
    ActivityPreset("Vélo d'appartement", 6.8),
    ActivityPreset("Natation", 7.0),
    ActivityPreset("Musculation", 5.0),
    ActivityPreset("Fitness / cardio", 7.3),
    ActivityPreset("Yoga / Pilates", 2.8),
    ActivityPreset("Danse", 5.5),
    ActivityPreset("Football", 7.0),
    ActivityPreset("Tennis", 7.3),
    ActivityPreset("Badminton", 5.5),
    ActivityPreset("Ménage", 3.3),
    ActivityPreset("Jardinage", 4.0),
)

@Composable
fun ExerciseDialog(
    weightKg: Double,
    onDismiss: () -> Unit,
    onSave: (name: String, minutes: Int, kcal: Double) -> Unit,
) {
    var preset by remember { mutableStateOf<ActivityPreset?>(null) }
    var custom by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("30") }
    var kcal by remember { mutableStateOf("") }

    val mins = minutes.toNumber()?.toInt() ?: 0
    val computed = preset?.let { it.met * weightKg * mins / 60.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une activité") },
        text = {
            if (preset == null && !custom) {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(presets) { p ->
                        Text(
                            p.name,
                            Modifier.fillMaxWidth().clickable { preset = p }.padding(vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                    item {
                        Text(
                            "Autre (saisir les calories)",
                            Modifier.fillMaxWidth().clickable { custom = true }.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (custom) {
                        OutlinedTextField(name, { name = it }, label = { Text("Activité") }, singleLine = true)
                    } else {
                        Text(preset!!.name, style = MaterialTheme.typography.titleMedium)
                    }
                    NumberField(minutes, { minutes = it }, "Durée", suffix = "min", decimal = false)
                    if (custom) {
                        NumberField(kcal, { kcal = it }, "Calories dépensées", suffix = "kcal", decimal = false)
                    } else {
                        Text("≈ ${computed?.fmt0() ?: 0} kcal dépensées (pour ${weightKg.fmt1()} kg)")
                    }
                }
            }
        },
        confirmButton = {
            val total = if (custom) kcal.toNumber() else computed
            TextButton(
                enabled = (preset != null || custom) && (total ?: 0.0) > 0,
                onClick = { onSave(if (custom) name.ifBlank { "Activité" } else preset!!.name, mins, total ?: 0.0) },
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
