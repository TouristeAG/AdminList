import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.eventmanager.app.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "NoctuList"
            packageVersion = "1.0.3"
            description = "NoctuList — Guest list and volunteer management"
            vendor = "Collectif Nocturne"
            val desktopIcon = project.file("../app/src/main/res/mipmap-xxxhdpi/ic_launcher_violet.png")
            macOS {
                bundleID = "com.eventmanager.app.desktop"
                iconFile.set(desktopIcon)
            }
            windows {
                iconFile.set(desktopIcon)
                menuGroup = "NoctuList"
                upgradeUuid = "8f4e2b1a-9c3d-4e5f-a6b7-c8d9e0f1a2b3"
            }
        }
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
