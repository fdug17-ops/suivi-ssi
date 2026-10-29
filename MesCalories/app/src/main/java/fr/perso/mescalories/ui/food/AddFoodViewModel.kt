package fr.perso.mescalories.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.perso.mescalories.data.DiaryEntry
import fr.perso.mescalories.data.Food
import fr.perso.mescalories.data.OpenFoodFacts
import fr.perso.mescalories.data.Repository
import fr.perso.mescalories.ui.fmt1
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Results(val foods: List<Food>) : OnlineState
    data class Error(val message: String) : OnlineState
}

/** Construit une entrée du journal pour une quantité donnée d'un aliment. */
fun entryFor(food: Food, day: Long, meal: Int, grams: Double, amountLabel: String, id: Long = 0): DiaryEntry {
    val n = food.nutrientsFor(grams)
    return DiaryEntry(
        id = id, day = day, meal = meal, foodId = food.id, name = food.displayName, grams = grams,
        amountLabel = amountLabel,
        kcal = n.kcal, protein = n.protein, carbs = n.carbs, sugars = n.sugars, fat = n.fat,
        saturatedFat = n.saturatedFat, fiber = n.fiber, salt = n.salt,
    )
}

/** Quantité proposée par défaut : une portion si elle est connue, sinon 100 g. */
fun defaultAmount(food: Food): Pair<Double, String> =
    if (food.servingGrams > 0) food.servingGrams to "1 ${food.servingName.ifBlank { "portion" }} (${food.servingGrams.fmt1()} g)"
    else 100.0 to "100 g"

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddFoodViewModel(private val repo: Repository, val day: Long, val meal: Int) : ViewModel() {
    val query = MutableStateFlow("")

    val local: StateFlow<List<Food>> = query.debounce(150).mapLatest { repo.searchLocal(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent = repo.foods.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = repo.foods.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val myFoods = repo.foods.myFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recipes = repo.foods.recipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val online = MutableStateFlow<OnlineState>(OnlineState.Idle)

    fun setQuery(q: String) {
        query.value = q
        online.value = OnlineState.Idle
    }

    fun searchOnline() {
        val q = query.value.trim()
        if (q.isEmpty()) return
        online.value = OnlineState.Loading
        viewModelScope.launch {
            online.value = try {
                OnlineState.Results(OpenFoodFacts.search(q))
            } catch (e: Exception) {
                OnlineState.Error("Recherche impossible. Vérifiez votre connexion internet.")
            }
        }
    }

    fun importOnline(food: Food, onId: (Long) -> Unit) = viewModelScope.launch {
        onId(repo.importOff(food))
    }

    /** Résultat d'un scan : aliment déjà connu, trouvé en ligne, inconnu ou erreur réseau. */
    fun lookupBarcode(code: String, onFound: (Long) -> Unit, onUnknown: (String) -> Unit, onError: (String) -> Unit) =
        viewModelScope.launch {
            repo.foods.byBarcode(code)?.let { return@launch onFound(it.id) }
            try {
                val off = OpenFoodFacts.byBarcode(code)
                if (off == null) onUnknown(code) else onFound(repo.importOff(off))
            } catch (e: Exception) {
                onError("Impossible de joindre Open Food Facts. Vérifiez votre connexion internet.")
            }
        }

    fun addDefault(food: Food) = viewModelScope.launch {
        val (grams, label) = defaultAmount(food)
        repo.addEntry(entryFor(food, day, meal, grams, label))
    }

    fun quickAdd(entry: DiaryEntry) = viewModelScope.launch { repo.addEntry(entry) }
}
