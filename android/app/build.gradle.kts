plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.studyo.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.studyo.android"   // 2.x (TWA) ile aynı: üstüne güncelleme olarak kurulur
        minSdk = 26
        targetSdk = 35
        versionCode = 1000 + (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "3.33." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        ndk { abiFilters += listOf("arm64-v8a") }   // telefon arm64; Vosk/JNA yerel kitaplıkları yalnız bu mimaride (APK küçük kalır)
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
    packaging { resources { excludes += setOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1") } }
}

/* 3.x: yerel (native) uygulama. Video: Kotlin + Compose + Media3 (ExoPlayer önizleme, Transformer dışa aktarma).
   Fotoğraf ve önceki Stüdyo: aynı uygulamanın içinden Trusted Web Activity ile (Chrome) açılır. */
dependencies {
    val media3 = "1.8.1"
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0")
    // Nesne kesme: Google ML Kit Subject Segmentation (ücretsiz, cihaz içinde; model Play Hizmetleri ile bir kez iner)
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    // Otomatik altyazı: Vosk (Apache-2.0), çevrimdışı konuşma tanıma; Türkçe model uygulama içinden bir kez iner
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
}
