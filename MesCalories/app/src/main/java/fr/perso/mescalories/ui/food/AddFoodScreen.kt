package fr.perso.mescalories.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import fr.perso.mescalories.data.Food
import fr.perso.mescalories.data.FoodSource
import fr.perso.mescalories.data.Meal
import fr.perso.mescalories.ui.BackButton
import fr.perso.mescalories.ui.Routes
import fr.perso.mescalories.ui.appViewModel
import fr.perso.mescalories.ui.diary.QuickAddDialog
import fr.perso.mescalories.ui.fmt0
import fr.perso.mescalories.ui.fmt1
import kotlinx.coroutines.launch

@Composable
fun AddFoodScreen(nav: NavHostController, day: Long, meal: Int) {
    val vm = appViewModel("add-$day-$meal") { AddFoodViewModel(it, day, meal) }
    val query by vm.query.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val myFoods by vm.myFoods.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var quickAdd by remember { mutableStateOf(false) }
    var unknownCode by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val openFood: (Long) -> Unit = { id -> nav.navigate(Routes.foodForAdd(id, day, meal)) }
    val addDefault: (Food) -> Unit = { f ->
        vm.addDefault(f)
        scope.launch { snackbar.showSnackbar("${f.name} ajouté") }
    }

    val scanner = rememberLauncherForScan { code ->
        vm.lookupBarcode(
            code,
            onFound = openFood,
            onUnknown = { unknownCode = it },
            onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Meal.of(meal).label) },
                navigationIcon = { BackButton { nav.popBackStack() } },
                actions = {
                    IconButton(onClick = scanner) { Icon(Icons.Filled.QrCodeScanner, "Scanner un code-barres") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Rechercher un aliment…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Effacer") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (query.isBlank()) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { quickAdd = true }, label = { Text("Ajout rapide") }, leadingIcon = { Icon(Icons.Filled.Bolt, null) })
                    AssistChip(onClick = { nav.navigate(Routes.editFood()) }, label = { Text("Créer un aliment") }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                    AssistChip(onClick = scanner, label = { Text("Scanner") }, leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) })
                }
                val titles = listOf("Récents", "Favoris", "Mes aliments", "Recettes")
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
                val (list, empty) = when (tab) {
                    0 -> recent to "Les aliments que vous ajoutez apparaîtront ici."
                    1 -> favorites to "Touchez l'étoile d'un aliment pour l'ajouter à vos favoris."
                    2 -> myFoods to "Créez vos propres aliments ou scannez des produits."
                    else -> recipes to "Créez des recettes depuis l'onglet « Aliments »."
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (list.isEmpty()) item { EmptyHint(empty) }
                    foodItems(list, openFood, addDefault)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (local.isEmpty()) item { EmptyHint("Aucun résultat dans vos aliments.") }
                    foodItems(local, openFood, addDefault)
                    item {
                        when (val o = online) {
                            OnlineState.Idle -> OutlinedButton(
                                onClick = vm::searchOnline,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            ) {
                                Icon(Icons.Filled.Cloud, null)
                                Text("  Rechercher « $query » sur Open Food Facts")
                            }
                            OnlineState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            is OnlineState.Error -> EmptyHint(o.message)
                            is OnlineState.Results -> SectionTitle("Open Food Facts (${o.foods.size})")
                        }
                    }
                    (online as? OnlineState.Results)?.let { r ->
                        if (r.foods.isEmpty()) item { EmptyHint("Aucun produit trouvé en ligne.") }
                        items(r.foods) { f ->
                            FoodRow(
                                food = f,
                                onClick = { vm.importOnline(f, openFood) },
                                onAdd = { vm.importOnline(f) { id -> nav.navigate(Routes.foodForAdd(id, day, meal)) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (quickAdd) {
        QuickAddDialog(
            initial = null, day = day, meal = meal,
            onDismiss = { quickAdd = false },
            onSave = { vm.quickAdd(it); quickAdd = false; nav.popBackStack() },
        )
    }
    unknownCode?.let { code ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { unknownCode = null },
            title = { Text("Produit inconnu") },
            text = { Text("Le code-barres $code n'est pas dans Open Food Facts. Voulez-vous créer cet aliment ?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    unknownCode = null
                    nav.navigate(Routes.editFood(barcode = code))
                }) { Text("Créer") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { unknownCode = null }) { Text("Annuler") } },
        )
    }
}

/** Lance le scanner de codes-barres ; retourne l'action à déclencher. */
@Composable
fun rememberLauncherForScan(onCode: (String) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onCode)
    }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
                .setPrompt("Visez le code-barres du produit")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }
}

fun LazyListScope.foodItems(foods: List<Food>, onOpen: (Long) -> Unit, onAdd: (Food) -> Unit) {
    items(foods, key = { it.id }) { f -> FoodRow(f, onClick = { onOpen(f.id) }, onAdd = { onAdd(f) }) }
}

@Composable
fun FoodRow(food: Food, onClick: () -> Unit, onAdd: (() -> Unit)?, trailing: ImageVector = Icons.Filled.AddCircleOutline) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (food.favorite) Icon(Icons.Filled.Star, null, Modifier.padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(food.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val portion = if (food.servingGrams > 0) " · ${food.servingName.ifBlank { "portion" }} ${food.servingGrams.fmt1()} g" else ""
                val kind = when (food.source) {
                    FoodSource.RECIPE -> "Recette · "
                    else -> if (food.brand.isNotBlank()) "${food.brand} · " else ""
                }
                Text(
                    "$kind${food.kcal.fmt0()} kcal / 100 g$portion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onAdd != null) {
                IconButton(onClick = onAdd) { Icon(trailing, "Ajouter", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        HorizontalDivider(Modifier.padding(start = 16.dp))
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
