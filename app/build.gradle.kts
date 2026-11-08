plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bobby.cadence"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bobby.cadence"
        minSdk = 26
        targetSdk = 35
        // CI passes these (-PappVersion / -PappCode); local builds fall back to 1.0
        versionCode = (project.findProperty("appCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersion") as String?) ?: "1.0"
    }

    // Release signing comes from the environment (GitHub Actions secrets, or your shell), never from the repo.
    // Without it - a fresh clone, a pull request, a fork - release builds are signed with the debug key,
    // so everyone can still build and install. See docs/RELEASING.md.
    val keystore = System.getenv("CADENCE_KEYSTORE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (keystore != null && file(keystore).exists()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("CADENCE_STORE_PASSWORD")
                keyAlias = System.getenv("CADENCE_KEY_ALIAS") ?: "cadence"
                keyPassword = System.getenv("CADENCE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    // the JVM has no org.json; Android supplies it at runtime
    testImplementation("org.json:json:20240303")
}
