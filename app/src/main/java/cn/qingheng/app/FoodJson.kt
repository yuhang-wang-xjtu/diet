package cn.qingheng.app

import org.json.JSONArray
import org.json.JSONObject

object FoodJson {
    fun encode(food: QuickFood): String = json(food).toString()
    private fun json(food: QuickFood): JSONObject = JSONObject().apply {
        put("name", food.name); put("basis", food.basisGrams ?: JSONObject.NULL)
        put("unit", food.unitName); put("unitGrams", food.unitGrams ?: JSONObject.NULL)
        put("kcal", food.values.kcal); put("protein", food.values.protein); put("carbs", food.values.carbs); put("fat", food.values.fat)
        put("sodium", food.values.sodium ?: JSONObject.NULL)
        put("source", food.source); put("url", food.sourceUrl); put("note", food.note)
        put("ingredients", JSONArray().apply { food.ingredients.forEach {
            put(JSONObject().apply { put("food", json(it.food.copy(ingredients = emptyList()))); put("amount", it.amount); put("unit", it.unit.name) })
        } })
    }
    fun decode(raw: String, id: Long = 0): QuickFood = fromJson(JSONObject(raw), id)
    private fun fromJson(j: JSONObject, id: Long = 0): QuickFood {
        val array = j.optJSONArray("ingredients") ?: JSONArray()
        return QuickFood(id, j.getString("name"), if (j.isNull("basis")) null else j.getDouble("basis"),
            FoodValues(j.getDouble("kcal"), j.getDouble("protein"), j.getDouble("carbs"), j.getDouble("fat"), if (j.isNull("sodium")) null else j.getDouble("sodium")),
            j.getString("unit"), if (j.isNull("unitGrams")) null else j.getDouble("unitGrams"), j.getString("source"), j.optString("url"), j.optString("note"),
            (0 until array.length()).map { i -> val item = array.getJSONObject(i); FoodIngredient(fromJson(item.getJSONObject("food")), item.getDouble("amount"), FoodUnit.valueOf(item.getString("unit"))) })
    }
}
