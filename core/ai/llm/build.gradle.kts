plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vaultbrain.core.ai.llm"
    compileSdk = 36

    defaultConfig {
        minSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Gemma Tier 2 model download config. Real values live in local gradle.properties;
        // the placeholders keep the project buildable without any configuration.
        buildConfigField(
            "String",
            "GEMMA_MODEL_URL",
            "\"${project.findProperty("GEMMA_MODEL_URL") ?: "https://example.com/gemma3-1b-it-int4.task"}\""
        )
        buildConfigField(
            "String",
            "GEMMA_MODEL_SHA256",
            "\"${project.findProperty("GEMMA_MODEL_SHA256") ?: "0".repeat(64)}\""
        )
        buildConfigField(
            "long",
            "GEMMA_MODEL_SIZE_BYTES",
            "${project.findProperty("GEMMA_MODEL_SIZE_BYTES") ?: "0"}L"
        )
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.mediapipe.tasks.genai)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)

    // WorkManager (Gemma model download)
    implementation(libs.workmanager.runtime.ktx)
    
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}


