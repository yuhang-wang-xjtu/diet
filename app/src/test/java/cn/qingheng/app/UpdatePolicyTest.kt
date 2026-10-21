package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val repo = GitHubRepo("owner", "qingheng")
    private val apk = ReleaseApk("qingheng.apk", "https://github.com/owner/qingheng/releases/download/v1.8/qingheng.apk", 12345, null)
    @Test fun defaultRepositoryUsesUserProvidedReleaseSource() {
        val source = GitHubRepo.parse(GitHubRepo.DEFAULT_SLUG)!!
        assertEquals("https://api.github.com/repos/yuhang-wang-xjtu/diet/releases/latest", source.api)
        assertTrue(source.ownsAsset("https://github.com/yuhang-wang-xjtu/diet/releases/download/v1.8/qingheng.apk"))
        assertFalse(source.ownsAsset(apk.url))
    }
    @Test fun parsesOnlyRepositoryAddressesAndBuildsLatestApi() {
        assertEquals(repo, GitHubRepo.parse(" https://github.com/owner/qingheng.git/ "))
        assertEquals(repo, GitHubRepo.parse("owner/qingheng"))
        assertEquals("https://api.github.com/repos/owner/qingheng/releases/latest", repo.api)
        for (raw in listOf("", "http://github.com/owner/qingheng", "https://evil.com/owner/qingheng", "owner/qingheng/releases", "https://github.com/owner/qingheng?token=x", "owner/..", "owner/repo#x")) assertNull(raw, GitHubRepo.parse(raw))
    }
    @Test fun numericVersionsAvoidLexicographicOrderingAndPadMissingParts() {
        assertTrue(UpdatePolicy.compareVersions("v1.10.0", "1.9")!! > 0)
        assertEquals(0, UpdatePolicy.compareVersions("v1.7.0", "1.7"))
        assertTrue(UpdatePolicy.compareVersions("1.6", "1.7")!! < 0)
        assertTrue(UpdatePolicy.compareVersions("V2", "1.99.99")!! > 0)
    }
    @Test fun unknownOrPrereleaseVersionIsNotSilentlyInstalled() {
        for (v in listOf("latest", "v1.8-beta", "", "9999999999999.0", "1.2.3.4.5")) assertNull(UpdatePolicy.compareVersions(v, "1.7"))
        assertNull(UpdatePolicy.compareVersions("1.8", "unknown"))
    }
    @Test fun assetMustBelongToConfiguredRepositoryOverHttps() {
        assertTrue(UpdatePolicy.validAsset(repo, apk))
        for (url in listOf("http://github.com/owner/qingheng/releases/download/v1.8/a.apk", "https://github.com.evil.com/owner/qingheng/releases/download/v1.8/a.apk", "https://github.com/other/qingheng/releases/download/v1.8/a.apk", "https://github.com/owner/qingheng/releases/download/../../other/a.apk", "https://github.com/owner/qingheng/releases/download/%2e%2e/other/a.apk")) assertFalse(url, UpdatePolicy.validAsset(repo, apk.copy(url = url)))
    }
    @Test fun metadataRejectsOversizedEmptyOrNonApkAttachments() {
        assertFalse(UpdatePolicy.validAsset(repo, apk.copy(size = 0)))
        assertFalse(UpdatePolicy.validAsset(repo, apk.copy(size = UpdatePolicy.MAX_APK + 1)))
        assertFalse(UpdatePolicy.validAsset(repo, apk.copy(name = "source.zip")))
        assertFalse(UpdatePolicy.validAsset(repo, apk.copy(url = apk.url.replace(".apk", ".zip"))))
    }
    @Test fun digestMustBeCompleteSha256WhenPresent() {
        assertTrue(UpdatePolicy.validAsset(repo, apk.copy(digest = "sha256:" + "ab".repeat(32))))
        assertFalse(UpdatePolicy.validAsset(repo, apk.copy(digest = "sha256:abc")))
        assertFalse(UpdatePolicy.validAsset(repo, apk.copy(digest = "md5:" + "ab".repeat(16))))
    }
    private fun problem(pkg: String = "cn.qingheng.app", code: Long = 9, min: Int = 26, signers: Set<String> = setOf("A")) =
        UpdatePolicy.packageProblem(pkg, "cn.qingheng.app", code, 8, min, 33, signers, setOf("A"))
    @Test fun onlySameAppSignedUpgradeCanProceed() { assertNull(problem()) }
    @Test fun rejectsDifferentPackageEvenWhenSignatureMatches() { assertNotNull(problem(pkg = "other.app")) }
    @Test fun rejectsDowngradeAndReusedVersionCode() { assertNotNull(problem(code = 7)); assertNotNull(problem(code = 8)) }
    @Test fun rejectsUnsupportedAndroidAndMissingOrDifferentSignatures() {
        assertNotNull(problem(min = 34))
        assertNotNull(problem(signers = emptySet()))
        assertNotNull(problem(signers = setOf("B")))
        assertNotNull(problem(signers = setOf("A", "B")))
    }
}
