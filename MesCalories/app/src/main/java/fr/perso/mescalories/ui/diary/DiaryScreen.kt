package fr.perso.mescalories.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import fr.perso.mescalories.data.DiaryEntry
import fr.perso.mescalories.data.Meal
import fr.perso.mescalories.data.Nutrients
import fr.perso.mescalories.ui.CalorieRing
import fr.perso.mescalories.ui.MacroBar
import fr.perso.mescalories.ui.Routes
import fr.perso.mescalories.ui.appViewModel
import fr.perso.mescalories.ui.dayLabel
import fr.perso.mescalories.ui.fmt0
import fr.perso.mescalories.ui.fmt1
import fr.perso.mescalories.ui.switchTab
import fr.perso.mescalories.ui.theme.MacroColors
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val GLASS_ML = 250

@Composable
fun DiaryScreen(nav: NavHostController) {
    val vm = appViewModel { DiaryViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var quickEdit by remember { mutableStateOf<DiaryEntry?>(null) }
    var showExercise by remember { mutableStateOf(false) }

    val dayEpoch = s.day.toEpochDay()
    val targets = s.profile.targets(s.weightKg)
    val eaten = s.entries.fold(Nutrients()) { acc, e -> acc + e.nutrients }
    val burned = s.exercises.sumOf { it.kcal }
    val budget = targets.kcal + if (s.profile.addExerciseKcal) burned else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.shiftDay(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Jour précédent")
                        }
                        Text(
                            dayLabel(s.day),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { vm.shiftDay(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Jour suivant")
                        }
                    }
                },
                actions = {
                    if (s.day != LocalDate.now()) {
                        IconButton(onClick = vm::goToday) { Icon(Icons.Filled.Today, "Aujourd'hui") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.loaded && !s.profile.configured) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Bienvenue !", style = MaterialTheme.typography.titleMedium)
                            Text("Renseignez votre profil pour calculer votre objectif calorique personnalisé.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { nav.switchTab("profile") }) { Text("Configurer mon profil") }
                        }
                    }
                }
            }
            item { SummaryCard(eaten, budget, burned, targets.carbs, targets.protein, targets.fat) }

            Meal.entries.forEach { meal ->
                item(key = "meal${meal.ordinal}") {
                    MealCard(
                        meal = meal,
                        entries = s.entries.filter { it.meal == meal.ordinal },
                        recommended = targets.kcal * meal.share,
                        onAdd = { nav.navigate(Routes.add(dayEpoch, meal.ordinal)) },
                        onOpen = { e ->
                            if (e.isQuickAdd) quickEdit = e
                            else nav.navigate(Routes.foodForEntry(e.foodId!!, e.id))
                        },
                        onCopyYesterday = {
                            vm.copyFromYesterday(meal.ordinal) { n ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (n == 0) "Rien à copier : ce repas était vide hier."
                                        else "$n aliment(s) copié(s) depuis hier."
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Activités", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${burned.fmt0()} kcal", fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { showExercise = true }) { Icon(Icons.Filled.Add, "Ajouter une activité") }
                        }
                        s.exercises.forEach { ex ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(ex.name)
                                    if (ex.minutes > 0) Text("${ex.minutes} min", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${ex.kcal.fmt0()} kcal")
                                IconButton(onClick = { vm.deleteExercise(ex) }) { Icon(Icons.Filled.Close, "Supprimer") }
                            }
                        }
                    }
                }
            }

            item { WaterCard(s.waterMl, targets.waterMl, vm::setWater) }
        }
    }

    quickEdit?.let { e ->
        QuickAddDialog(
            initial = e, day = e.day, meal = e.meal,
            onDismiss = { quickEdit = null },
            onSave = { vm.saveQuickAdd(it); quickEdit = null },
            onDelete = { vm.deleteEntry(it); quickEdit = null },
        )
    }
    if (showExercise) {
        ExerciseDialog(
            weightKg = s.weightKg,
            onDismiss = { showExercise = false },
            onSave = { name, min, kcal -> vm.addExercise(name, min, kcal); showExercise = false },
        )
    }
}

@Composable
private fun SummaryCard(
    eaten: Nutrients,
    budget: Double,
    burned: Double,
    carbsGoal: Double,
    proteinGoal: Double,
    fatGoal: Double,
) {
    val remaining = budget - eaten.kcal
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                SideValue(eaten.kcal.fmt0(), "Mangées")
                CalorieRing(
                    progress = if (budget > 0) (eaten.kcal / budget).toFloat() else 0f,
                    size = 150.dp,
                    color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    track = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            (if (remaining < 0) -remaining else remaining).fmt0(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(if (remaining < 0) "kcal en trop" else "kcal restantes", style = MaterialTheme.typography.bodySmall)
                    }
                }
                SideValue(burned.fmt0(), "Dépensées")
            }
            Spacer(Modifier.height(4.dp))
            Text("Objectif : ${budget.fmt0()} kcal", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MacroBar("Glucides", eaten.carbs, carbsGoal, MacroColors.carbs, Modifier.weight(1f))
                MacroBar("Protéines", eaten.protein, proteinGoal, MacroColors.protein, Modifier.weight(1f))
                MacroBar("Lipides", eaten.fat, fatGoal, MacroColors.fat, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SideValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<DiaryEntry>,
    recommended: Double,
    onAdd: () -> Unit,
    onOpen: (DiaryEntry) -> Unit,
    onCopyYesterday: () -> Unit,
) {
    val total = entries.fold(Nutrients()) { acc, e -> acc + e.nutrients }
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(meal.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${total.kcal.fmt0()} / ${recommended.fmt0()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalIconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Ajouter un aliment") }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, "Options")
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Copier le repas d'hier") }, onClick = { menu = false; onCopyYesterday() })
                    }
                }
            }
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(vertical = 8.dp, horizontal = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val detail = when {
                                e.isQuickAdd -> "Ajout rapide"
                                e.amountLabel.isNotBlank() -> e.amountLabel
                                else -> "${e.grams.fmt0()} g"
                            }
                            Text(
                                "$detail · G ${e.carbs.fmt1()} · P ${e.protein.fmt1()} · L ${e.fat.fmt1()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${e.kcal.fmt0()} kcal", modifier = Modifier.padding(end = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterCard(ml: Int, goal: Int, onChange: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalDrink, null, tint = MacroColors.water)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Eau", style = MaterialTheme.typography.titleMedium)
                    Text("${"%.2f".format(ml / 1000.0).replace('.', ',')} / ${"%.1f".format(goal / 1000.0).replace('.', ',')} L", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalIconButton(onClick = { onChange(ml - GLASS_ML) }, enabled = ml > 0) {
                    Icon(Icons.Filled.Remove, "Retirer un verre")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { onChange(ml + GLASS_ML) }) {
                    Icon(Icons.Filled.Add, "Ajouter un verre")
                }
            }
            Spacer(Modifier.height(8.dp))
            val glasses = (goal / GLASS_ML).coerceAtLeast(1)
            val filled = ml / GLASS_ML
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(maxOf(glasses, filled)) { i ->
                    Icon(
                        Icons.Filled.LocalDrink,
                        contentDescription = null,
                        tint = if (i < filled) MacroColors.water else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(26.dp).clickable { onChange((i + 1) * GLASS_ML) },
                    )
                }
            }
        }
    }
}
