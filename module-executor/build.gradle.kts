plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.handoff.executor"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(project(":module-api"))
}