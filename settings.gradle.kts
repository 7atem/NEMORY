pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "Nemory"

include(":app")
include(":shared")
include(":core:common")
include(":core:database")
include(":core:integrations")
include(":core:vectorstore")
include(":core:security")
include(":core:notifications")
include(":core:billing")
include(":core:ai:embeddings")
include(":core:ai:rag")
include(":core:ai:llm")
include(":core:ai:heuristics")
include(":core:ai:vision")
include(":feature:vault")
include(":feature:capture")
include(":feature:brain")
include(":feature:briefing")
include(":feature:lens-money")
include(":feature:lens-health")
include(":feature:lens-travel")
include(":feature:lens-bureaucracy")
include(":feature:lens-media")
include(":feature:settings")
include(":feature:voice")
include(":sync:drive")
include(":sync:gmail")
// Diagnostic tooling is opt-in and is not required by production builds.
if (providers.gradleProperty("includeEvaluation").orNull == "true") {
    include(":evaluation")
}
