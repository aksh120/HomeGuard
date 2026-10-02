import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
// Debug builds may read the developer's local.properties. Release builds must NOT: they take
// credentials from CI secrets (SUPABASE_URL / SUPABASE_ANON_KEY env or -P properties) so a
// developer's machine never leaks a key into a shipped APK.
val localSupabaseUrl = (localProps.getProperty("supabase.url") ?: "").replace("\"", "\\\"")
val localSupabaseAnonKey = (localProps.getProperty("supabase.anon.key") ?: "").replace("\"", "\\\"")
val ciSupabaseUrl = (project.findProperty("SUPABASE_URL") ?: System.getenv("SUPABASE_URL") ?: "")
    .toString().replace("\"", "\\\"")
val ciSupabaseAnonKey = (project.findProperty("SUPABASE_ANON_KEY")
    ?: System.getenv("SUPABASE_ANON_KEY") ?: "").toString().replace("\"", "\\\"")

android {
    namespace = "org.homeguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.homeguard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "SUPABASE_URL", "\"$localSupabaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$localSupabaseAnonKey\"")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "SUPABASE_URL", "\"$ciSupabaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$ciSupabaseAnonKey\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android's org.json is a stub on the JVM unit-test classpath (toString() returns null).
    // This supplies the real implementation so payload serialisation is actually exercised.
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
