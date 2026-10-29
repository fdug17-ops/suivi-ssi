package fr.perso.mescalories.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import fr.perso.mescalories.data.DiaryEntry
import fr.perso.mescalories.data.Food
import fr.perso.mescalories.data.FoodSource
import fr.perso.mescalories.data.Meal
import fr.perso.mescalories.data.Nutrients
import fr.perso.mescalories.data.Repository
import fr.perso.mescalories.ui.BackButton
import fr.perso.mescalories.ui.NumberField
import fr.perso.mescalories.ui.Routes
import fr.perso.mescalories.ui.appViewModel
import fr.perso.mescalories.ui.fmt0
import fr.perso.mescalories.ui.fmt1
import fr.perso.mescalories.ui.theme.MacroColors
import fr.perso.mescalories.ui.toNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class FoodDetailViewModel(
    private val repo: Repository,
    private val foodId: Long,
    private val entryId: Long,
) : ViewModel() {
    val food = MutableStateFlow<Food?>(null)
    val entry = MutableStateFlow<DiaryEntry?>(null)

    /** L'aliment n'existe plus en base (supprimé) : on le reconstitue depuis l'entrée du journal. */
    var detached = false
        private set

    init {
        viewModelScope.launch {
            val e = if (entryId > 0) repo.diary.get(entryId) else null
            entry.value = e
            food.value = repo.foods.get(foodId) ?: e?.let { en ->
                detached = true
                val f = if (en.grams > 0) 100.0 / en.grams else 0.0
                Food(
                    id = foodId, name = en.name, kcal = en.kcal * f, protein = en.protein * f, carbs = en.carbs * f,
                    sugars = en.sugars * f, fat = en.fat * f, saturatedFat = en.saturatedFat * f,
                    fiber = en.fiber * f, salt = en.salt * f,
                )
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        repo.foods.get(foodId)?.let { food.value = it }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val f = food.value ?: return@launch
        repo.foods.setFavorite(f.id, !f.favorite)
        food.value = f.copy(favorite = !f.favorite)
    }

    fun save(day: Long, meal: Int, grams: Double, label: String, done: () -> Unit) = viewModelScope.launch {
        val f = food.value ?: return@launch
        val e = entry.value
        val newEntry = entryFor(f, e?.day ?: day, meal, grams, label, id = e?.id ?: 0)
        repo.addEntry(if (detached) newEntry.copy(foodId = e?.foodId) else newEntry)
        done()
    }

    fun delete(done: () -> Unit) = viewModelScope.launch {
        entry.value?.let { repo.diary.delete(it) }
        done()
    }
}

@Composable
fun FoodDetailScreen(nav: NavHostController, foodId: Long, day: Long?, meal: Int, entryId: Long) {
    val vm = appViewModel("food-$foodId-$entryId") { FoodDetailViewModel(it, foodId, entryId) }
    val food by vm.food.collectAsStateWithLifecycle()
    val entry by vm.entry.collectAsStateWithLifecycle()

    // Recharger l'aliment au retour de l'écran de modification.
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(food?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton { nav.popBackStack() } },
                actions = {
                    val f = food
                    if (f != null && !vm.detached) {
                        IconButton(onClick = vm::toggleFavorite) {
                            Icon(if (f.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favori")
                        }
                        IconButton(onClick = {
                            nav.navigate(if (f.source == FoodSource.RECIPE) Routes.recipe(f.id) else Routes.editFood(f.id))
                        }) { Icon(Icons.Filled.Edit, "Modifier l'aliment") }
                    }
                    if (entry != null) {
                        IconButton(onClick = { vm.delete { nav.popBackStack() } }) {
                            Icon(Icons.Filled.Delete, "Supprimer du journal")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val f = food
        if (f == null) {
            CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            return@Scaffold
        }
        // On attend l'entrée (en modification) pour initialiser la quantité.
        if (entryId > 0 && entry == null) return@Scaffold
        FoodDetailContent(
            food = f,
            entry = entry,
            initialMeal = entry?.meal ?: meal,
            modifier = Modifier.padding(padding),
            onSave = { m, grams, label ->
                vm.save(day ?: LocalDate.now().toEpochDay(), m, grams, label) { nav.popBackStack() }
            },
        )
    }
}

@Composable
private fun FoodDetailContent(
    food: Food,
    entry: DiaryEntry?,
    initialMeal: Int,
    modifier: Modifier,
    onSave: (meal: Int, grams: Double, label: String) -> Unit,
) {
    val hasServing = food.servingGrams > 0
    val servingName = food.servingName.ifBlank { "portion" }
    // Unité choisie : 0 = grammes, 1 = portion
    var unit by rememberSaveable {
        mutableIntStateOf(
            when {
                !hasServing -> 0
                entry == null -> 1
                else -> if (entry.amountLabel.endsWith(" g")) 0 else 1
            }
        )
    }
    var amount by rememberSaveable {
        mutableStateOf(
            when {
                entry != null && unit == 1 -> (entry.grams / food.servingGrams).fmt1()
                entry != null -> entry.grams.fmt1()
                unit == 1 -> "1"
                else -> "100"
            }
        )
    }
    var meal by rememberSaveable { mutableIntStateOf(initialMeal) }

    val value = amount.toNumber() ?: 0.0
    val grams = if (unit == 1) value * food.servingGrams else value
    val n = food.nutrientsFor(grams)
    val label = if (unit == 1) "${value.fmt1()} $servingName (${grams.fmt0()} g)" else "${grams.fmt1()} g"

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (food.brand.isNotBlank()) Text(food.brand, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(amount, { amount = it }, "Quantité", Modifier.weight(1f))
            Column(Modifier.weight(1f)) {
                FilterChip(selected = unit == 0, onClick = {
                    if (unit == 1) amount = grams.fmt1()
                    unit = 0
                }, label = { Text("grammes") })
                if (hasServing) {
                    FilterChip(selected = unit == 1, onClick = {
                        if (unit == 0) amount = (grams / food.servingGrams).fmt1()
                        unit = 1
                    }, label = { Text("$servingName (${food.servingGrams.fmt1()} g)", maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }

        Text("Repas", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Meal.entries.forEach { m ->
                FilterChip(selected = meal == m.ordinal, onClick = { meal = m.ordinal }, label = { Text("${m.emoji} ${m.label}") })
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("${n.kcal.fmt0()} kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("pour ${grams.fmt1()} g", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                MacroSplit(n)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Valeurs nutritionnelles", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("", Modifier.weight(1f))
                    Text("Quantité", Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium)
                    Text("100 g", Modifier.weight(0.4f), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
                val p = food.per100
                NutrientLine("Énergie", "${n.kcal.fmt0()} kcal", "${p.kcal.fmt0()}", bold = true)
                NutrientLine("Glucides", "${n.carbs.fmt1()} g", p.carbs.fmt1(), bold = true)
                NutrientLine("  dont sucres", "${n.sugars.fmt1()} g", p.sugars.fmt1())
                NutrientLine("Protéines", "${n.protein.fmt1()} g", p.protein.fmt1(), bold = true)
                NutrientLine("Lipides", "${n.fat.fmt1()} g", p.fat.fmt1(), bold = true)
                NutrientLine("  dont saturés", "${n.saturatedFat.fmt1()} g", p.saturatedFat.fmt1())
                NutrientLine("Fibres", "${n.fiber.fmt1()} g", p.fiber.fmt1())
                NutrientLine("Sel", "${n.salt.fmt1()} g", p.salt.fmt1())
            }
        }

        Button(
            onClick = { onSave(meal, grams, label) },
            enabled = grams > 0,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (entry == null) "Ajouter au journal" else "Enregistrer") }
    }
}

@Composable
private fun NutrientLine(label: String, value: String, per100: String, bold: Boolean = false) {
    val w = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = w)
        Text(value, Modifier.weight(0.5f), fontWeight = w)
        Text(per100, Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Répartition des calories entre glucides, protéines et lipides. */
@Composable
fun MacroSplit(n: Nutrients) {
    val c = n.carbs * 4
    val p = n.protein * 4
    val f = n.fat * 9
    val total = (c + p + f).takeIf { it > 0 } ?: 1.0
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp)) {
            if (c > 0) Box(Modifier.weight(c.toFloat()).fillMaxHeight().background(MacroColors.carbs))
            if (p > 0) Box(Modifier.weight(p.toFloat()).fillMaxHeight().background(MacroColors.protein))
            if (f > 0) Box(Modifier.weight(f.toFloat()).fillMaxHeight().background(MacroColors.fat))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MacroLegend("Glucides", n.carbs, c / total, MacroColors.carbs)
            MacroLegend("Protéines", n.protein, p / total, MacroColors.protein)
            MacroLegend("Lipides", n.fat, f / total, MacroColors.fat)
        }
    }
}

@Composable
private fun MacroLegend(label: String, grams: Double, share: Double, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
        Text("${grams.fmt1()} g · ${(share * 100).fmt0()} %", style = MaterialTheme.typography.bodySmall)
    }
}
