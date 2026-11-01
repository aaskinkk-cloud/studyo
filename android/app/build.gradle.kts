plugins { id("com.android.application") }
android {
    namespace = "app.studyo.android"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.studyo.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 100 + (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "2." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }
    signingConfigs {
        create("okur") {
            storeFile = file("../okur.jks"); storePassword = "okurokur"; keyAlias = "okur"; keyPassword = "okurokur"
        }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("okur") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
/* 2.x: Trusted Web Activity — sayfa telefondaki Chrome'un kendisiyle açılır (WebView değil) */
dependencies { implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0") }
