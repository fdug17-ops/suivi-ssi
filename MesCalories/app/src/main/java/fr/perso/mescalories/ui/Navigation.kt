package fr.perso.mescalories.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.perso.mescalories.ui.diary.DiaryScreen
import fr.perso.mescalories.ui.food.AddFoodScreen
import fr.perso.mescalories.ui.food.EditFoodScreen
import fr.perso.mescalories.ui.food.FoodDetailScreen
import fr.perso.mescalories.ui.foods.FoodsScreen
import fr.perso.mescalories.ui.profile.ProfileScreen
import fr.perso.mescalories.ui.progress.ProgressScreen
import fr.perso.mescalories.ui.recipe.RecipeEditScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("diary", "Journal", Icons.Filled.Restaurant),
    Tab("foods", "Aliments", Icons.Filled.Kitchen),
    Tab("progress", "Progrès", Icons.Filled.BarChart),
    Tab("profile", "Profil", Icons.Filled.Person),
)

/** Routes et helpers de navigation. */
object Routes {
    fun add(day: Long, meal: Int) = "add/$day/$meal"
    fun foodForAdd(foodId: Long, day: Long, meal: Int) = "food/$foodId?day=$day&meal=$meal"
    fun foodForEntry(foodId: Long, entryId: Long) = "food/$foodId?entry=$entryId"
    fun foodView(foodId: Long) = "food/$foodId"
    fun editFood(foodId: Long = 0, barcode: String = "") = "editFood?id=$foodId&barcode=$barcode"
    fun recipe(id: Long = 0) = "recipe?id=$id"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Les écrans ont leur propre barre du haut : on ne réserve que l'espace de la barre du bas.
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        NavHost(nav, startDestination = "diary", modifier = Modifier.padding(bottom).consumeWindowInsets(bottom)) {
            composable("diary") { DiaryScreen(nav) }
            composable("foods") { FoodsScreen(nav) }
            composable("progress") { ProgressScreen() }
            composable("profile") { ProfileScreen() }
            composable(
                "add/{day}/{meal}",
                arguments = listOf(
                    navArgument("day") { type = NavType.LongType },
                    navArgument("meal") { type = NavType.IntType },
                ),
            ) { e ->
                AddFoodScreen(nav, e.arguments!!.getLong("day"), e.arguments!!.getInt("meal"))
            }
            composable(
                "food/{id}?day={day}&meal={meal}&entry={entry}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("day") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                    navArgument("meal") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("entry") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                val a = e.arguments!!
                FoodDetailScreen(
                    nav = nav,
                    foodId = a.getLong("id"),
                    day = a.getLong("day").takeIf { it != Long.MIN_VALUE },
                    meal = a.getInt("meal"),
                    entryId = a.getLong("entry"),
                )
            }
            composable(
                "editFood?id={id}&barcode={barcode}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                EditFoodScreen(nav, e.arguments!!.getLong("id"), e.arguments!!.getString("barcode").orEmpty())
            }
            composable(
                "recipe?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { e ->
                RecipeEditScreen(nav, e.arguments!!.getLong("id"))
            }
        }
    }
}

fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
