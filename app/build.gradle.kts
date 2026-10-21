plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "cn.qingheng.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig { applicationId = "cn.qingheng.app"; minSdk = 26; targetSdk = 35; versionCode = 10; versionName = "0.1.7" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
