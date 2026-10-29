package fr.perso.mescalories.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE search LIKE '%' || :q || '%' ORDER BY favorite DESC, useCount DESC, length(name) LIMIT 200")
    suspend fun search(q: String): List<Food>

    @Query("SELECT * FROM foods WHERE lastUsed > 0 ORDER BY lastUsed DESC LIMIT 60")
    fun recent(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE favorite = 1 ORDER BY name COLLATE NOCASE")
    fun favorites(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE source IN (1, 2) ORDER BY name COLLATE NOCASE")
    fun myFoods(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE source = 3 ORDER BY name COLLATE NOCASE")
    fun recipes(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun get(id: Long): Food?

    @Query("SELECT * FROM foods WHERE id = :id")
    fun observe(id: Long): Flow<Food?>

    @Query("SELECT * FROM foods WHERE barcode = :code LIMIT 1")
    suspend fun byBarcode(code: String): Food?

    @Query("SELECT * FROM foods")
    suspend fun all(): List<Food>

    @Query("SELECT COUNT(*) FROM foods WHERE source = 0")
    suspend fun builtinCount(): Int

    @Insert
    suspend fun insert(food: Food): Long

    @Insert
    suspend fun insertAll(foods: List<Food>)

    @Update
    suspend fun update(food: Food)

    @Query("UPDATE foods SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE foods SET lastUsed = :time, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Long, time: Long)

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM foods")
    suspend fun clear()
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary WHERE day = :day ORDER BY meal, createdAt")
    fun forDay(day: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary WHERE day = :day AND meal = :meal ORDER BY createdAt")
    suspend fun forMeal(day: Long, meal: Int): List<DiaryEntry>

    @Query(
        "SELECT day, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat " +
            "FROM diary WHERE day BETWEEN :from AND :to GROUP BY day ORDER BY day"
    )
    fun totals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query("SELECT * FROM diary WHERE id = :id")
    suspend fun get(id: Long): DiaryEntry?

    @Query("SELECT * FROM diary")
    suspend fun all(): List<DiaryEntry>

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Insert
    suspend fun insertAll(entries: List<DiaryEntry>)

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("DELETE FROM diary")
    suspend fun clear()
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights ORDER BY day")
    fun all(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weights ORDER BY day")
    suspend fun list(): List<WeightEntry>

    @Upsert
    suspend fun upsert(entry: WeightEntry)

    @Upsert
    suspend fun upsertAll(entries: List<WeightEntry>)

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("DELETE FROM weights")
    suspend fun clear()
}

@Dao
interface WaterDao {
    @Query("SELECT * FROM water WHERE day = :day")
    fun forDay(day: Long): Flow<WaterEntry?>

    @Query("SELECT * FROM water")
    suspend fun list(): List<WaterEntry>

    @Upsert
    suspend fun upsert(entry: WaterEntry)

    @Upsert
    suspend fun upsertAll(entries: List<WaterEntry>)

    @Query("DELETE FROM water")
    suspend fun clear()
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE day = :day ORDER BY id")
    fun forDay(day: Long): Flow<List<ExerciseEntry>>

    @Query("SELECT * FROM exercises")
    suspend fun list(): List<ExerciseEntry>

    @Insert
    suspend fun insert(entry: ExerciseEntry)

    @Insert
    suspend fun insertAll(entries: List<ExerciseEntry>)

    @Delete
    suspend fun delete(entry: ExerciseEntry)

    @Query("DELETE FROM exercises")
    suspend fun clear()
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY id")
    suspend fun ingredients(recipeId: Long): List<RecipeIngredient>

    @Query("SELECT * FROM recipe_ingredients")
    suspend fun list(): List<RecipeIngredient>

    @Insert
    suspend fun insertAll(items: List<RecipeIngredient>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: Long)

    @Query("DELETE FROM recipe_ingredients")
    suspend fun clear()
}

@Database(
    entities = [Food::class, DiaryEntry::class, WeightEntry::class, WaterEntry::class, ExerciseEntry::class, RecipeIngredient::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foods(): FoodDao
    abstract fun diary(): DiaryDao
    abstract fun weights(): WeightDao
    abstract fun water(): WaterDao
    abstract fun exercises(): ExerciseDao
    abstract fun recipes(): RecipeDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mescalories.db").build()
    }
}
