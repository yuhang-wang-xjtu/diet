package cn.qingheng.app

import java.net.URI

data class GitHubRepo(val owner: String, val name: String) {
    val slug get() = "$owner/$name"
    val page get() = "https://github.com/$slug/releases/latest"
    val api get() = "https://api.github.com/repos/$slug/releases/latest"
    fun ownsAsset(url: String): Boolean = try {
        val u = URI(url)
        u.scheme == "https" && u.host == "github.com" && u.userInfo == null && u.port == -1 && u.fragment == null &&
            u.path.startsWith("/$slug/releases/download/", ignoreCase = true) && u.path.endsWith(".apk", ignoreCase = true) && u.path.split('/').none { it == "." || it == ".." }
    } catch (_: Exception) { false }
    companion object {
        const val DEFAULT_SLUG = "yuhang-wang-xjtu/diet"
        fun parse(raw: String): GitHubRepo? {
            val value = raw.trim().trimEnd('/').removeSuffix(".git")
            val slug = if (value.startsWith("https://github.com/", ignoreCase = true)) value.substring(19) else value
            val parts = slug.split('/')
            if (parts.size != 2 || !Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})").matches(parts[0]) ||
                !Regex("[A-Za-z0-9_.-]{1,100}").matches(parts[1]) || parts[1] in listOf(".", "..")) return null
            return GitHubRepo(parts[0], parts[1])
        }
    }
}
data class ReleaseApk(val name: String, val url: String, val size: Long, val digest: String?)
object UpdatePolicy {
    const val MAX_APK = 200L * 1024 * 1024
    fun compareVersions(candidate: String, current: String): Int? {
        fun parts(value: String): List<Int>? {
            val plain = value.trim().removePrefix("v").removePrefix("V")
            if (!Regex("[0-9]+(?:\\.[0-9]+){0,3}").matches(plain)) return null
            return plain.split('.').map { it.toIntOrNull() ?: return null }
        }
        val a = parts(candidate) ?: return null; val b = parts(current) ?: return null
        for (i in 0 until maxOf(a.size, b.size)) {
            val result = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        return 0
    }
    fun validAsset(repo: GitHubRepo, apk: ReleaseApk) = apk.name.endsWith(".apk", true) &&
        apk.size in 1..MAX_APK && repo.ownsAsset(apk.url) && (apk.digest == null || Regex("sha256:[a-fA-F0-9]{64}").matches(apk.digest))
    fun packageProblem(packageName: String, expected: String, version: Long, installed: Long, minSdk: Int, sdk: Int,
        signers: Set<String>, installedSigners: Set<String>): String? = when {
        packageName != expected -> "安装包不是轻衡，已阻止安装"
        version <= installed -> "安装包版本号没有增加，不能作为更新安装"
        minSdk > sdk -> "此更新不支持当前Android版本"
        signers.isEmpty() || installedSigners.isEmpty() || signers != installedSigners -> "安装包签名与当前应用不一致，不能覆盖更新；请使用相同签名发布"
        else -> null
    }
}
