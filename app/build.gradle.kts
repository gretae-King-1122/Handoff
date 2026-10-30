import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Diagnostics is opt-in even for debug APKs: old discovery hooks are not needed for normal handoff.
val handoffDiagnostics = providers.gradleProperty("handoffDiagnostics").orElse("false").map {
    require(it == "true" || it == "false") { "handoffDiagnostics must be true or false" }
    it.toBoolean()
}
val handoffBoundaryDiagnostics = providers.gradleProperty("handoffBoundaryDiagnostics").orElse("false").map {
    require(it == "true" || it == "false") { "handoffBoundaryDiagnostics must be true or false" }
    it.toBoolean()
}

// Keep the publishing key and passwords outside the repository. Missing config produces an unsigned
// release for source builders; never silently sign a release with the public Android debug identity.
val releaseSigningFile = providers.environmentVariable("HANDOFF_SIGNING_PROPERTIES").orNull
    ?.let { file(it) }
    ?: file("${System.getProperty("user.home")}/.handoff/release-signing/signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
fun signingValue(name: String): String = releaseSigning.getProperty(name)
    ?.takeIf { it.isNotBlank() } ?: error("Release signing property is missing: $name")

android {
    namespace = "com.handoff"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.handoff.module"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.3.0-dev"
        buildConfigField("boolean", "HANDOFF_DIAGNOSTICS", handoffDiagnostics.get().toString())
        buildConfigField("boolean", "HANDOFF_BOUNDARY_DIAGNOSTICS", handoffBoundaryDiagnostics.get().toString())
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningFile.isFile) {
            create("handoffRelease") {
                storeFile = file(signingValue("storeFile"))
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            buildConfigField("boolean", "HANDOFF_DIAGNOSTICS", "false")
            buildConfigField("boolean", "HANDOFF_BOUNDARY_DIAGNOSTICS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningFile.isFile) {
                signingConfig = signingConfigs.getByName("handoffRelease")
            }
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
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(project(":module-api"))
    implementation(project(":module-hook-lsposed"))
    implementation(project(":module-ui"))
    implementation(project(":module-storage"))
}
