plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.handoff.storage"
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
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    // Robolectric's instrumentation must understand the JDK 26 runtime used by local builds.
    testImplementation(libs.asm)
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.commons)
    testImplementation(libs.asm.util)
    testImplementation(libs.asm.analysis)
}
