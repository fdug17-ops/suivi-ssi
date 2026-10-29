package fr.perso.mescalories.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import fr.perso.mescalories.data.Activity
import fr.perso.mescalories.data.Goal
import fr.perso.mescalories.data.Profile
import fr.perso.mescalories.data.Repository
import fr.perso.mescalories.data.WeightEntry
import fr.perso.mescalories.ui.NumberField
import fr.perso.mescalories.ui.NutrientRow
import fr.perso.mescalories.ui.appViewModel
import fr.perso.mescalories.ui.fmt0
import fr.perso.mescalories.ui.fmt1
import fr.perso.mescalories.ui.toNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ProfileViewModel(private val repo: Repository) : ViewModel() {
    val loaded = MutableStateFlow<Pair<Profile, Double>?>(null)

    init {
        viewModelScope.launch {
            val p = repo.profileStore.current()
            val w = repo.weights.all().first().lastOrNull()?.kg ?: p.startWeightKg
            loaded.value = p to w
        }
    }

    fun save(p: Profile, weightKg: Double, done: () -> Unit) = viewModelScope.launch {
        val weights = repo.weights.all().first()
        val profile = if (weights.isEmpty()) p.copy(startWeightKg = weightKg) else p
        repo.profileStore.save(profile.copy(configured = true))
        // Le poids saisi ici est enregistré comme pesée du jour s'il a changé.
        if (weights.lastOrNull()?.kg != weightKg) {
            repo.weights.upsert(WeightEntry(LocalDate.now().toEpochDay(), weightKg))
        }
        loaded.value = profile.copy(configured = true) to weightKg
        done()
    }

    fun exportBackup(context: Context, uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        val msg = try {
            val json = repo.exportJson()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
            "Sauvegarde exportée."
        } catch (e: Exception) {
            "Échec de l'export : ${e.message}"
        }
        done(msg)
    }

    fun importBackup(context: Context, uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        val msg = try {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            } ?: error("fichier illisible")
            repo.importJson(text)
            val p = repo.profileStore.current()
            loaded.value = p to (repo.weights.all().first().lastOrNull()?.kg ?: p.startWeightKg)
            "Sauvegarde restaurée."
        } catch (e: Exception) {
            "Échec de l'import : ${e.message}"
        }
        done(msg)
    }
}

private data class MacroPreset(val label: String, val carbs: Int, val protein: Int, val fat: Int)

private val macroPresets = listOf(
    MacroPreset("Équilibré", 50, 20, 30),
    MacroPreset("Riche en protéines", 40, 30, 30),
    MacroPreset("Pauvre en glucides", 25, 35, 40),
    MacroPreset("Cétogène", 5, 25, 70),
)

@Composable
fun ProfileScreen() {
    val vm = appViewModel { ProfileViewModel(it) }
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val data = loaded ?: return
    ProfileForm(vm, data.first, data.second)
}

