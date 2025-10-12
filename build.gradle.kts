plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.10"
    id("com.google.devtools.ksp") version "1.9.10-1.0.13"
    id("com.google.gms.google-services") version "4.4.0"
}

android {
    namespace = "com.example.autoflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.autoflow"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       /*
        //buildConfigField("String", "OPENROUTER_API_KEY", "\"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\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"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\"")
        
        // FREE Vision Models for Receipt Parsing
        buildConfigField("String", "OPENROUTER_VISION_MODEL", "\"google/gemini-2.5-flash-image-preview:free\"")
        buildConfigField("String", "OPENROUTER_TEXT_MODEL", "\"google/gemini-pro:free\"")
        buildConfigField("String", "OPENROUTER_FALLBACK_MODEL", "\"meta-llama/llama-3.2-11b-vision-instruct:free\"")
        
        // Enable multidex for large app
        multiDexEnabled = true
        */
        buildConfigField("String", "OPENROUTER_API_KEY", "\"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\"")

// CORRECTED: Using powerful and free models
        buildConfigField("String", "OPENROUTER_VISION_MODEL", "\"meta-llama/llama-3.2-11b-vision-instruct:free\"")
        buildConfigField("String", "OPENROUTER_TEXT_MODEL", "\"google/gemini-pro:free\"")
        buildConfigField("String", "OPENROUTER_FALLBACK_MODEL", "\"qwen/qwen2.5-vl-32b-instruct:free\"")

// Enable multidex for large app
        multiDexEnabled = true
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")

    // Multidex support
    implementation("androidx.multidex:multidex:2.0.1")

    implementation(platform("com.google.firebase:firebase-bom:32.6.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.json:json:20240303")

    // Room database with annotation processor (using KSP instead of KAPT)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}