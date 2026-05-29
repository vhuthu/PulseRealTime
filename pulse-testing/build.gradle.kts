//plugins {
//    alias(libs.plugins.jetbrains.kotlin.jvm)
//}
//
//java {
//    sourceCompatibility = JavaVersion.VERSION_11
//    targetCompatibility = JavaVersion.VERSION_11
//}
//
//kotlin {
//    compilerOptions {
//        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
//    }
//}
//
//dependencies {
//    implementation(project(":pulse-core"))
//    implementation(libs.junit.junit)
//    implementation(libs.kotlinx.coroutines.core)
//
//    testImplementation(libs.junit)
//    testImplementation(libs.kotlinx.coroutines.test)
//}

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }

    sourceSets {
        test {
            kotlin.srcDir("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(project(":pulse-core"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}