// 根构建脚本：只声明插件版本，不 apply
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Keep source text UTF-8 even when a Windows Gradle daemon needs a GBK process encoding for paths.
subprojects {
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
}
