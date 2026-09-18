plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)
}

val uploadStoreFilePath = providers.environmentVariable("NEMORY_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("NEMORY_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("NEMORY_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("NEMORY_UPLOAD_KEY_PASSWORD").orNull
val uploadSigningConfigured = listOf(
    uploadStoreFilePath,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword
).all { !it.isNullOrBlank() }

// Explicit escape hatch shared with core:ai:llm for hosts without the NDK. A build made
// this way has no Qwen native runtime; stamp the version so it can never be mistaken
// for a production build.
val nativeRuntimeOmitted = providers.gradleProperty("nemory.skipNativeForTests")
    .map(String::toBoolean)
    .getOrElse(false)

android {
    namespace = "com.vaultbrain.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nemory.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 15
        versionName = "1.1.2" + if (nativeRuntimeOmitted) "-nonative" else ""
        buildConfigField("boolean", "NATIVE_RUNTIME_OMITTED", nativeRuntimeOmitted.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("release") {
                storeFile = file(uploadStoreFilePath!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (uploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
    lint {
        // Dependency upgrades are managed deliberately and should not obscure
        // correctness findings in CI lint reports.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:vectorstore"))
    implementation(project(":core:security"))
    implementation(project(":core:notifications"))
    implementation(project(":core:integrations"))

    implementation(project(":core:ai:embeddings"))
    implementation(project(":core:ai:rag"))
    implementation(project(":core:ai:llm"))
    implementation(project(":core:ai:heuristics"))
    implementation(project(":core:ai:vision"))
    implementation(project(":feature:vault"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:brain"))
    implementation(project(":feature:briefing"))
    implementation(project(":feature:voice"))
    implementation(project(":feature:lens-money"))
    implementation(project(":feature:lens-health"))
    implementation(project(":feature:lens-travel"))
    implementation(project(":feature:lens-bureaucracy"))
    implementation(project(":feature:lens-media"))
    implementation(project(":feature:settings"))
    implementation(project(":sync:gmail"))
    implementation(project(":sync:drive"))
    implementation(project(":core:billing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)
    implementation(libs.workmanager.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}


