plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

android {
    namespace = "io.github.vhuthu.pulse"
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
version = "0.1.1"

mavenPublishing {

//    publishToMavenCentral()
//    signAllPublications()

    pom {
        name.set("PulseRealtime")
        description.set(
            "Modular, lifecycle-aware, coroutine-first WebSocket SDK for Android. " +
                    "Single dependency that includes core, Android lifecycle binding, and logging."
        )
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
    api(project(":pulse-core"))
    api(project(":pulse-android"))
    api(project(":pulse-logging"))
}