@Composable
private fun ProfileForm(vm: ProfileViewModel, initial: Profile, initialWeight: Double) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }

    var male by remember(initial) { mutableStateOf(initial.male) }
    var birthYear by remember(initial) { mutableStateOf(initial.birthYear.toString()) }
    var height by remember(initial) { mutableStateOf(initial.heightCm.toString()) }
    var weight by remember(initialWeight) { mutableStateOf(initialWeight.fmt1()) }
    var target by remember(initial) { mutableStateOf(initial.targetWeightKg.fmt1()) }
    var goal by remember(initial) { mutableStateOf(initial.goal) }
    var rate by remember(initial) { mutableStateOf(initial.weeklyRateKg) }
    var activity by remember(initial) { mutableStateOf(initial.activity) }
    var customOn by remember(initial) { mutableStateOf(initial.customKcal > 0) }
    var customKcal by remember(initial) { mutableStateOf(if (initial.customKcal > 0) initial.customKcal.toString() else "") }
    var carbs by remember(initial) { mutableStateOf(initial.carbsPct.toString()) }
    var protein by remember(initial) { mutableStateOf(initial.proteinPct.toString()) }
    var fat by remember(initial) { mutableStateOf(initial.fatPct.toString()) }
    var water by remember(initial) { mutableStateOf(initial.waterGoalMl.toString()) }
    var addExercise by remember(initial) { mutableStateOf(initial.addExerciseKcal) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    val w = weight.toNumber() ?: initialWeight
    val draft = initial.copy(
        male = male,
        birthYear = birthYear.toIntOrNull()?.coerceIn(1900, LocalDate.now().year) ?: initial.birthYear,
        heightCm = height.toIntOrNull()?.coerceIn(80, 250) ?: initial.heightCm,
        targetWeightKg = target.toNumber() ?: initial.targetWeightKg,
        goal = goal,
        weeklyRateKg = rate,
        activity = activity,
        customKcal = if (customOn) customKcal.toIntOrNull() ?: 0 else 0,
        carbsPct = carbs.toIntOrNull() ?: 0,
        proteinPct = protein.toIntOrNull() ?: 0,
        fatPct = fat.toIntOrNull() ?: 0,
        waterGoalMl = water.toIntOrNull()?.coerceIn(0, 10_000) ?: initial.waterGoalMl,
        addExerciseKcal = addExercise,
    )
    val pctSum = draft.carbsPct + draft.proteinPct + draft.fatPct
    val targets = draft.targets(w)

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportBackup(context, it, toast) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profil & objectifs") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Moi") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !male, onClick = { male = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Femme") }
                    SegmentedButton(selected = male, onClick = { male = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Homme") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(birthYear, { birthYear = it }, "Année de naissance", Modifier.weight(1f), decimal = false)
                    NumberField(height, { height = it }, "Taille", Modifier.weight(1f), "cm", decimal = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(weight, { weight = it }, "Poids actuel", Modifier.weight(1f), "kg")
                    NumberField(target, { target = it }, "Poids visé", Modifier.weight(1f), "kg")
                }
            }

            Section("Objectif") {
                Goal.entries.forEach { g ->
                    RadioLine(g.label, null, goal == g) { goal = g }
                }
                if (goal != Goal.MAINTAIN) {
                    Text("Rythme par semaine", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.25, 0.5, 0.75, 1.0).forEach { r ->
                            FilterChip(selected = rate == r, onClick = { rate = r }, label = { Text("${r.fmt1()} kg") })
                        }
                    }
                }
            }

            Section("Niveau d'activité") {
                Activity.entries.forEach { a ->
                    RadioLine(a.label, a.detail, activity == a) { activity = a }
                }
            }

            Section("Objectif calorique") {
                NutrientRow("Métabolisme de base", "${draft.bmr(w).fmt0()} kcal")
                NutrientRow("Dépense journalière estimée", "${draft.tdee(w).fmt0()} kcal")
                NutrientRow("Objectif recommandé", "${draft.recommendedKcal(w)} kcal", bold = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fixer mon propre objectif", Modifier.weight(1f))
                    Switch(checked = customOn, onCheckedChange = { customOn = it })
                }
                if (customOn) {
                    NumberField(customKcal, { customKcal = it }, "Objectif personnalisé", Modifier.fillMaxWidth(), "kcal", decimal = false)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ajouter les calories des activités au budget du jour", Modifier.weight(1f))
                    Switch(checked = addExercise, onCheckedChange = { addExercise = it })
                }
            }

            Section("Répartition des macronutriments") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    macroPresets.forEach { m ->
                        FilterChip(
                            selected = draft.carbsPct == m.carbs && draft.proteinPct == m.protein && draft.fatPct == m.fat,
                            onClick = { carbs = m.carbs.toString(); protein = m.protein.toString(); fat = m.fat.toString() },
                            label = { Text(m.label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(carbs, { carbs = it }, "Glucides", Modifier.weight(1f), "%", decimal = false)
                    NumberField(protein, { protein = it }, "Protéines", Modifier.weight(1f), "%", decimal = false)
                    NumberField(fat, { fat = it }, "Lipides", Modifier.weight(1f), "%", decimal = false)
                }
                if (pctSum != 100) {
                    Text("Le total doit faire 100 % (actuellement $pctSum %).", color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        "Soit ${targets.carbs.fmt0()} g de glucides, ${targets.protein.fmt0()} g de protéines et ${targets.fat.fmt0()} g de lipides pour ${targets.kcal.fmt0()} kcal.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                NumberField(water, { water = it }, "Objectif d'eau", Modifier.fillMaxWidth(), "ml", decimal = false)
            }

            Button(
                enabled = pctSum == 100,
                onClick = { vm.save(draft, w) { toast("Profil enregistré.") } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Enregistrer") }

            Section("Mes données") {
                Text(
                    "Toutes vos données restent sur ce téléphone. Exportez une sauvegarde pour les conserver ou les transférer.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exporter.launch("mescalories-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                        Text("Exporter")
                    }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) {
                        Text("Importer")
                    }
                }
            }

            Text(
                "Données produits : Open Food Facts (licence ODbL). Valeurs de la base intégrée : moyennes indicatives.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Restaurer la sauvegarde ?") },
            text = { Text("Toutes les données actuelles seront remplacées par celles du fichier.") },
            confirmButton = {
                TextButton(onClick = { confirmImport = null; vm.importBackup(context, uri, toast) }) { Text("Restaurer") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun RadioLine(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
