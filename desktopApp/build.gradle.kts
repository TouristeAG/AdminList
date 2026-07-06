import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
}

compose.desktop {
    application {
        mainClass = "com.eventmanager.app.desktop.MainKt"
        jvmArgs("-Dapple.awt.application.appearance=system")
        nativeDistributions {
            jvmArgs("-Dapple.awt.application.appearance=system")
            val packageFormats = buildList {
                add(TargetFormat.Dmg)
                add(TargetFormat.Msi)
                add(TargetFormat.Exe)
                if (System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) {
                    add(TargetFormat.Deb)
                    add(TargetFormat.AppImage)
                }
            }
            targetFormats(*packageFormats.toTypedArray())
            packageName = "NoctuList"
            packageVersion = "1.0.3"
            description = "NoctuList — Guest list and volunteer management"
            vendor = "Collectif Nocturne"
            val desktopIcon = project.file("../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
            macOS {
                bundleID = "com.eventmanager.app.desktop"
                iconFile.set(desktopIcon)
                runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>NoctuList uses the camera to scan QR codes for admin login and door check-in.</string>
                        <key>NSRequiresAquaSystemAppearance</key>
                        <false/>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(desktopIcon)
                menuGroup = "NoctuList"
                upgradeUuid = "8f4e2b1a-9c3d-4e5f-a6b7-c8d9e0f1a2b3"
            }
            linux {
                iconFile.set(desktopIcon)
                debMaintainer = "Collectif Nocturne <contact@collectif-nocturne.ch>"
                menuGroup = "Office"
                appCategory = "Office"
            }
        }
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
