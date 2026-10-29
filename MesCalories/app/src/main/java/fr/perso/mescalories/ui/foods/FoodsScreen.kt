package fr.perso.mescalories.ui.foods

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import fr.perso.mescalories.data.OpenFoodFacts
import fr.perso.mescalories.data.Repository
import fr.perso.mescalories.ui.Routes
import fr.perso.mescalories.ui.appViewModel
import fr.perso.mescalories.ui.food.EmptyHint
import fr.perso.mescalories.ui.food.FoodRow
import fr.perso.mescalories.ui.food.rememberLauncherForScan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoodsViewModel(private val repo: Repository) : ViewModel() {
    val myFoods = repo.foods.myFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recipes = repo.foods.recipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = repo.foods.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val searchResults = MutableStateFlow<List<Food>>(emptyList())

    fun search(q: String) = viewModelScope.launch { searchResults.value = repo.searchLocal(q) }

    fun scan(code: String, onFound: (Long) -> Unit, onUnknown: (String) -> Unit, onError: () -> Unit) =
        viewModelScope.launch {
            repo.foods.byBarcode(code)?.let { return@launch onFound(it.id) }
            try {
                val off = OpenFoodFacts.byBarcode(code)
                if (off == null) onUnknown(code) else onFound(repo.importOff(off))
            } catch (e: Exception) {
                onError()
            }
        }
}

@Composable
fun FoodsScreen(nav: NavHostController) {
    val vm = appViewModel { FoodsViewModel(it) }
    val myFoods by vm.myFoods.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) { vm.search(query) }

    val open: (Food) -> Unit = { f ->
        nav.navigate(if (f.source == FoodSource.RECIPE) Routes.recipe(f.id) else Routes.editFood(f.id))
    }
    val scan = rememberLauncherForScan { code ->
        vm.scan(
            code,
            onFound = { nav.navigate(Routes.editFood(it)) },
            onUnknown = { nav.navigate(Routes.editFood(barcode = it)) },
            onError = { scope.launch { snackbar.showSnackbar("Connexion à Open Food Facts impossible.") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aliments & recettes") },
                actions = { IconButton(onClick = scan) { Icon(Icons.Filled.QrCodeScanner, "Scanner un produit") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(if (tab == 1) Routes.recipe() else Routes.editFood()) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(if (tab == 1) "Recette" else "Aliment") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Rechercher dans tous les aliments…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Effacer") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (query.isNotBlank()) {
                FoodList(results, "Aucun aliment trouvé.", open)
            } else {
                TabRow(selectedTabIndex = tab) {
                    listOf("Mes aliments", "Recettes", "Favoris").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
                when (tab) {
                    0 -> FoodList(myFoods, "Vos aliments personnels et les produits scannés apparaîtront ici.", open)
                    1 -> FoodList(recipes, "Créez une recette pour calculer automatiquement ses valeurs par portion.", open)
                    else -> FoodList(favorites, "Aucun favori pour l'instant.", open)
                }
            }
        }
    }
}

@Composable
private fun FoodList(foods: List<Food>, empty: String, onOpen: (Food) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        if (foods.isEmpty()) item { EmptyHint(empty) }
        items(foods, key = { it.id }) { f ->
            FoodRow(f, onClick = { onOpen(f) }, onAdd = { onOpen(f) }, trailing = Icons.Filled.ChevronRight)
        }
    }
}
