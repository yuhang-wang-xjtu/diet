package cn.qingheng.app

import android.app.*
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class AppUpdates(private val activity: ComponentActivity, private val changed: () -> Unit) {
    private data class VerifiedApk(val file: File, val code: Long)
    private val prefs = activity.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val manager = activity.getSystemService(DownloadManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var label: TextView? = null
    private var foreground = false
    private var message = "点击检查GitHub上的最新正式版"
    val repo get() = GitHubRepo.parse(prefs.getString("repo", GitHubRepo.DEFAULT_SLUG) ?: "")
    @Suppress("DEPRECATION")
    private fun installed() = activity.packageManager.getPackageInfo(activity.packageName, signingFlags())
    val version get() = installed().versionName ?: "未知"
    fun bind(view: TextView) { label = view; view.text = message }
    private fun status(value: String) { message = value; label?.text = value }
    fun resume() {
        foreground = true
        val target = prefs.getLong("target_code", -1)
        if (target > 0 && code(installed()) >= target) {
            clearDownload(); status("更新已完成，当前版本 $version"); return
        }
        if (prefs.getBoolean("install_permission_pending", false)) {
            prefs.edit().putBoolean("install_permission_pending", false).apply()
            if (activity.packageManager.canRequestPackageInstalls()) install()
            else status("未允许安装应用；可稍后点击“下载状态 / 安装更新”继续")
        } else if (prefs.getLong("download_id", -1) > 0) monitor()
    }
    fun pause() { foreground = false; job?.cancel() }
    fun close() { scope.cancel(); label = null }
    fun configure() {
        val field = EditText(activity).apply { hint = "https://github.com/用户名/仓库名"; setText(repo?.slug ?: ""); setSingleLine(true) }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; val p = (24 * resources.displayMetrics.density).toInt(); setPadding(p, p / 2, p, p / 2)
            addView(TextView(activity).apply { text = "填写发布轻衡APK的公开GitHub仓库。留空可清除。更换仓库会取消当前更新下载，不影响记录数据。" }); addView(field)
        }
        val dialog = AlertDialog.Builder(activity).setTitle("GitHub更新源").setView(box).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val raw = field.text.toString().trim(); val value = GitHubRepo.parse(raw)
            if (raw.isNotEmpty() && value == null) field.error = "请填公开GitHub仓库地址或 owner/repo"
            else {
                job?.cancel(); clearDownload()
                prefs.edit().putString("repo", value?.slug ?: "").apply()
                status(if (value == null) "尚未配置GitHub更新源" else "更新源已保存，点击检查更新")
                dialog.dismiss(); changed()
            }
        } }; dialog.show()
    }
    fun check() {
        val source = repo ?: run { configure(); return }
        if (prefs.getLong("download_id", -1) > 0) { monitor(showReady = true); return }
        job?.cancel()
        job = scope.launch {
            status("正在检查 ${source.slug} 的最新正式版…")
            try {
                val release = withContext(Dispatchers.IO) { latest(source) }
                ensureActive()
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) error("最新条目不是正式发布版")
                val tag = release.getString("tag_name")
                val comparison = UpdatePolicy.compareVersions(tag, version) ?: error("版本标签须使用 v1.8.0 或 1.8 这样的数字格式")
                if (comparison <= 0) { status("当前版本 $version；GitHub最新正式版 $tag，无需更新"); return@launch }
                val assets = release.optJSONArray("assets")
                val apks = (0 until (assets?.length() ?: 0)).mapNotNull { i ->
                    val a = assets!!.getJSONObject(i)
                    val digest = a.optString("digest").takeIf { it.isNotBlank() && it != "null" }
                    ReleaseApk(a.optString("name"), a.optString("browser_download_url"), a.optLong("size"), digest)
                        .takeIf { a.optString("state") == "uploaded" && UpdatePolicy.validAsset(source, it) }
                }
                status("发现新版本 $tag，当前 $version")
                AlertDialog.Builder(activity).setTitle("轻衡 $tag")
                    .setMessage(release.optString("body").take(4000).ifBlank { "此版本没有发布说明。" } + if (apks.isEmpty()) "\n\n没有可用的APK附件，请查看Release页面。" else "\n\n下载后核对包名、版本、签名，再交给系统确认安装。")
                    .setPositiveButton(if (apks.isEmpty()) "查看Release" else "下载更新") { _, _ ->
                        if (apks.isEmpty()) openRelease() else if (apks.size == 1) download(apks.first()) else {
                            AlertDialog.Builder(activity).setTitle("选择适合手机的APK").setItems(apks.map { "${it.name}（${it.size / 1024 / 1024.0} MB）" }.toTypedArray()) { _, index -> download(apks[index]) }.setNegativeButton("取消", null).show()
                        }
                    }.setNeutralButton("Release页面") { _, _ -> openRelease() }.setNegativeButton("稍后", null).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status("检查失败：${e.message ?: "请检查网络后重试"}") }
        }
    }
    private fun latest(source: GitHubRepo): JSONObject {
        val connection = URL(source.api).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000; connection.readTimeout = 15000; connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Qingheng-Android")
            when (connection.responseCode) {
                200 -> Unit
                404 -> error("仓库不存在、不是公开仓库，或尚未发布正式Release")
                403, 429 -> error("GitHub访问受限或请求过于频繁，请稍后再试")
                301, 302, 307, 308 -> error("仓库地址已重定向，请更新仓库配置")
                else -> error("GitHub返回HTTP ${connection.responseCode}")
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (output.size() <= 2 * 1024 * 1024) {
                    val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            check(bytes.size <= 2 * 1024 * 1024) { "Release响应过大，请在浏览器查看" }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    fun openRelease() {
        val source = repo ?: run { configure(); return }
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.page))) }
        catch (_: ActivityNotFoundException) { status("设备没有可用浏览器") }
    }
    private fun download(apk: ReleaseApk) {
        val source = repo ?: return
        if (!UpdatePolicy.validAsset(source, apk)) { status("APK下载地址或元数据无效"); return }
        try {
            clearDownload()
            val name = "qingheng-${System.currentTimeMillis()}.apk"
            val root = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("下载目录不可用")
            val directory = File(root, "updates")
            check(directory.isDirectory || directory.mkdirs()) { "无法创建更新下载目录" }
            val request = DownloadManager.Request(Uri.parse(apk.url)).setTitle("轻衡更新：${apk.name}")
                .setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "updates/$name")
            val id = manager.enqueue(request)
            prefs.edit().putLong("download_id", id).putString("file", name).putLong("size", apk.size).putString("digest", apk.digest).putBoolean("installer_launched", false).apply()
            monitor()
        } catch (e: Exception) { status("无法开始下载：${e.message ?: "请检查下载管理器及存储空间"}") }
    }
    fun continueUpdate() {
        if (prefs.getLong("download_id", -1) > 0) monitor(showReady = true) else check()
    }
    fun cancelDownload() {
        job?.cancel(); clearDownload(); status("更新下载已清除，可重新检查")
    }
    private fun clearDownload() {
        val id = prefs.getLong("download_id", -1)
        if (id > 0) try { manager.remove(id) } catch (_: Exception) { }
        prefs.getString("file", null)?.takeIf { Regex("qingheng-[0-9]+\\.apk").matches(it) }?.let { File(activity.cacheDir, "updates/$it").delete() }
        prefs.edit().remove("download_id").remove("file").remove("size").remove("digest").remove("target_code").remove("installer_launched").remove("install_permission_pending").apply()
    }
    private fun monitor(showReady: Boolean = false) {
        if (job?.isActive == true) return
        val id = prefs.getLong("download_id", -1)
        if (id <= 0) return
        job = scope.launch {
            try {
                while (foreground) {
                    val state = withContext(Dispatchers.IO) {
                        manager.query(DownloadManager.Query().setFilterById(id)).use { c ->
                            check(c != null && c.moveToFirst()) { "下载任务已不存在，请清除后重新下载" }
                            Triple(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)), c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)), c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                        }
                    }
                    ensureActive()
                    if (state.second > UpdatePolicy.MAX_APK) { clearDownload(); error("APK超过下载大小上限，已取消下载") }
                    when (state.first) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val file = withContext(Dispatchers.IO) { verifiedFile() }
                            ensureActive()
                            status("APK已下载并通过包名、版本及签名检查")
                            if (showReady || !prefs.getBoolean("installer_launched", false)) installVerified(file)
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> error("下载失败（代码${state.third}），请检查网络/空间并清除后重试")
                        DownloadManager.STATUS_PAUSED -> status("下载暂停，等待网络或系统重试（${state.third}）")
                        else -> status("正在下载更新：${state.second / 1024} / ${prefs.getLong("size", 0) / 1024} KB")
                    }
                    delay(1000)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status("更新未完成：${e.message ?: "请重试"}") }
        }
    }
    @Suppress("DEPRECATION")
    private fun signingFlags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)?.map { hash(it.toByteArray()) }?.toSet() ?: emptySet()
    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 255) }
    @Suppress("DEPRECATION")
    private fun verifiedFile(): VerifiedApk {
        val name = prefs.getString("file", "") ?: ""
        check(Regex("qingheng-[0-9]+\\.apk").matches(name)) { "更新文件名无效" }
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("下载目录不可用")
        val downloaded = File(directory, "updates/$name")
        check(downloaded.isFile && downloaded.length() == prefs.getLong("size", -1) && downloaded.length() in 1..UpdatePolicy.MAX_APK) { "APK大小不完整，请清除后重新下载" }
        val cache = File(activity.cacheDir, "updates")
        check(cache.isDirectory || cache.mkdirs()) { "无法创建安装缓存目录" }
        val file = File(cache, name)
        downloaded.copyTo(file, overwrite = true)
        check(file.length() == prefs.getLong("size", -1)) { "安装缓存不完整，请重试" }
        prefs.getString("digest", null)?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            val actual = digest.digest().joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 255) }
            check(expected.equals("sha256:$actual", true)) { "APK的SHA-256校验失败，已阻止安装" }
        }
        val apk = activity.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags()) ?: error("不是有效的Android安装包")
        val current = installed()
        UpdatePolicy.packageProblem(apk.packageName, activity.packageName, code(apk), code(current), apk.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE,
            Build.VERSION.SDK_INT, signers(apk), signers(current))?.let { error(it) }
        return VerifiedApk(file, code(apk))
    }
    private fun install() {
        job?.cancel()
        job = scope.launch {
            try { val file = withContext(Dispatchers.IO) { verifiedFile() }; ensureActive(); installVerified(file) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status("无法安装：${e.message}") }
        }
    }
    private fun installVerified(apk: VerifiedApk) {
        if (!foreground) return
        prefs.edit().putLong("target_code", apk.code).apply()
        if (!activity.packageManager.canRequestPackageInstalls()) {
            prefs.edit().putBoolean("installer_launched", true).apply()
            status("APK已就绪，需要允许轻衡安装应用；可点“下载状态 / 安装更新”继续")
            AlertDialog.Builder(activity).setTitle("允许轻衡安装更新")
                .setMessage("需要在系统中允许轻衡安装应用。返回后继续交给系统安装器，安装仍需你确认；现有记录会保留。")
                .setPositiveButton("去设置") { _, _ ->
                    try {
                        prefs.edit().putBoolean("install_permission_pending", true).apply()
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    } catch (_: ActivityNotFoundException) { prefs.edit().remove("install_permission_pending").apply(); status("请在系统设置中允许轻衡安装应用后重试") }
                }.setNegativeButton("稍后", null).show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk.file)
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("轻衡更新", uri) })
            prefs.edit().putBoolean("installer_launched", true).apply()
            status("已打开系统安装界面；如取消，可点“下载状态 / 安装更新”重试")
        } catch (_: ActivityNotFoundException) { status("设备没有可用的APK安装器") }
    }
}
