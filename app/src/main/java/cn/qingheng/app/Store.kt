package cn.qingheng.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Store(context: Context) : SQLiteOpenHelper(context, "qingheng.db", null, 7) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, kcal REAL NOT NULL, minutes INTEGER NOT NULL, protein REAL, carbs REAL, fat REAL, meal TEXT NOT NULL DEFAULT 'unspecified', sodium REAL, details TEXT NOT NULL DEFAULT '', exercise_rate REAL, exercise_rate_kind TEXT NOT NULL DEFAULT 'ACTIVE', overlap_steps INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX entries_date ON entries(date)")
        db.execSQL("CREATE TABLE weights(date TEXT PRIMARY KEY, kg REAL NOT NULL)")
        createMealTable(db)
        createQuickFoods(db)
        createExerciseTables(db)
        createProfiles(db)
        createPhoneSteps(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 7) createPhoneSteps(db)
        if (old < 6) createProfiles(db)
        if (old < 2) listOf("protein", "carbs", "fat").forEach { db.execSQL("ALTER TABLE entries ADD COLUMN $it REAL") }
        if (old < 3) {
            db.execSQL("ALTER TABLE entries ADD COLUMN meal TEXT NOT NULL DEFAULT 'unspecified'")
            createMealTable(db)
        }
        if (old < 4) {
            db.execSQL("ALTER TABLE entries ADD COLUMN sodium REAL")
            db.execSQL("ALTER TABLE entries ADD COLUMN details TEXT NOT NULL DEFAULT ''")
            createQuickFoods(db)
        }
        if (old < 5) {
            db.execSQL("ALTER TABLE entries ADD COLUMN exercise_rate REAL")
            db.execSQL("ALTER TABLE entries ADD COLUMN exercise_rate_kind TEXT NOT NULL DEFAULT 'ACTIVE'")
            db.execSQL("ALTER TABLE entries ADD COLUMN overlap_steps INTEGER NOT NULL DEFAULT 0")
            createExerciseTables(db)
        }
    }
    private fun createPhoneSteps(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE phone_step_days(date TEXT PRIMARY KEY, steps INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE phone_step_counter(id INTEGER PRIMARY KEY CHECK(id=1), raw INTEGER NOT NULL, date TEXT NOT NULL, boot INTEGER NOT NULL)")
    }
    fun phoneStepDay(date: String): StepDay? = readableDatabase.rawQuery("SELECT date,steps,updated_at FROM phone_step_days WHERE date=?", arrayOf(date)).use { c ->
        if (!c.moveToFirst()) null else StepDay(c.getString(0), c.getLong(1), "phone_sensor", c.getLong(2))
    }
    fun phoneStepDays(): List<StepDay> {
        val result = mutableListOf<StepDay>()
        readableDatabase.rawQuery("SELECT date,steps,updated_at FROM phone_step_days ORDER BY date", null).use { c ->
            while (c.moveToNext()) result.add(StepDay(c.getString(0), c.getLong(1), "phone_sensor", c.getLong(2)))
        }
        return result
    }
    fun resetPhoneCounter() { writableDatabase.delete("phone_step_counter", null, null) }
    fun deletePhoneDay(date: String) { writableDatabase.delete("phone_step_days", "date=?", arrayOf(date)) }
    fun recordPhoneCounter(current: PhoneCounter, updatedAt: Long): Long {
        val db = writableDatabase; db.beginTransaction()
        try {
            val previous = db.rawQuery("SELECT raw,date,boot FROM phone_step_counter WHERE id=1", null).use { c ->
                if (!c.moveToFirst()) null else PhoneCounter(c.getLong(0), c.getString(1), c.getInt(2))
            }
            val total = ((phoneStepDay(current.date)?.steps ?: 0L) + PhoneSteps.increment(previous, current)).coerceAtMost(300000L)
            db.insertWithOnConflict("phone_step_days", null, ContentValues().apply { put("date", current.date); put("steps", total); put("updated_at", updatedAt) }, SQLiteDatabase.CONFLICT_REPLACE)
            db.insertWithOnConflict("phone_step_counter", null, ContentValues().apply { put("id", 1); put("raw", current.raw); put("date", current.date); put("boot", current.boot) }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful(); return total
        } finally { db.endTransaction() }
    }
    private fun createProfiles(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles(date TEXT PRIMARY KEY, age INTEGER NOT NULL, sex TEXT NOT NULL, height REAL NOT NULL, activity TEXT NOT NULL)")
    }
    fun profiles(): List<Profile> {
        val result = mutableListOf<Profile>()
        readableDatabase.rawQuery("SELECT date,age,sex,height,activity FROM profiles ORDER BY date", null).use { c ->
            while (c.moveToNext()) result.add(Profile(c.getString(0), c.getInt(1), FormulaSex.valueOf(c.getString(2)), c.getDouble(3), ActivityLevel.valueOf(c.getString(4))))
        }
        return result
    }
    fun saveProfile(profile: Profile, weight: Weight) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("profiles", null, ContentValues().apply {
                put("date", profile.date); put("age", profile.age); put("sex", profile.sex.name); put("height", profile.heightCm); put("activity", profile.activity.name)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            saveWeight(weight)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun createExerciseTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE day_steps(date TEXT PRIMARY KEY, steps INTEGER NOT NULL, source TEXT NOT NULL, updated_at INTEGER NOT NULL, included INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE exercise_templates(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, rate REAL NOT NULL, kind TEXT NOT NULL)")
    }
    fun stepDays(): List<StepDay> {
        val result = mutableListOf<StepDay>()
        readableDatabase.rawQuery("SELECT date,steps,source,updated_at,included FROM day_steps ORDER BY date DESC", null).use { c ->
            while (c.moveToNext()) result.add(StepDay(c.getString(0), c.getLong(1), c.getString(2), c.getLong(3), c.getInt(4) != 0))
        }
        return result
    }
    fun stepDay(date: String): StepDay? = readableDatabase.rawQuery("SELECT date,steps,source,updated_at,included FROM day_steps WHERE date=?", arrayOf(date)).use { c ->
        if (!c.moveToFirst()) null else StepDay(c.getString(0), c.getLong(1), c.getString(2), c.getLong(3), c.getInt(4) != 0)
    }
    fun saveSteps(day: StepDay) {
        require(day.steps in 0..300000)
        writableDatabase.insertWithOnConflict("day_steps", null, ContentValues().apply {
            put("date", day.date); put("steps", day.steps); put("source", day.source); put("updated_at", day.updatedAt); put("included", if (day.included) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun deleteSteps(date: String) { writableDatabase.delete("day_steps", "date=?", arrayOf(date)) }
    fun exerciseTemplates(): List<ExerciseTemplate> {
        val result = mutableListOf<ExerciseTemplate>()
        readableDatabase.rawQuery("SELECT id,name,rate,kind FROM exercise_templates ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) result.add(ExerciseTemplate(c.getLong(0), c.getString(1), c.getDouble(2), ExerciseRateKind.valueOf(c.getString(3))))
        }
        return result
    }
    fun saveExerciseTemplate(t: ExerciseTemplate) {
        val values = ContentValues().apply { put("name", t.name); put("rate", t.kcalPerMinute); put("kind", t.kind.name) }
        if (t.id > 0) writableDatabase.update("exercise_templates", values, "id=?", arrayOf(t.id.toString())) else writableDatabase.insertOrThrow("exercise_templates", null, values)
    }
    fun deleteExerciseTemplate(t: ExerciseTemplate) { writableDatabase.delete("exercise_templates", "id=?", arrayOf(t.id.toString())) }
    private fun createQuickFoods(db: SQLiteDatabase) { db.execSQL("CREATE TABLE quick_foods(id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT NOT NULL)") }
    fun quickFoods(): List<QuickFood> {
        val result = mutableListOf<QuickFood>()
        readableDatabase.rawQuery("SELECT id,payload FROM quick_foods ORDER BY id DESC", null).use { c -> while (c.moveToNext()) result.add(FoodJson.decode(c.getString(1), c.getLong(0))) }
        return result
    }
    fun saveQuickFood(food: QuickFood): QuickFood {
        val values = ContentValues().apply { put("payload", FoodJson.encode(food)) }
        return if (food.id > 0) { writableDatabase.update("quick_foods", values, "id=?", arrayOf(food.id.toString())); food }
        else food.copy(id = writableDatabase.insertOrThrow("quick_foods", null, values))
    }
    fun deleteQuickFood(food: QuickFood) { writableDatabase.delete("quick_foods", "id=?", arrayOf(food.id.toString())) }
    private fun createMealTable(db: SQLiteDatabase) { db.execSQL("CREATE TABLE skipped_meals(date TEXT NOT NULL, meal TEXT NOT NULL, PRIMARY KEY(date,meal))") }
    fun entries(date: String? = null): List<Entry> {
        val result = mutableListOf<Entry>()
        readableDatabase.query("entries", null, if (date == null) null else "date=?", date?.let { arrayOf(it) }, null, null, "date DESC, id DESC").use { c ->
            while (c.moveToNext()) result.add(Entry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getInt(5),
                if (c.isNull(6)) null else c.getDouble(6), if (c.isNull(7)) null else c.getDouble(7), if (c.isNull(8)) null else c.getDouble(8), c.getString(9), if (c.isNull(10)) null else c.getDouble(10), c.getString(11),
                if (c.isNull(12)) null else c.getDouble(12), c.getString(13), c.getLong(14)))
        }
        return result
    }
    fun save(e: Entry) {
        val values = ContentValues().apply { put("date", e.date); put("kind", e.kind); put("name", e.name); put("kcal", e.kcal); put("minutes", e.minutes) }
        listOf("protein" to e.protein, "carbs" to e.carbs, "fat" to e.fat, "sodium" to e.sodium).forEach { (key, value) ->
            if (value == null) values.putNull(key) else values.put(key, value)
        }
        values.put("meal", if (e.kind == "food") Meal.from(e.meal).key else "unspecified")
        values.put("details", e.details)
        if (e.exerciseRate == null) values.putNull("exercise_rate") else values.put("exercise_rate", e.exerciseRate)
        values.put("exercise_rate_kind", e.exerciseRateKind); values.put("overlap_steps", e.overlapSteps)
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (e.id == 0L) db.insertOrThrow("entries", null, values)
            else db.update("entries", values, "id=?", arrayOf(e.id.toString()))
            if (e.kind == "food") db.delete("skipped_meals", "date=? AND meal=?", arrayOf(e.date, e.meal))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun skippedMeals(): List<SkippedMeal> {
        val result = mutableListOf<SkippedMeal>()
        readableDatabase.rawQuery("SELECT date,meal FROM skipped_meals ORDER BY date DESC,meal", null).use { c -> while (c.moveToNext()) result.add(SkippedMeal(c.getString(0), c.getString(1))) }
        return result
    }
    fun isSkipped(date: String, meal: Meal): Boolean = readableDatabase.rawQuery("SELECT 1 FROM skipped_meals WHERE date=? AND meal=?", arrayOf(date, meal.key)).use { it.moveToFirst() }
    fun resolved(date: String, meal: Meal): Boolean = readableDatabase.rawQuery("SELECT 1 FROM entries WHERE date=? AND kind='food' AND meal=? LIMIT 1", arrayOf(date, meal.key)).use { it.moveToFirst() } || isSkipped(date, meal)
    /** Do not let a stale notification mark an already-recorded meal as skipped. */
    fun skip(date: String, meal: Meal): Boolean {
        if (!meal.remindable) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (db.rawQuery("SELECT 1 FROM entries WHERE date=? AND kind='food' AND meal=? LIMIT 1", arrayOf(date, meal.key)).use { it.moveToFirst() }) return false
            db.insertWithOnConflict("skipped_meals", null, ContentValues().apply { put("date", date); put("meal", meal.key) }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful(); return true
        } finally { db.endTransaction() }
    }
    fun undoSkip(date: String, meal: Meal) { writableDatabase.delete("skipped_meals", "date=? AND meal=?", arrayOf(date, meal.key)) }
    fun delete(e: Entry) { writableDatabase.delete("entries", "id=?", arrayOf(e.id.toString())) }
    fun weights(): List<Weight> {
        val result = mutableListOf<Weight>()
        readableDatabase.rawQuery("SELECT date,kg FROM weights ORDER BY date", null).use { c -> while (c.moveToNext()) result.add(Weight(c.getString(0), c.getDouble(1))) }
        return result
    }
    fun saveWeight(w: Weight) {
        writableDatabase.insertWithOnConflict("weights", null, ContentValues().apply { put("date", w.date); put("kg", w.kg) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun deleteWeight(date: String) { writableDatabase.delete("weights", "date=?", arrayOf(date)) }
}
