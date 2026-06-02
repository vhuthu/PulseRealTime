plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

android {
    namespace = "com.vhuthu.pulse_android"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }
}

group = "io.github.vhuthu"
version = "0.1.0"

mavenPublishing {
//
//    publishToMavenCentral()
//    signAllPublications()

    pom {
        name.set("PulseRealtime Android")
        description.set("Android lifecycle binding for the PulseRealtime WebSocket SDK")
        url.set("https://github.com/vhuthu/PulseRealTime")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        developers {
            developer {
                id.set("vhuthu")
                name.set("Vhuthu Kwinda")
                email.set("vhuthukwinda67@gmail.com")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/vhuthu/PulseRealTime.git")
            developerConnection.set("scm:git:ssh://github.com/vhuthu/PulseRealTime.git")
            url.set("https://github.com/vhuthu/PulseRealTime")
        }
    }
}

dependencies {
    implementation(project(":pulse-core"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
}