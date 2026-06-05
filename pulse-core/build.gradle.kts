plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.vanniktech.publish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

group = "io.github.vhuthu"
version = "0.1.2"

mavenPublishing {

//    publishToMavenCentral()
//    signAllPublications()

    pom {
        name.set("PulseRealtime Core")
        description.set("Modular, lifecycle-aware, coroutine-first WebSocket SDK for Android — core module")
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
    implementation(project(":pulse-logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.org.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}