import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
val commitCount = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
    .standardOutput.asText.map { it.trim().toInt() }
val keystorePath = providers.environmentVariable("BRAINROT_KEYSTORE_PATH")
val clerkKey = providers.environmentVariable("CLERK_PUBLISHABLE_KEY").orElse("")
if (clerkKey.get().isBlank() && gradle.startParameter.taskNames.any { it.contains("Release") }) {
    throw GradleException("CLERK_PUBLISHABLE_KEY is required for release builds")
}

android {
    namespace = "app.flashcards.android"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.flashcards.android"
        minSdk = 26
        targetSdk = 37
        versionCode = commitCount.get()
        versionName = versionProps.getProperty("versionName")
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"${clerkKey.get()}\"")
    }

    signingConfigs {
        if (keystorePath.isPresent) {
            create("release") {
                storeFile = file(keystorePath.get())
                storePassword = providers.environmentVariable("BRAINROT_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("BRAINROT_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("BRAINROT_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // Phone reaches the local backend through: adb reverse tcp:8088 tcp:8088
            buildConfigField("String", "API_BASE_URL", "\"http://localhost:8088/api/\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://brainrot.deeka.work/api/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
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
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.clerk.api)
    implementation(libs.clerk.ui)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}
