import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.multiplatform.settings)
                implementation(libs.jnanoid)
                implementation(libs.gson)
                implementation(libs.google.api.client)
                implementation(libs.google.api.client.gson)
                implementation(libs.google.sheets)
                implementation(libs.google.gmail)
                implementation(libs.google.auth)
                implementation(libs.google.auth.credentials)
                implementation(libs.room.runtime)
                implementation(libs.room.ktx)
                implementation(libs.sqlite.bundled)
                implementation("androidx.sqlite:sqlite:2.5.0")
                implementation(libs.zxing.core)
                implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
                implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
            }
        }
        val androidMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(files("${rootProject.projectDir}/app/libs/smartcardio-0.1.7.aar"))
                implementation(files("${rootProject.projectDir}/app/libs/acssmcio-0.6.2.aar"))
                implementation(libs.google.api.client.android)
                implementation("androidx.biometric:biometric:1.1.0")
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.fragment:fragment-ktx:1.8.5")
                implementation("com.journeyapps:zxing-android-embedded:4.3.0")
                implementation("io.coil-kt:coil-compose:2.5.0")
                implementation("com.patrykandpatrick.vico:compose:2.0.0-alpha.20")
                implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.20")
                implementation("com.google.android.gms:play-services-auth:20.7.0")
                implementation("androidx.webkit:webkit:1.12.1")
                // Mirror app POI setup — avoid duplicate stax-api / xmlbeans in merged dex
                implementation("org.apache.poi:poi:3.15") {
                    exclude(group = "org.apache.logging.log4j", module = "log4j-api")
                    exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
                }
                implementation("org.apache.poi:poi-ooxml:3.15") {
                    exclude(group = "org.apache.logging.log4j", module = "log4j-api")
                    exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
                    isTransitive = false
                }
                implementation("org.apache.poi:poi-ooxml-schemas:3.15") {
                    exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
                }
                implementation("javax.xml.stream:stax-api:1.0-2")
                implementation("com.fasterxml.woodstox:woodstox-core:6.5.1") {
                    exclude(group = "javax.xml.stream", module = "stax-api")
                    exclude(group = "stax", module = "stax-api")
                }
                implementation("org.apache.xmlbeans:xmlbeans:2.5.0") {
                    exclude(group = "javax.xml.stream", module = "stax-api")
                    exclude(group = "stax", module = "stax-api")
                }
            }
        }
        val desktopMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("com.github.librepdf:openpdf:1.3.30")
                implementation(compose.desktop.currentOs)
                implementation(files("${rootProject.projectDir}/shared/libs/smartcardio-api-0.1.7.jar"))
                implementation(libs.apdu4j.jnasmartcardio)
                implementation(libs.jna)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqlite.bundled)
                implementation(libs.webcam.capture)
                implementation(libs.webcam.capture.driver.native)
                implementation(libs.poi.ooxml)
                implementation(libs.zxing.javase)
                implementation("com.google.oauth-client:google-oauth-client-jetty:1.36.0")
            }
        }
        val commonTest by getting
    }
}

// Read version from version.json for shared BuildConfig-like constants
val versionFile = rootProject.file("version.json")
val versionName = if (versionFile.exists()) {
    val props = groovy.json.JsonSlurper().parse(versionFile) as Map<*, *>
    props["latestVersionName"] as String
} else "1.0.0"

configurations.matching {
    it.name.contains("android", ignoreCase = true)
}.configureEach {
    exclude(group = "commons-logging", module = "commons-logging")
    resolutionStrategy {
        force("org.apache.xmlbeans:xmlbeans:2.5.0")
        force("javax.xml.stream:stax-api:1.0-2")
        eachDependency {
            if (requested.group == "org.apache.xmlbeans" && requested.name == "xmlbeans") {
                useVersion("2.5.0")
            }
            if (requested.group == "javax.xml.stream" && requested.name == "stax-api") {
                useVersion("1.0-2")
            }
        }
    }
}

android {
    namespace = "com.eventmanager.app"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://raw.githubusercontent.com/leonardomondada/NoctuList/main/update-manifest.json\"")
        buildConfigField("String", "UPDATE_FALLBACK_STORE_URL", "\"https://play.google.com/store/apps/details?id=com.eventmanager.app\"")
    }
    sourceSets["main"].res.srcDirs(
        "src/androidMain/res",
        "${rootProject.projectDir}/app/src/main/res"
    )
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

val desktopBiometricResourcesDir = layout.projectDirectory.dir("src/desktopMain/resources")

tasks.register("prepareDesktopBiometricNativeLibs") {
    val biometrikJar = layout.buildDirectory.file("biometrik-native/biometrik-jvm-1.0.2.jar")
    outputs.dir(desktopBiometricResourcesDir)

    doLast {
        desktopBiometricResourcesDir.asFile.mkdirs()
        val jar = biometrikJar.get().asFile
        if (!jar.exists()) {
            jar.parentFile.mkdirs()
            URI("https://repo1.maven.org/maven2/io/github/n7ghtm4r3/biometrik-jvm/1.0.2/biometrik-jvm-1.0.2.jar")
                .toURL()
                .openStream()
                .use { input -> jar.outputStream().use { output -> input.copyTo(output) } }
        }
        copy {
            from(zipTree(jar)) {
                include("LocalAuthenticationEngine.dylib", "WindowsHelloEngine.dll", "LinuxPolkitEngine.so")
            }
            into(desktopBiometricResourcesDir)
        }
        val macSource = layout.projectDirectory.file("nativeengines/macos/LocalAuthenticationEngine.m").asFile
        if (macSource.exists() && System.getProperty("os.name").startsWith("Mac OS")) {
            val dylib = desktopBiometricResourcesDir.file("LocalAuthenticationEngine.dylib").asFile
            project.exec {
                commandLine(
                    "clang",
                    "-dynamiclib",
                    "-framework", "LocalAuthentication",
                    "-framework", "Foundation",
                    "-o", dylib.absolutePath,
                    macSource.absolutePath
                )
            }
        }
    }
}

tasks.named("compileKotlinDesktop") {
    dependsOn("prepareDesktopBiometricNativeLibs")
}

tasks.matching { it.name == "desktopProcessResources" }.configureEach {
    dependsOn("prepareDesktopBiometricNativeLibs")
}

configurations.matching { it.name.contains("desktop", ignoreCase = true) }.configureEach {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.eventmanager.app.resources"
}

kotlin.sourceSets.all {
    languageSettings {
        optIn("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn("androidx.compose.animation.ExperimentalAnimationApi")
    }
}
