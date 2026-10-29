package fr.perso.mescalories.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import fr.perso.mescalories.data.Food
import fr.perso.mescalories.data.FoodSource
import fr.perso.mescalories.data.Repository
import fr.perso.mescalories.ui.BackButton
import fr.perso.mescalories.ui.NumberField
import fr.perso.mescalories.ui.appViewModel
import fr.perso.mescalories.ui.fmt1
import fr.perso.mescalories.ui.toNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class EditFoodViewModel(private val repo: Repository, private val id: Long) : ViewModel() {
    /** null tant que le chargement n'est pas terminé. */
    val food = MutableStateFlow<Food?>(null)
    val loaded = MutableStateFlow(id == 0L)

    init {
        if (id != 0L) viewModelScope.launch {
            food.value = repo.foods.get(id)
            loaded.value = true
        }
    }

    fun save(food: Food, done: () -> Unit) = viewModelScope.launch {
        repo.saveFood(food)
        done()
    }

    fun delete(done: () -> Unit) = viewModelScope.launch {
        repo.deleteFood(id)
        done()
    }
}

@Composable
fun EditFoodScreen(nav: NavHostController, foodId: Long, barcode: String) {
    val vm = appViewModel("edit-$foodId") { EditFoodViewModel(it, foodId) }
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val food by vm.food.collectAsStateWithLifecycle()
    if (!loaded) return
    val deleteAction: (() -> Unit)? = if (food == null) null else fun() { vm.delete { nav.popBackStack() } }
    EditFoodForm(
        initial = food,
        barcode = barcode,
        onBack = { nav.popBackStack() },
        onSave = { vm.save(it) { nav.popBackStack() } },
        onDelete = deleteAction,
    )
}

@Composable
private fun EditFoodForm(
    initial: Food?,
    barcode: String,
    onBack: () -> Unit,
    onSave: (Food) -> Unit,
    onDelete: (() -> Unit)?,
) {
    fun Double?.str() = this?.takeIf { it != 0.0 }?.fmt1() ?: ""
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var brand by rememberSaveable { mutableStateOf(initial?.brand ?: "") }
    var code by rememberSaveable { mutableStateOf(initial?.barcode ?: barcode) }
    var kcal by rememberSaveable { mutableStateOf(initial?.kcal.str()) }
    var carbs by rememberSaveable { mutableStateOf(initial?.carbs.str()) }
    var sugars by rememberSaveable { mutableStateOf(initial?.sugars.str()) }
    var protein by rememberSaveable { mutableStateOf(initial?.protein.str()) }
    var fat by rememberSaveable { mutableStateOf(initial?.fat.str()) }
    var satFat by rememberSaveable { mutableStateOf(initial?.saturatedFat.str()) }
    var fiber by rememberSaveable { mutableStateOf(initial?.fiber.str()) }
    var salt by rememberSaveable { mutableStateOf(initial?.salt.str()) }
    var servingName by rememberSaveable { mutableStateOf(initial?.servingName ?: "") }
    var servingGrams by rememberSaveable { mutableStateOf(initial?.servingGrams.str()) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Calories calculées à partir des macros si non saisies.
    val computedKcal = (carbs.toNumber() ?: 0.0) * 4 + (protein.toNumber() ?: 0.0) * 4 + (fat.toNumber() ?: 0.0) * 9
    val valid = name.isNotBlank() && (kcal.toNumber() != null || computedKcal > 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Nouvel aliment" else "Modifier l'aliment") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (onDelete != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Supprimer") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Nom *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it }, label = { Text("Marque") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumberField(code, { code = it }, "Code-barres", Modifier.fillMaxWidth(), decimal = false)

            Text("Valeurs pour 100 g", style = MaterialTheme.typography.titleMedium)
            NumberField(
                kcal, { kcal = it }, "Énergie", Modifier.fillMaxWidth(), "kcal",
            )
            if (kcal.isBlank() && computedKcal > 0) {
                Text("Calculé à partir des macros : ${computedKcal.fmt1()} kcal", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(carbs, { carbs = it }, "Glucides", Modifier.weight(1f), "g")
                NumberField(sugars, { sugars = it }, "dont sucres", Modifier.weight(1f), "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(fat, { fat = it }, "Lipides", Modifier.weight(1f), "g")
                NumberField(satFat, { satFat = it }, "dont saturés", Modifier.weight(1f), "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(protein, { protein = it }, "Protéines", Modifier.weight(1f), "g")
                NumberField(fiber, { fiber = it }, "Fibres", Modifier.weight(1f), "g")
            }
            NumberField(salt, { salt = it }, "Sel", Modifier.fillMaxWidth(), "g")

            Text("Portion (facultatif)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    servingName, { servingName = it },
                    label = { Text("Nom (ex. tranche)") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                NumberField(servingGrams, { servingGrams = it }, "Poids", Modifier.weight(1f), "g")
            }

            Button(
                enabled = valid,
                onClick = {
                    val base = initial ?: Food(name = "", kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0, source = FoodSource.CUSTOM)
                    onSave(
                        base.copy(
                            name = name.trim(),
                            brand = brand.trim(),
                            barcode = code.trim().ifBlank { null },
                            kcal = kcal.toNumber() ?: computedKcal,
                            carbs = carbs.toNumber() ?: 0.0,
                            sugars = sugars.toNumber() ?: 0.0,
                            protein = protein.toNumber() ?: 0.0,
                            fat = fat.toNumber() ?: 0.0,
                            saturatedFat = satFat.toNumber() ?: 0.0,
                            fiber = fiber.toNumber() ?: 0.0,
                            salt = salt.toNumber() ?: 0.0,
                            servingName = servingName.trim(),
                            servingGrams = servingGrams.toNumber() ?: 0.0,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Enregistrer") }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cet aliment ?") },
            text = { Text("Les entrées déjà présentes dans votre journal sont conservées.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}
