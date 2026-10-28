plugins {
    alias(libs.plugins.android.application)
}

// Diagnostics is opt-in even for debug APKs: old discovery hooks are not needed for normal handoff.
val handoffDiagnostics = providers.gradleProperty("handoffDiagnostics").orElse("false").map {
    require(it == "true" || it == "false") { "handoffDiagnostics must be true or false" }
    it.toBoolean()
}

android {
    namespace = "com.handoff"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.handoff.module"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "HANDOFF_DIAGNOSTICS", handoffDiagnostics.get().toString())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(project(":module-api"))
    implementation(project(":module-hook-lsposed"))
}
