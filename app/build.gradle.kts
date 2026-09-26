import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ─────────────────────────────────────────────────────────────────────────────
//  >>> EDIT THIS ONE LINE: your deployed backend API (must end with "/api/") <<<
val apiBaseUrl = "https://livora-backend-jqjr.onrender.com/api/"
// ─────────────────────────────────────────────────────────────────────────────
// Fallback website origin for relative image paths (runtime value comes from /settings/public).
val siteUrl = "https://livorhospitality.vercel.app"
// Set true to point DEBUG builds at a backend running on your computer (Android emulator).
val useLocalBackendInDebug = false
val localDebugApiBaseUrl = "http://10.0.2.2:5000/api/"

fun socketUrlOf(api: String): String = api.trimEnd('/').removeSuffix("/api")
fun q(s: String): String = "\"" + s + "\""

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.livora.corbett"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.livora.corbett.vedant"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            val api = if (useLocalBackendInDebug) localDebugApiBaseUrl else apiBaseUrl
            buildConfigField("String", "API_BASE_URL", q(api))
            buildConfigField("String", "SOCKET_URL", q(socketUrlOf(api)))
            buildConfigField("String", "SITE_URL", q(siteUrl))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", q(apiBaseUrl))
            buildConfigField("String", "SOCKET_URL", q(socketUrlOf(apiBaseUrl)))
            buildConfigField("String", "SITE_URL", q(siteUrl))
            if (keystoreProps.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)

    // Android already ships org.json, so exclude the duplicate.
    implementation(libs.socketio.client) {
        exclude(group = "org.json", module = "json")
    }
}
