plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.dashboard_and_security_module"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.dashboard_and_security_module"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(platform(libs.firebase.bom)) // Firebase BoM (always declare first)

    // --- START: CORRECTED FIREBASE DEPENDENCIES ---
    // Use the aliases defined in your libs.versions.toml file
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database) // Added this based on your toml file
    // --- END: CORRECTED FIREBASE DEPENDENCIES ---

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // --- START: CORRECTED GOOGLE PLAY SERVICES DEPENDENCIES ---
    // Use the aliases defined in your libs.versions.toml file
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    // --- END: CORRECTED GOOGLE PLAY SERVICES DEPENDENCIES ---

    // Testing Dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Map Dependencies
    implementation(libs.osmdroid.android)

    // ✅ Gson for JSON serialization/deserialization
    implementation(libs.gson)

    // Zoom gradle
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    // WORKER
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
