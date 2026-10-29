package fr.perso.mescalories.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.perso.mescalories.data.DiaryEntry
import fr.perso.mescalories.data.ExerciseEntry
import fr.perso.mescalories.data.Profile
import fr.perso.mescalories.data.Repository
import fr.perso.mescalories.data.WaterEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DiaryState(
    val day: LocalDate = LocalDate.now(),
    val profile: Profile = Profile(),
    val weightKg: Double = 70.0,
    val entries: List<DiaryEntry> = emptyList(),
    val exercises: List<ExerciseEntry> = emptyList(),
    val waterMl: Int = 0,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(private val repo: Repository) : ViewModel() {
    private val day = MutableStateFlow(LocalDate.now())

    /** Poids le plus récent (journal de poids), à défaut celui du profil. */
    private val weight = combine(repo.weights.all(), repo.profileStore.profile) { list, p ->
        list.lastOrNull()?.kg ?: p.startWeightKg
    }

    private val dayData = day.flatMapLatest { d ->
        val e = d.toEpochDay()
        combine(repo.diary.forDay(e), repo.exercises.forDay(e), repo.water.forDay(e)) { entries, ex, water ->
            Triple(entries, ex, water?.ml ?: 0)
        }.map { d to it }
    }

    val state: StateFlow<DiaryState> = combine(dayData, repo.profileStore.profile, weight) { (d, data), p, w ->
        DiaryState(d, p, w, data.first, data.second, data.third, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryState())

    fun shiftDay(delta: Long) {
        day.value = day.value.plusDays(delta)
    }

    fun goToday() {
        day.value = LocalDate.now()
    }

    fun setWater(ml: Int) = viewModelScope.launch {
        repo.water.upsert(WaterEntry(day.value.toEpochDay(), ml.coerceAtLeast(0)))
    }

    fun addExercise(name: String, minutes: Int, kcal: Double) = viewModelScope.launch {
        repo.exercises.insert(ExerciseEntry(day = day.value.toEpochDay(), name = name, minutes = minutes, kcal = kcal))
    }

    fun deleteExercise(e: ExerciseEntry) = viewModelScope.launch { repo.exercises.delete(e) }

    fun deleteEntry(e: DiaryEntry) = viewModelScope.launch { repo.diary.delete(e) }

    fun saveQuickAdd(entry: DiaryEntry) = viewModelScope.launch { repo.addEntry(entry) }

    fun copyFromYesterday(meal: Int, onDone: (Int) -> Unit) = viewModelScope.launch {
        val d = day.value.toEpochDay()
        onDone(repo.copyMeal(d - 1, d, meal))
    }
}
