package fr.perso.mescalories.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Goal(val label: String) {
    LOSE("Perdre du poids"),
    MAINTAIN("Maintenir mon poids"),
    GAIN("Prendre du poids"),
}

enum class Activity(val label: String, val detail: String, val factor: Double) {
    SEDENTARY("Sédentaire", "Travail de bureau, peu de sport", 1.2),
    LIGHT("Légèrement actif", "Sport 1 à 3 fois par semaine", 1.375),
    MODERATE("Actif", "Sport 3 à 5 fois par semaine", 1.55),
    HIGH("Très actif", "Sport 6 à 7 fois par semaine", 1.725),
    EXTREME("Extrêmement actif", "Travail physique + sport intense", 1.9),
}

enum class Meal(val label: String, val emoji: String, val share: Double) {
    BREAKFAST("Petit-déjeuner", "🥐", 0.25),
    LUNCH("Déjeuner", "🍽️", 0.35),
    DINNER("Dîner", "🍲", 0.30),
    SNACK("Collations", "🍎", 0.10);

    companion object {
        fun of(index: Int) = entries.getOrElse(index) { SNACK }
    }
}

@Serializable
data class Profile(
    val configured: Boolean = false,
    val male: Boolean = false,
    val birthYear: Int = 1985,
    val heightCm: Int = 170,
    val startWeightKg: Double = 70.0,
    val targetWeightKg: Double = 65.0,
    val goal: Goal = Goal.LOSE,
    /** Rythme visé en kg par semaine (perte ou prise). */
    val weeklyRateKg: Double = 0.5,
    val activity: Activity = Activity.LIGHT,
    /** 0 = objectif calculé automatiquement. */
    val customKcal: Int = 0,
    val carbsPct: Int = 50,
    val proteinPct: Int = 20,
    val fatPct: Int = 30,
    val waterGoalMl: Int = 2000,
    /** Ajouter les calories des activités au budget du jour. */
    val addExerciseKcal: Boolean = true,
) {
    val age: Int get() = (LocalDate.now().year - birthYear).coerceIn(10, 110)

    /** Métabolisme de base, formule de Mifflin-St Jeor. */
    fun bmr(weightKg: Double): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (male) 5 else -161

    fun tdee(weightKg: Double): Double = bmr(weightKg) * activity.factor

    fun recommendedKcal(weightKg: Double): Int {
        // 1 kg de masse grasse ≈ 7 700 kcal
        val delta = weeklyRateKg * 7700 / 7
        val raw = when (goal) {
            Goal.LOSE -> tdee(weightKg) - delta
            Goal.MAINTAIN -> tdee(weightKg)
            Goal.GAIN -> tdee(weightKg) + delta
        }
        val floor = if (male) 1500.0 else 1200.0
        return (raw.coerceAtLeast(floor) / 10).roundToInt() * 10
    }

    fun kcalGoal(weightKg: Double): Int = if (customKcal > 0) customKcal else recommendedKcal(weightKg)

    fun targets(weightKg: Double): Targets {
        val kcal = kcalGoal(weightKg).toDouble()
        return Targets(
            kcal = kcal,
            carbs = kcal * carbsPct / 100 / 4,
            protein = kcal * proteinPct / 100 / 4,
            fat = kcal * fatPct / 100 / 9,
            waterMl = waterGoalMl,
        )
    }
}

data class Targets(
    val kcal: Double,
    val carbs: Double,
    val protein: Double,
    val fat: Double,
    val waterMl: Int,
)

class ProfileStore(private val context: Context) {
    private val key = stringPreferencesKey("profile")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val profile: Flow<Profile> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() } ?: Profile()
    }

    suspend fun current(): Profile = profile.first()

    suspend fun save(profile: Profile) {
        context.dataStore.edit { it[key] = json.encodeToString(Profile.serializer(), profile) }
    }
}

private val Context.dataStore by preferencesDataStore("settings")
