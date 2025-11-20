plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services") //  Firebase plugin
}

android {
    namespace = "com.example.oncalldoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.oncalldoc"
        minSdk = 24
        targetSdk = 36
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
    // -------------------
    // AndroidX & Material
    // -------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // -------------------
    // Firebase BOM (manages versions)
    // -------------------
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth-ktx")
    // Firebase Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Firebase Analytics
    implementation("com.google.firebase:firebase-analytics")

    // Optional: Firebase UI Auth (social login prebuilt UI)
    implementation("com.firebaseui:firebase-ui-auth:8.0.2")

    // Geolocation
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.firebase:geofire-android-common:3.2.0")
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.maps)

    // -------------------
    // Testing
    // -------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
