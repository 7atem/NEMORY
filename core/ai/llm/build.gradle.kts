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

        // Qwen3-VL Tier 2 VLM model download config (language GGUF + mmproj GGUF).
        buildConfigField(
            "String",
            "QWEN_MODEL_URL",
            "\"${project.findProperty("QWEN_MODEL_URL") ?: "https://example.com/Qwen3VL-2B-Instruct-Q4_K_M.gguf"}\""
        )
        buildConfigField(
            "String",
            "QWEN_MODEL_SHA256",
            "\"${project.findProperty("QWEN_MODEL_SHA256") ?: "0".repeat(64)}\""
        )
        buildConfigField(
            "long",
            "QWEN_MODEL_SIZE_BYTES",
            "${project.findProperty("QWEN_MODEL_SIZE_BYTES") ?: "0"}L"
        )
        buildConfigField(
            "String",
            "QWEN_MMPROJ_URL",
            "\"${project.findProperty("QWEN_MMPROJ_URL") ?: "https://example.com/mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf"}\""
        )
        buildConfigField(
            "String",
            "QWEN_MMPROJ_SHA256",
            "\"${project.findProperty("QWEN_MMPROJ_SHA256") ?: "0".repeat(64)}\""
        )
        buildConfigField(
            "long",
            "QWEN_MMPROJ_SIZE_BYTES",
            "${project.findProperty("QWEN_MMPROJ_SIZE_BYTES") ?: "0"}L"
        )

        // Experimental Arabic VL transcription path; default off. Enable locally with
        // -Pnemory.arabicVlExperiment=true.
        buildConfigField(
            "boolean",
            "ARABIC_VL_EXPERIMENT",
            "${project.findProperty("nemory.arabicVlExperiment") ?: "false"}"
        )

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                arguments("-DGGML_NATIVE=OFF", "-DGGML_OPENMP=OFF")
            }
        }

        ndk {
            // llama.cpp's fp16 NEON kernels (ggml sgemm) require AArch64 FP16 intrinsics;
            // 32-bit ABIs do not compile, and Play requires 64-bit anyway.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Explicit escape hatch for JVM tests on hosts that cannot fetch the Android toolchain.
    // Normal builds must package the native runtime.
    if (!providers.gradleProperty("nemory.skipNativeForTests").map(String::toBoolean).getOrElse(false)) {
        ndkVersion = "27.2.12479018"
        externalNativeBuild {
            cmake {
                path("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
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

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)

    // WorkManager (On-device model download)
    implementation(libs.workmanager.runtime.ktx)
    
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
}



