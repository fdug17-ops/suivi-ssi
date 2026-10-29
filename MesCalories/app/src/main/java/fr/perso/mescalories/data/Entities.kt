package fr.perso.mescalories.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Valeurs nutritionnelles pour une quantité donnée. */
data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val sugars: Double = 0.0,
    val fat: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val fiber: Double = 0.0,
    val salt: Double = 0.0,
) {
    operator fun plus(o: Nutrients) = Nutrients(
        kcal + o.kcal, protein + o.protein, carbs + o.carbs, sugars + o.sugars,
        fat + o.fat, saturatedFat + o.saturatedFat, fiber + o.fiber, salt + o.salt,
    )

    operator fun times(f: Double) = Nutrients(
        kcal * f, protein * f, carbs * f, sugars * f,
        fat * f, saturatedFat * f, fiber * f, salt * f,
    )
}

object FoodSource {
    const val BUILTIN = 0
    const val CUSTOM = 1
    const val OPEN_FOOD_FACTS = 2
    const val RECIPE = 3
}

/** Un aliment. Toutes les valeurs nutritionnelles sont exprimées pour 100 g (ou 100 ml). */
@Serializable
@Entity(tableName = "foods", indices = [Index("barcode"), Index("search")])
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val sugars: Double = 0.0,
    val fat: Double,
    val saturatedFat: Double = 0.0,
    val fiber: Double = 0.0,
    val salt: Double = 0.0,
    val servingName: String = "",
    val servingGrams: Double = 0.0,
    val source: Int = FoodSource.CUSTOM,
    val favorite: Boolean = false,
    val lastUsed: Long = 0,
    val useCount: Int = 0,
    val search: String = "",
) {
    val per100: Nutrients
        get() = Nutrients(kcal, protein, carbs, sugars, fat, saturatedFat, fiber, salt)

    fun nutrientsFor(grams: Double): Nutrients = per100 * (grams / 100.0)

    val displayName: String
        get() = if (brand.isBlank()) name else "$name – $brand"
}

@Serializable
@Entity(tableName = "diary", indices = [Index("day")])
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Jour au format LocalDate.toEpochDay(). */
    val day: Long,
    val meal: Int,
    /** null pour un « ajout rapide » (calories saisies directement). */
    val foodId: Long?,
    val name: String,
    val grams: Double,
    /** Libellé de la quantité affichée, ex. « 2 tranches ». */
    val amountLabel: String = "",
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val sugars: Double = 0.0,
    val fat: Double,
    val saturatedFat: Double = 0.0,
    val fiber: Double = 0.0,
    val salt: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val nutrients: Nutrients
        get() = Nutrients(kcal, protein, carbs, sugars, fat, saturatedFat, fiber, salt)

    val isQuickAdd: Boolean get() = foodId == null
}

@Serializable
@Entity(tableName = "weights")
data class WeightEntry(
    @PrimaryKey val day: Long,
    val kg: Double,
)

@Serializable
@Entity(tableName = "water")
data class WaterEntry(
    @PrimaryKey val day: Long,
    val ml: Int,
)

@Serializable
@Entity(tableName = "exercises", indices = [Index("day")])
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val name: String,
    val minutes: Int,
    val kcal: Double,
)

@Serializable
@Entity(tableName = "recipe_ingredients", indices = [Index("recipeId")])
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val foodId: Long,
    val grams: Double,
)

/** Totaux d'une journée (requête agrégée). */
data class DayTotal(
    val day: Long,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)
