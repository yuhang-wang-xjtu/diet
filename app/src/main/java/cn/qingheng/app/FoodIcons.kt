package cn.qingheng.app

data class FoodIcon(val id: String, val title: String, val category: String) {
    val key get() = "icon:$id"
    val asset get() = "food-icons/$id.png"
}

/** Bundled Microsoft Fluent Emoji, distributed unchanged under the MIT license. */
object FoodIcons {
    val all = listOf(
        FoodIcon("pot_of_food", "汤锅", "菜品"), FoodIcon("shallow_pan_of_food", "炖菜", "菜品"),
        FoodIcon("bento_box", "便当", "菜品"), FoodIcon("green_salad", "沙拉", "菜品"),
        FoodIcon("sushi", "寿司", "菜品"), FoodIcon("dumpling", "饺子", "菜品"),
        FoodIcon("curry_rice", "咖喱饭", "菜品"), FoodIcon("cooking", "煎蛋", "菜品"),
        FoodIcon("cooked_rice", "米饭", "主食"), FoodIcon("bread", "面包", "主食"),
        FoodIcon("flatbread", "烙饼", "主食"), FoodIcon("baguette_bread", "法棍", "主食"),
        FoodIcon("steaming_bowl", "面条", "主食"), FoodIcon("spaghetti", "意面", "主食"),
        FoodIcon("rice_ball", "饭团", "主食"), FoodIcon("sandwich", "三明治", "主食"),
        FoodIcon("glass_of_milk", "牛奶", "饮品"), FoodIcon("teacup_without_handle", "茶", "饮品"),
        FoodIcon("hot_beverage", "咖啡", "饮品"), FoodIcon("bubble_tea", "奶茶", "饮品"),
        FoodIcon("beverage_box", "盒装饮料", "饮品"), FoodIcon("cup_with_straw", "饮料", "饮品"),
        FoodIcon("egg", "鸡蛋", "蛋奶肉类"), FoodIcon("poultry_leg", "鸡腿", "蛋奶肉类"),
        FoodIcon("cut_of_meat", "肉类", "蛋奶肉类"), FoodIcon("fish", "鱼", "蛋奶肉类"),
        FoodIcon("shrimp", "虾", "蛋奶肉类"), FoodIcon("cheese_wedge", "奶酪", "蛋奶肉类"),
        FoodIcon("tomato", "番茄", "蔬果"), FoodIcon("carrot", "胡萝卜", "蔬果"),
        FoodIcon("broccoli", "西兰花", "蔬果"), FoodIcon("cucumber", "黄瓜", "蔬果"),
        FoodIcon("leafy_green", "绿叶菜", "蔬果"), FoodIcon("ear_of_corn", "玉米", "蔬果"),
        FoodIcon("potato", "土豆", "蔬果"), FoodIcon("roasted_sweet_potato", "红薯", "蔬果"),
        FoodIcon("red_apple", "苹果", "蔬果"), FoodIcon("banana", "香蕉", "蔬果"),
        FoodIcon("grapes", "葡萄", "蔬果"), FoodIcon("strawberry", "草莓", "蔬果"),
        FoodIcon("watermelon", "西瓜", "蔬果"), FoodIcon("avocado", "牛油果", "蔬果"),
        FoodIcon("onion", "洋葱", "蔬果"), FoodIcon("garlic", "大蒜", "蔬果"),
        FoodIcon("salt", "食盐", "佐料"), FoodIcon("butter", "黄油", "佐料"),
        FoodIcon("honey_pot", "蜂蜜", "佐料"), FoodIcon("jar", "调味罐", "佐料"),
        FoodIcon("cookie", "饼干", "零食"), FoodIcon("chocolate_bar", "巧克力", "零食"),
        FoodIcon("popcorn", "爆米花", "零食"), FoodIcon("shortcake", "蛋糕", "零食"),
        FoodIcon("peanuts", "花生", "零食"), FoodIcon("moon_cake", "月饼", "零食")
    )
    private val byKey = all.associateBy { it.key }
    val categories = all.map { it.category }.distinct()
    fun find(key: String) = byKey[key]
    fun categoryKey(category: String) = "icon:" + when (FoodCategories.clean(category)) {
        "菜品" -> "pot_of_food"; "主食" -> "cooked_rice"; "饮品" -> "glass_of_milk"
        "蛋奶肉类" -> "egg"; "蔬果" -> "broccoli"; "佐料" -> "salt"; "零食" -> "cookie"
        else -> "bento_box"
    }
    fun defaultFor(food: QuickFood): String = when (food.id) {
        -1L -> "icon:cooked_rice"; -2L -> "icon:bread"; -3L -> "icon:egg"
        -4L -> "icon:tomato"; -5L, -7L, -8L -> "icon:jar"; -6L -> "icon:salt"
        -9L -> "icon:glass_of_milk"; -10L -> "icon:banana"; -100L -> "icon:cooking"
        else -> categoryKey(FoodCategories.of(food))
    }
    fun filter(category: String?, query: String): List<FoodIcon> {
        val q = query.trim()
        return all.filter { (category == null || it.category == category) &&
            (q.isBlank() || it.title.contains(q, true) || it.category.contains(q, true) || it.id.replace('_', ' ').contains(q, true)) }
    }
}
