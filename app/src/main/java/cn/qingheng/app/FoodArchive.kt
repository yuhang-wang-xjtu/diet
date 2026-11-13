package cn.qingheng.app

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Portable shortcut foods, including recipe snapshots and optional local pictures. */
object FoodArchive {
    data class Document(val foods: List<QuickFood>, val images: Map<String, ByteArray>)
    fun imageKeys(foods: List<QuickFood>): Set<String> = foods.flatMap { food ->
        listOf(food.image) + imageKeys(food.ingredients.map { it.food })
    }.filter { it.isNotBlank() }.toSet()
    fun encode(foods: List<QuickFood>, images: Map<String, ByteArray> = emptyMap()): String = JSONObject().apply {
        put("format", "qingheng-foods"); put("version", 1)
        put("foods", JSONArray().apply { foods.forEach { put(JSONObject(FoodJson.encode(it))) } })
        put("images", JSONObject().apply { images.forEach { (key, bytes) -> put(key, Base64.getEncoder().encodeToString(bytes)) } })
    }.toString(2)
    fun decode(raw: String): Document {
        val root = JSONObject(raw.removePrefix("\uFEFF"))
        require(root.getString("format") == "qingheng-foods" && root.getInt("version") == 1) { "请选择轻衡导出的菜品JSON文件" }
        val foods = root.getJSONArray("foods")
        val images = root.optJSONObject("images") ?: JSONObject()
        return Document((0 until foods.length()).map { FoodJson.decode(foods.getJSONObject(it).toString(), 0) },
            images.keys().asSequence().associateWith { Base64.getDecoder().decode(images.getString(it)) })
    }
    fun restoreImages(food: QuickFood, mapping: Map<String, String>): QuickFood = food.copy(
        image = if (food.image.isBlank() || food.image.startsWith("icon:") || food.image == "design:rice") food.image else mapping[food.image].orEmpty(),
        ingredients = food.ingredients.map { it.copy(food = restoreImages(it.food, mapping)) })
}
