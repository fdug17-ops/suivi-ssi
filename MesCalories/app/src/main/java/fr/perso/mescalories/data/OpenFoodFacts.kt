package fr.perso.mescalories.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Accès à la base collaborative et gratuite Open Food Facts (aucune clé, aucune publicité).
 * Utilisé uniquement pour la recherche en ligne et les codes-barres.
 */
object OpenFoodFacts {
    private const val USER_AGENT = "MesCalories/1.0 (application personnelle Android)"
    private const val FIELDS =
        "code,product_name,product_name_fr,brands,nutriments,serving_size,serving_quantity"

    /** Retourne le produit, ou null s'il est inconnu. Lève IOException en cas de problème réseau. */
    suspend fun byBarcode(code: String): Food? = withContext(Dispatchers.IO) {
        val json = get("https://world.openfoodfacts.org/api/v2/product/${enc(code)}.json?fields=$FIELDS")
        if (json.optInt("status") != 1) return@withContext null
        parse(json.optJSONObject("product") ?: return@withContext null, code)
    }

    suspend fun search(query: String): List<Food> = withContext(Dispatchers.IO) {
        val json = get(
            "https://fr.openfoodfacts.org/cgi/search.pl?search_terms=${enc(query)}" +
                "&search_simple=1&action=process&json=1&page_size=40&fields=$FIELDS"
        )
        val products = json.optJSONArray("products") ?: return@withContext emptyList()
        (0 until products.length()).mapNotNull { i ->
            products.optJSONObject(i)?.let { parse(it, it.optString("code")) }
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == 404) return JSONObject()
            if (code !in 200..299) throw IOException("Erreur serveur $code")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(p: JSONObject, code: String): Food? {
        val name = p.optString("product_name_fr").ifBlank { p.optString("product_name") }.trim()
        if (name.isBlank()) return null
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        fun num(key: String): Double? =
            if (n.has(key)) n.optDouble(key).takeUnless { it.isNaN() } else null

        val kcal = num("energy-kcal_100g") ?: num("energy_100g")?.let { it / 4.184 } ?: return null
        val servingQty = p.optDouble("serving_quantity").takeUnless { it.isNaN() || it <= 0 } ?: 0.0
        return Food(
            name = name,
            brand = p.optString("brands").split(',').first().trim(),
            barcode = code.ifBlank { null },
            kcal = kcal,
            protein = num("proteins_100g") ?: 0.0,
            carbs = num("carbohydrates_100g") ?: 0.0,
            sugars = num("sugars_100g") ?: 0.0,
            fat = num("fat_100g") ?: 0.0,
            saturatedFat = num("saturated-fat_100g") ?: 0.0,
            fiber = num("fiber_100g") ?: 0.0,
            salt = num("salt_100g") ?: 0.0,
            servingName = if (servingQty > 0) "portion" else "",
            servingGrams = servingQty,
            source = FoodSource.OPEN_FOOD_FACTS,
        )
    }
}
