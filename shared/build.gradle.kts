plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                    freeCompilerArgs.add("-Xannotation-default-target=param-property")
                }
            }
        }
    }
    
    sourceSets.androidMain.dependencies {
        implementation(libs.biometric)
        implementation("androidx.fragment:fragment-ktx:1.8.5")
        implementation(libs.security.crypto)
        implementation(libs.workmanager.runtime.ktx)
        implementation("androidx.core:core-ktx:1.15.0")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            // Deliberate choice: dynamic framework so the iOS app can link and embed it
            // using a standard FRAMEWORK_SEARCH_PATHS / -framework shared setup.
            baseName = "shared"
            isStatic = false
            binaryOption("bundleId", "com.vaultbrain.shared")
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Room annotation processor, per platform compilation (commonMain sources are
// processed as part of each target compilation in KSP multiplatform setups).
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

android {
    namespace = "com.vaultbrain.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}
