import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
val commitCount = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
    .standardOutput.asText.map { it.trim().toInt() }
val keystorePath = providers.environmentVariable("BRAINROT_KEYSTORE_PATH")

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
        release {
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
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
}
