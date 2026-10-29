package fr.perso.mescalories.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import fr.perso.mescalories.data.Food
import fr.perso.mescalories.data.IngredientLine
import fr.perso.mescalories.data.Nutrients
import fr.perso.mescalories.data.Repository
import fr.perso.mescalories.ui.BackButton
import fr.perso.mescalories.ui.NumberField
import fr.perso.mescalories.ui.appViewModel
import fr.perso.mescalories.ui.fmt0
import fr.perso.mescalories.ui.fmt1
import fr.perso.mescalories.ui.food.MacroSplit
import fr.perso.mescalories.ui.toNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class RecipeViewModel(private val repo: Repository, private val id: Long) : ViewModel() {
    val name = MutableStateFlow("")
    val portions = MutableStateFlow("4")
    val cookedWeight = MutableStateFlow("")
    val lines = MutableStateFlow<List<IngredientLine>>(emptyList())
    val loaded = MutableStateFlow(id == 0L)
    val results = MutableStateFlow<List<Food>>(emptyList())

    init {
        if (id != 0L) viewModelScope.launch {
            val recipe = repo.foods.get(id)
            val ingredients = repo.recipeIngredients(id)
            if (recipe != null) {
                name.value = recipe.name
                val raw = ingredients.sumOf { it.grams }
                val weight = if (recipe.servingGrams > 0) recipe.servingGrams else raw
                val p = if (weight > 0 && recipe.servingGrams > 0) {
                    // On retrouve le nombre de portions à partir du poids total.
                    val total = if (recipe.kcal > 0) {
                        ingredients.sumOf { it.food.nutrientsFor(it.grams).kcal } / recipe.kcal * 100
                    } else raw
                    (total / recipe.servingGrams).let { Math.round(it).toInt().coerceAtLeast(1) }.also {
                        if (kotlin.math.abs(total - raw) > 1) cookedWeight.value = total.fmt0()
                    }
                } else 1
                portions.value = p.toString()
            }
            lines.value = ingredients
            loaded.value = true
        }
    }

    fun search(q: String) = viewModelScope.launch {
        results.value = if (q.isBlank()) emptyList() else repo.searchLocal(q).filter { it.id != id }
    }

    fun addLine(food: Food, grams: Double) {
        lines.value = lines.value + IngredientLine(food, grams)
    }

    fun updateLine(index: Int, grams: Double) {
        lines.value = lines.value.mapIndexed { i, l -> if (i == index) l.copy(grams = grams) else l }
    }

    fun removeLine(index: Int) {
        lines.value = lines.value.filterIndexed { i, _ -> i != index }
    }

    fun save(done: () -> Unit) = viewModelScope.launch {
        repo.saveRecipe(
            id = id,
            name = name.value.trim(),
            portions = portions.value.toNumber()?.toInt()?.coerceAtLeast(1) ?: 1,
            cookedWeight = cookedWeight.value.toNumber(),
            lines = lines.value,
        )
        done()
    }

    fun delete(done: () -> Unit) = viewModelScope.launch {
        repo.deleteFood(id)
        done()
    }
}

@Composable
fun RecipeEditScreen(nav: NavHostController, recipeId: Long) {
    val vm = appViewModel("recipe-$recipeId") { RecipeViewModel(it, recipeId) }
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val portions by vm.portions.collectAsStateWithLifecycle()
    val cooked by vm.cookedWeight.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val total = lines.fold(Nutrients()) { acc, l -> acc + l.food.nutrientsFor(l.grams) }
    val nPortions = portions.toNumber()?.takeIf { it >= 1 } ?: 1.0
    val weight = cooked.toNumber()?.takeIf { it > 0 } ?: lines.sumOf { it.grams }
    val perPortion = total * (1.0 / nPortions)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == 0L) "Nouvelle recette" else "Modifier la recette") },
                navigationIcon = { BackButton { nav.popBackStack() } },
                actions = {
                    if (recipeId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Supprimer") }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(name, { vm.name.value = it }, label = { Text("Nom de la recette *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(portions, { vm.portions.value = it }, "Portions", Modifier.weight(1f), decimal = false)
                    NumberField(cooked, { vm.cookedWeight.value = it }, "Poids cuit (facult.)", Modifier.weight(1f), "g")
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Par portion : ${perPortion.kcal.fmt0()} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Total ${total.kcal.fmt0()} kcal · ${weight.fmt0()} g · portion de ${(weight / nPortions).fmt0()} g",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        MacroSplit(perPortion)
                    }
                }
            }
            item { Text("Ingrédients (${lines.size})", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(lines) { i, l ->
                Row(
                    Modifier.fillMaxWidth().clickable { editIndex = i },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l.food.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${l.grams.fmt1()} g · ${l.food.nutrientsFor(l.grams).kcal.fmt0()} kcal", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.removeLine(i) }) { Icon(Icons.Filled.Close, "Retirer") }
                }
                HorizontalDivider()
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text(" Ajouter un ingrédient")
                }
            }
            item {
                Button(
                    enabled = name.isNotBlank() && lines.isNotEmpty(),
                    onClick = { vm.save { nav.popBackStack() } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Enregistrer la recette") }
            }
        }
    }

    if (picking) {
        IngredientPicker(
            vm = vm,
            onDismiss = { picking = false },
            onPick = { food, grams -> vm.addLine(food, grams); picking = false },
        )
    }
    editIndex?.let { i ->
        lines.getOrNull(i)?.let { line ->
            GramsDialog(
                title = line.food.name,
                initial = line.grams,
                onDismiss = { editIndex = null },
                onConfirm = { vm.updateLine(i, it); editIndex = null },
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette recette ?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete { nav.popBackStack() } }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun IngredientPicker(vm: RecipeViewModel, onDismiss: () -> Unit, onPick: (Food, Double) -> Unit) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Food?>(null) }
    val results by vm.results.collectAsStateWithLifecycle()
    LaunchedEffect(query) { vm.search(query) }

    val c = chosen
    if (c != null) {
        GramsDialog(
            title = c.name,
            initial = if (c.servingGrams > 0) c.servingGrams else 100.0,
            onDismiss = { chosen = null },
            onConfirm = { onPick(c, it) },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un ingrédient") },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Rechercher…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.id }) { f ->
                        Column(Modifier.fillMaxWidth().clickable { chosen = f }.padding(vertical = 10.dp)) {
                            Text(f.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${f.kcal.fmt0()} kcal / 100 g", style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun GramsDialog(title: String, initial: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var grams by remember { mutableStateOf(initial.fmt1()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { NumberField(grams, { grams = it }, "Quantité", suffix = "g") },
        confirmButton = {
            val g = grams.toNumber()
            TextButton(enabled = g != null && g > 0, onClick = { onConfirm(g!!) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
