// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.vanniktech.publish) apply false
}

val localProperties = java.util.Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

listOf(
    "mavenCentralUsername",
    "mavenCentralPassword",
    "signing.keyId",
    "signing.password",
    "signing.secretKeyRingFile",
).forEach { key ->
    localProperties.getProperty(key)?.let { ext.set(key, it) }
}