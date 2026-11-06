package cn.qingheng.app

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FoodIconsTest {
    private val assets get() = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
    @Test fun bundledPngsMatchPinnedSourceManifestAndKeepTransparency() {
        val manifest = JSONObject(File(assets, "food-icons/catalog.json").readText())
        assertEquals("MIT", manifest.getString("license")); assertFalse(manifest.getBoolean("modified"))
        val sourceCommit = manifest.getString("commit")
        assertTrue(sourceCommit.matches(Regex("[a-f0-9]{40}")))
        val icons = manifest.getJSONArray("icons")
        assertEquals(FoodIcons.all.size, icons.length())
        assertEquals(FoodIcons.all.size, FoodIcons.all.map { it.id }.distinct().size)
        val ids = mutableSetOf<String>()
        repeat(icons.length()) { index ->
            val item = icons.getJSONObject(index); val id = item.getString("id")
            assertTrue(ids.add(id))
            val icon = FoodIcons.find("icon:$id")!!
            assertEquals(item.getString("title"), icon.title); assertEquals(item.getString("category"), icon.category)
            val bytes = File(assets, icon.asset).readBytes()
            assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.copyOfRange(0, 8))
            assertEquals(256, ByteBuffer.wrap(bytes, 16, 4).int); assertEquals(256, ByteBuffer.wrap(bytes, 20, 4).int)
            assertEquals(6, bytes[25].toInt()) // Original RGBA PNG; no opaque replacement or image editing.
            assertEquals(item.getInt("bytes"), bytes.size)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(item.getString("sha256"), digest)
            assertTrue(item.getString("sourceUrl").startsWith("https://raw.githubusercontent.com/microsoft/fluentui-emoji/$sourceCommit/assets/"))
        }
        assertEquals(FoodIcons.all.map { it.id }.toSet(), ids)
        val license = File(assets, "licenses/fluent-emoji-MIT.txt").readText()
        assertTrue(license.contains("MIT License")); assertTrue(license.contains("Copyright (c) Microsoft Corporation."))
        assertTrue(license.contains("this permission notice shall be included"))
    }
    @Test fun everyDefaultCategoryHasItsOwnBundledIconAndCustomCategoriesHaveFallback() {
        val keys = FoodCategories.defaults.map { FoodIcons.categoryKey(it) }
        assertEquals(keys.size, keys.distinct().size)
        keys.forEach { assertNotNull(FoodIcons.find(it)) }
        assertEquals("icon:glass_of_milk", FoodIcons.categoryKey("饮品"))
        assertEquals("icon:cooked_rice", FoodIcons.categoryKey("主食"))
        assertEquals("icon:bento_box", FoodIcons.categoryKey("我的自定义分类"))
    }
    @Test fun filterRespectsCategoryAndFindsChineseAndEnglishNames() {
        assertEquals(listOf("glass_of_milk"), FoodIcons.filter("饮品", " 牛奶 ").map { it.id })
        assertTrue(FoodIcons.filter("主食", "牛奶").isEmpty())
        assertEquals(listOf("cooked_rice"), FoodIcons.filter(null, "cooked rice").map { it.id })
        assertEquals(FoodIcons.all.size, FoodIcons.filter(null, "").size)
        assertTrue(FoodIcons.filter(null, "不存在的食物").isEmpty())
    }
    @Test fun materialSelectionPersistsWithoutChangingFoodOrRecipeNutrition() {
        val original = FoodCatalog.tomatoEgg.copy(id = 42, category = "我的菜", image = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg")
        val changed = original.copy(image = "icon:cooking")
        assertEquals(changed, FoodJson.decode(FoodJson.encode(changed), 42))
        assertEquals(original.values, changed.values); assertEquals(original.ingredients, changed.ingredients)
        assertEquals(original.basisGrams, changed.basisGrams)
        assertEquals(original, FoodJson.decode(FoodJson.encode(original), 42))
    }
    @Test fun imageKeysAreAnExactCatalogAndCannotBeUsedAsAssetPaths() {
        listOf("", "icon:../licenses/fluent-emoji-MIT.txt", "icon:missing", "cooked_rice", "https://example.com/icon.png", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg").forEach { assertNull(FoodIcons.find(it)) }
        FoodCatalog.all.forEach { assertNotNull(FoodIcons.find(FoodIcons.defaultFor(it))) }
        assertEquals("icon:tomato", FoodIcons.defaultFor(FoodCatalog.tomato))
    }
}
