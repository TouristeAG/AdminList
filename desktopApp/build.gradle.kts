import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val versionFile = rootProject.file("version.json")
val desktopPackageVersion = if (versionFile.exists()) {
    @Suppress("UNCHECKED_CAST")
    val props = groovy.json.JsonSlurper().parse(versionFile) as Map<String, Any?>
    props["latestVersionName"] as? String ?: "1.0.0"
} else {
    "1.0.0"
}

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
    resolutionStrategy {
        force(
            "io.grpc:grpc-api:1.68.2",
            "io.grpc:grpc-core:1.68.2",
            "io.grpc:grpc-context:1.68.2",
            "io.grpc:grpc-stub:1.68.2",
            "io.grpc:grpc-okhttp:1.68.2",
            "io.grpc:grpc-protobuf-lite:1.68.2",
            "io.grpc:grpc-util:1.68.2",
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.eventmanager.app.desktop.MainKt"
        jvmArgs("-Dapple.awt.application.appearance=system")
        nativeDistributions {
            // Keep installer output out of any previously locked Windows package folders
            // (e.g. an open NoctuList-*.exe holding build/compose/binaries/main-release/exe).
            outputBaseDir.set(project.layout.buildDirectory.dir("compose/packaged"))
            jvmArgs("-Dapple.awt.application.appearance=system")
            // jlink-trimmed runtime: JNA (jnasmartcardio / WinSCard) needs Unsafe;
            // PC/SC API may come from the JDK module rather than only the shaded jar.
            modules("jdk.unsupported", "java.smartcardio")
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
            packageVersion = desktopPackageVersion
            description = "NoctuList — Guest list and volunteer management"
            vendor = "Collectif Nocturne"
            // Packagers require OS-native formats (PNG is ignored → default Java icon).
            // Source + generators live under desktopApp/icons/.
            macOS {
                bundleID = "com.eventmanager.app.desktop"
                iconFile.set(project.file("icons/icon.icns"))
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
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "NoctuList"
                upgradeUuid = "8f4e2b1a-9c3d-4e5f-a6b7-c8d9e0f1a2b3"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
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
