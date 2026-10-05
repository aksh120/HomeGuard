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

// Phase 4: the google-services plugin fails the build outright when its config is missing, so it is
// applied only when the file is actually present. Push then degrades to "unavailable" at runtime
// rather than making the project unbuildable, which matters because the config is supplied per
// developer and per build type rather than committed.
//
// The build warns loudly instead of failing, because a build that cannot reach Firebase is not
// obviously broken from the outside and push silently not working in a safety app is worse than a
// noisy log line. Release distribution still requires the file; see README and NEW_PHASES.md L12.
val firebaseConfigFiles = listOf(
    file("src/debug/google-services.json"),
    file("src/release/google-services.json"),
    file("google-services.json")
)
val hasFirebaseConfig = firebaseConfigFiles.any { it.exists() }
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn(
        "WARNING: google-services.json not found in ${firebaseConfigFiles.map { it.path }}. " +
            "Push notifications will be unavailable. See README section 4."
    )
}

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
    // Required by registerForActivityResult; see the note in libs.versions.toml.
    implementation(libs.androidx.fragment)

    // Push delivery (Phase 4). The BOM pins every Firebase artifact, so firebase-messaging carries no
    // version of its own.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

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
