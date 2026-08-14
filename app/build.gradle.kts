plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersionCode: Int = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

fun readAppVersion(): String {
    val constantsFile = file("src/main/java/win/downops/clipshare/util/Constants.kt")
    val match = Regex("""const val VERSION = "([^"]+)"""").find(constantsFile.readText())
    return match?.groupValues?.get(1) ?: "0.1.0"
}

val releaseVersionName: String = (project.findProperty("versionName") as String?) ?: readAppVersion()

val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")

android {
    namespace = "win.downops.clipshare"
    compileSdk = 36

    defaultConfig {
        applicationId = "win.downops.clipshare"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(releaseKeystorePath)
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.json:json:20240303")
}
