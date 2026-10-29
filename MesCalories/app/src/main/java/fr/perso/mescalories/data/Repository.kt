package fr.perso.mescalories.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

@Serializable
data class Backup(
    val version: Int = 1,
    val profile: Profile,
    val foods: List<Food>,
    val diary: List<DiaryEntry>,
    val weights: List<WeightEntry>,
    val water: List<WaterEntry>,
    val exercises: List<ExerciseEntry>,
    val recipeIngredients: List<RecipeIngredient>,
)

data class IngredientLine(val food: Food, val grams: Double)

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    val profileStore: ProfileStore,
) {
    val foods get() = db.foods()
    val diary get() = db.diary()
    val weights get() = db.weights()
    val water get() = db.water()
    val exercises get() = db.exercises()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    /** Charge la base d'aliments intégrée au premier lancement. */
    suspend fun seedIfNeeded() {
        if (foods.builtinCount() > 0) return
        val list = context.assets.open("aliments.csv").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                val c = line.split(';')
                if (c.size < 11) return@mapNotNull null
                fun d(i: Int) = c[i].trim().toDoubleOrNull() ?: 0.0
                withSearch(
                    Food(
                        name = c[0].trim(), kcal = d(1), protein = d(2), carbs = d(3), sugars = d(4),
                        fat = d(5), saturatedFat = d(6), fiber = d(7), salt = d(8),
                        servingName = c[9].trim(), servingGrams = d(10), source = FoodSource.BUILTIN,
                    )
                )
            }.toList()
        }
        foods.insertAll(list)
    }

    suspend fun searchLocal(query: String): List<Food> {
        val words = normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return foods.search(words.first()).filter { f -> words.all { f.search.contains(it) } }.take(60)
    }

    /** Insère (ou met à jour) un aliment et retourne son identifiant. */
    suspend fun saveFood(food: Food): Long {
        val f = withSearch(food)
        return if (f.id == 0L) foods.insert(f) else {
            foods.update(f); f.id
        }
    }

    /** Enregistre localement un produit Open Food Facts (une seule fois par code-barres). */
    suspend fun importOff(food: Food): Long {
        food.barcode?.let { code -> foods.byBarcode(code)?.let { return it.id } }
        return saveFood(food)
    }

    suspend fun addEntry(entry: DiaryEntry) {
        if (entry.id == 0L) diary.insert(entry) else diary.update(entry)
        entry.foodId?.let { foods.markUsed(it, System.currentTimeMillis()) }
    }

    suspend fun copyMeal(fromDay: Long, toDay: Long, meal: Int): Int {
        val src = diary.forMeal(fromDay, meal)
        diary.insertAll(src.map { it.copy(id = 0, day = toDay, createdAt = System.currentTimeMillis()) })
        return src.size
    }

    suspend fun recipeIngredients(recipeId: Long): List<IngredientLine> =
        db.recipes().ingredients(recipeId).mapNotNull { ing ->
            foods.get(ing.foodId)?.let { IngredientLine(it, ing.grams) }
        }

    /**
     * Enregistre une recette : elle devient un aliment (valeurs pour 100 g calculées
     * à partir des ingrédients), dont une portion = poids total / nombre de portions.
     */
    suspend fun saveRecipe(
        id: Long,
        name: String,
        portions: Int,
        cookedWeight: Double?,
        lines: List<IngredientLine>,
    ): Long = db.withTransaction {
        val rawWeight = lines.sumOf { it.grams }
        val weight = cookedWeight?.takeIf { it > 0 } ?: rawWeight
        val total = lines.fold(Nutrients()) { acc, l -> acc + l.food.nutrientsFor(l.grams) }
        val per100 = if (weight > 0) total * (100.0 / weight) else Nutrients()
        val existing = if (id != 0L) foods.get(id) else null
        val recipe = (existing ?: Food(name = name, kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0)).copy(
            name = name,
            kcal = per100.kcal, protein = per100.protein, carbs = per100.carbs, sugars = per100.sugars,
            fat = per100.fat, saturatedFat = per100.saturatedFat, fiber = per100.fiber, salt = per100.salt,
            servingName = "portion",
            servingGrams = if (portions > 0) weight / portions else weight,
            source = FoodSource.RECIPE,
        )
        val recipeId = saveFood(recipe)
        db.recipes().deleteForRecipe(recipeId)
        db.recipes().insertAll(lines.map { RecipeIngredient(recipeId = recipeId, foodId = it.food.id, grams = it.grams) })
        recipeId
    }

    suspend fun deleteFood(id: Long) {
        db.recipes().deleteForRecipe(id)
        foods.delete(id)
    }

    suspend fun exportJson(): String = json.encodeToString(
        Backup.serializer(),
        Backup(
            profile = profileStore.current(),
            foods = foods.all(),
            diary = diary.all(),
            weights = weights.list(),
            water = water.list(),
            exercises = exercises.list(),
            recipeIngredients = db.recipes().list(),
        )
    )

    suspend fun importJson(text: String) {
        val b = json.decodeFromString(Backup.serializer(), text)
        db.withTransaction {
            foods.clear(); diary.clear(); weights.clear(); water.clear(); exercises.clear(); db.recipes().clear()
            foods.insertAll(b.foods.map { withSearch(it) })
            diary.insertAll(b.diary)
            weights.upsertAll(b.weights)
            water.upsertAll(b.water)
            exercises.insertAll(b.exercises)
            db.recipes().insertAll(b.recipeIngredients)
        }
        profileStore.save(b.profile)
    }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace("œ", "oe")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        fun withSearch(f: Food) = f.copy(search = normalize(f.name + " " + f.brand))
    }
}
