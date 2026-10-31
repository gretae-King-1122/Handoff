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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":module-api"))
    implementation(project(":module-rules"))
    implementation(libs.okhttp)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    // Match the existing UI test runtime; older transitive ASM cannot read the local JDK 26 classes.
    testImplementation(libs.asm)
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.commons)
    testImplementation(libs.asm.util)
    testImplementation(libs.asm.analysis)
}
