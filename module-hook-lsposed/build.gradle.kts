plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.handoff.hook"
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
    compileOnly(libs.libxposed.api)
    api(project(":module-api"))
    implementation(kotlin("stdlib"))
}