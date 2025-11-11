plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services") //  Firebase plugin
}

android {
    namespace = "com.example.oncalldoc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.oncalldoc"
        minSdk = 24
        targetSdk = 34
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
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth-ktx")
    // Firebase Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Firebase Analytics
    implementation("com.google.firebase:firebase-analytics")

    // Optional: Firebase UI Auth (social login prebuilt UI)
    implementation("com.firebaseui:firebase-ui-auth:8.0.2")
    implementation(libs.firebase.auth)
    implementation("android.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.firebase.firestore.ktx)
    // -------------------
    // Testing
    // -------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
