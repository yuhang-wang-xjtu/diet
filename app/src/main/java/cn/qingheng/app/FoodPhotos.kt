package cn.qingheng.app

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.util.UUID

object FoodPhotos {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private fun file(context: Context, key: String): File? = key.takeIf { Regex("[a-f0-9-]{36}\\.jpg").matches(it) }?.let { File(context.filesDir, "food-images/$it") }
    private fun bitmap(context: Context, key: String): Bitmap? {
        cache.get(key)?.let { return it }
        val bitmap = try {
            val icon = FoodIcons.find(key)
            if (icon != null) context.assets.open(icon.asset).use { BitmapFactory.decodeStream(it) }
            else file(context, key)?.takeIf { it.isFile }?.let {
                BitmapFactory.decodeFile(it.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        } catch (_: IOException) { null }
        bitmap?.let { cache.put(key, it) }
        return bitmap
    }
    fun display(view: ImageView, key: String, fallback: String = FoodIcons.categoryKey(FoodCategories.LEGACY)) {
        val primary = bitmap(view.context, key)
        if (key == "design:rice" || primary == null && fallback == "design:rice") { DesignUi.bindSvg(view, "rice.svg"); return }
        val resolved = if (primary == null) fallback else key
        val bitmap = primary ?: bitmap(view.context, fallback)
        val icon = FoodIcons.find(resolved) != null
        view.setTag(R.id.design_asset_name, null)
        view.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
        view.scaleType = if (icon) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        val padding = if (icon) (8 * view.resources.displayMetrics.density).toInt() else 0
        view.setPadding(padding, padding, padding, padding)
        if (bitmap == null) view.setImageResource(R.drawable.food_placeholder) else view.setImageBitmap(bitmap)
    }
    fun fallback(food: QuickFood) = if (food.id == -1L) "design:rice" else FoodIcons.defaultFor(food)
    fun exportBytes(context: Context, key: String): ByteArray? = file(context, key)?.takeIf { it.isFile }?.readBytes()
    fun restoreBytes(context: Context, bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "菜品图片无法识别" }
        val key = "${UUID.randomUUID()}.jpg"
        val destination = file(context, key)!!
        destination.parentFile!!.mkdirs(); destination.writeBytes(bytes)
        return key
    }
    fun import(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (resolver.openInputStream(uri) ?: error("无法打开图片")).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..100000 && bounds.outHeight in 1..100000) { "无法识别图片尺寸" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("图片格式不支持")
        val orientation = try { resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } } catch (_: Exception) { null }
        val matrix = Matrix().apply { when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        } }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val bitmap = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.WHITE); drawBitmap(rotated, 0f, 0f, null) }
        val key = "${UUID.randomUUID()}.jpg"; val destination = file(context, key)!!
        try {
            check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
            destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        } catch (e: Exception) { destination.delete(); throw e }
        finally { bitmap.recycle(); if (rotated !== decoded) rotated.recycle(); decoded.recycle() }
        return key
    }
}
/** Construct during onCreate so registration precedes STARTED. Images are copied, never uploaded. */
class FoodPhotoPicker(private val activity: ComponentActivity) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callback: ((String) -> Unit)? = null
    var busy: Boolean = false
        private set
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val chosen = callback; callback = null
        if (uri != null && chosen != null) {
            busy = true
            scope.launch {
                try { val image = withContext(Dispatchers.IO) { FoodPhotos.import(activity, uri) }; chosen(image) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { Toast.makeText(activity, "图片读取失败，请换一张图片重试", Toast.LENGTH_LONG).show() }
                finally { busy = false }
            }
        }
    }
    init { activity.lifecycle.addObserver(LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) { callback = null; scope.cancel() } }) }
    fun choose(chosen: (String) -> Unit) {
        if (busy) { Toast.makeText(activity, "图片处理中，请稍候", Toast.LENGTH_SHORT).show(); return }
        callback = chosen
        try { launcher.launch(arrayOf("image/*")) }
        catch (_: android.content.ActivityNotFoundException) { callback = null; Toast.makeText(activity, "设备没有图片选择器", Toast.LENGTH_SHORT).show() }
    }
}
