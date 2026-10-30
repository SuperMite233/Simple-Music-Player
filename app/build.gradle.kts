import java.util.Properties

plugins {
    id("com.android.application")
}

val releaseVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val releaseChannel = providers.gradleProperty("releaseChannel").orElse("beta").get()
require(releaseChannel in listOf("stable", "beta")) { "releaseChannel must be stable or beta" }
val smpVersion = if (releaseChannel == "stable") releaseVersion.getProperty("stableVersion") else
    "beta-${releaseVersion.getProperty("baseStableVersion")}-${releaseVersion.getProperty("refactorDate")}"
val signingPropertiesFile = file(providers.environmentVariable("SMP_SIGNING_PROPERTIES").orElse(
    "${System.getProperty("user.home")}/AppData/Local/SMP/signing/signing.properties").get())
val privateSigning = Properties().apply { if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use { load(it) } }

android {
    namespace = "com.supermite.smp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.supermite.smp"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = smpVersion
        buildConfigField("String", "RELEASE_BUILT_AT", "\"${releaseVersion.getProperty("releaseBuiltAt")}\"")
        buildConfigField("String", "RELEASE_CHANNEL", "\"$releaseChannel\"")
        testInstrumentationRunner = "com.supermite.smp.VisualAuditInstrumentation"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { buildConfig = true }
    signingConfigs {
        if (signingPropertiesFile.isFile) create("smpRelease") {
            storeFile = file(privateSigning.getProperty("storeFile"))
            storePassword = privateSigning.getProperty("storePassword")
            keyAlias = privateSigning.getProperty("keyAlias")
            keyPassword = privateSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug" }
        getByName("release") {
            signingConfig = signingConfigs.findByName("smpRelease")
            isMinifyEnabled = false
        }
    }
}

val verifyReleaseSigning by tasks.registering {
    doLast {
        require(signingPropertiesFile.isFile) { "Configure SMP_SIGNING_PROPERTIES with an external signing.properties before publishing" }
        require(file(privateSigning.getProperty("storeFile")).isFile) { "Release signing keystore does not exist" }
    }
}
tasks.matching { it.name == "packageRelease" }.configureEach { dependsOn(verifyReleaseSigning) }

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
}
