package cn.qingheng.app

/** Small offline reference set. Sources and conversion choices are documented in docs/food-sources.md. */
object FoodCatalog {
    private fun cn(id: Long, name: String, page: Int, kj: Double, p: Double, c: Double, f: Double, sodium: Double,
                   unit: String, unitGrams: Double, note: String) = QuickFood(id, name, 100.0, FoodValues(kj / 4.184, p, c, f, sodium), unit, unitGrams,
        "中国疾控中心营养与健康所·食物营养成分查询平台", "https://nlc.chinanutri.cn/fq/foodinfo/$page.html", "$note；能量按来源 kJ ÷ 4.184 换算 kcal。")
    val rice = cn(-1, "米饭（蒸熟）", 287, 493.0, 2.6, 25.9, 0.3, 2.5, "碗（估重）", 150.0, "按熟饭可食重；1碗暂按150g，碗大小不同请改用克数")
    val bun = cn(-2, "馒头（标准粉，熟）", 273, 1003.0, 7.8, 49.8, 1.0, 165.2, "个（估重）", 100.0, "按成品可食重；1个暂按100g，大小不同请改用克数")
    val egg = cn(-3, "鸡蛋（生，去壳）", 978, 599.0, 13.3, 2.8, 8.8, 131.5, "个（估重）", 50.0, "按去壳可食重；1个暂按50g，适合作为烹调原料，不含油")
    val tomato = QuickFood(-4, "番茄（生，西红柿）", 100.0, FoodValues(18.0, 0.88, 3.89, 0.2, 5.0), "个（估重）", 150.0,
        "USDA FoodData Central · SR Legacy 170457", "https://fdc.nal.usda.gov/food-search/", "按可食生重；1个暂按150g，大小不同请改用克数。在来源官网可搜索FDC编号。")
    val oil = QuickFood(-5, "花生油", 100.0, FoodValues(884.0, 0.0, 0.0, 100.0, 0.0), "勺（估重）", 10.0,
        "USDA FoodData Central · SR Legacy 171410", "https://fdc.nal.usda.gov/food-search/", "1勺暂按10g；请记录实际进入成品的油，不自动扣除锅中残油。在来源官网可搜索FDC编号。")
    val salt = QuickFood(-6, "食盐", 100.0, FoodValues(0.0, 0.0, 0.0, 0.0, 38758.0), "小勺（估重）", 2.0,
        "USDA FoodData Central · SR Legacy 173468", "https://fdc.nal.usda.gov/food-search/", "普通食盐参考，不适用于低钠盐；1小勺暂按2g。盐不提供热量，但计入钠。在来源官网可搜索FDC编号。")
    val sugar = QuickFood(-7, "白砂糖（标签参考）", 100.0, FoodValues(1698.0 / 4.184, 0.0, 99.9, 0.0, 0.0), "勺（估重）", 5.0,
        "中国疾控营养所·东都白砂糖营养标签", "https://nlc.chinanutri.cn/food/Vpoku7SVQCCGNTbnuKtWMw.html", "特定产品的每100g标签参考；1勺暂按5g。品牌不同可建立自定义条目。")
    val soySauce = cn(-8, "酱油（均值）", 1518, 269.0, 5.6, 10.1, 0.1, 5757.0, "勺（估重）", 10.0, "按克称重；1勺暂按10g，不直接将毫升等同于克；品牌不同请以包装为准")
    val milk = cn(-9, "牛奶（均值）", 916, 227.0, 3.0, 3.4, 3.2, 37.2, "杯（估重）", 250.0, "按克称重；1杯暂按250g，不是严格的毫升换算；以实际包装标签优先")
    val banana = QuickFood(-10, "香蕉（生，去皮）", 100.0, FoodValues(93.0, 1.4, 22.0, 0.2, 0.8), "根（估重）", 100.0,
        "广西壮族自治区疾控中心·健康吃水果系列之香蕉", "https://www.gxcdc.org.cn/html/2018/jkzs_0709/9259.html", "按去皮可食重；1根暂按100g。")
    val tomatoEgg by lazy { FoodMath.recipe("番茄炒蛋（示例配方）", listOf(
        FoodIngredient(egg, 2.0, FoodUnit.PORTIONS), FoodIngredient(tomato, 1.0, FoodUnit.PORTIONS),
        FoodIngredient(oil, 10.0, FoodUnit.GRAMS), FoodIngredient(salt, 2.0, FoodUnit.GRAMS)), null, -100) }
    val all get() = listOf(tomatoEgg, rice, bun, egg, tomato, milk, banana, oil, salt, sugar, soySauce)
    val aliases get() = mapOf("鸡蛋" to egg, "蛋" to egg, "番茄" to tomato, "西红柿" to tomato, "米饭" to rice, "馒头" to bun,
        "花生油" to oil, "食盐" to salt, "盐" to salt, "白砂糖" to sugar, "白糖" to sugar, "糖" to sugar, "酱油" to soySauce, "牛奶" to milk, "香蕉" to banana)
}
