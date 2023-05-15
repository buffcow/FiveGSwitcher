@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
    kotlin("android")
    id("com.android.application")
    id("com.google.devtools.ksp")
}

val prop by lazy {
    Properties().apply {
        load(rootProject.file("local.properties").inputStream())
    }
}

android {
    compileSdk = 33
    namespace = "com.qingyu.mi5g"

    defaultConfig {
        minSdk = 24
        targetSdk = 33
        versionCode = 14
        versionName = "2.0.4"
        applicationId = android.namespace
    }

    signingConfigs {
        create("release") {
            enableV3Signing = true
            storeFile = file(prop.getProperty("sign.storeFile"))
            keyAlias = prop.getProperty("sign.keyAlias")
            keyPassword = prop.getProperty("sign.storePassword")
            storePassword = prop.getProperty("sign.storePassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions += listOf("mode")
    productFlavors {
        listOf("tile", "panel").forEach { create(it) }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    applicationVariants.all {
        outputs.all {
            val appName = rootProject.name
            val flavor = name.split("-").first()
            val newApkName = "$appName-${versionName}_$versionCode-$flavor.apk"
            (this as BaseVariantOutputImpl).outputFileName = newApkName
        }
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly(files("libs/telephony.jar"))
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.1.11")
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("com.highcapable.yukihookapi:api:1.1.11")
}
