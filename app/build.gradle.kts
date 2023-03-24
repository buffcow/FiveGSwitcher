@file:Suppress("UnstableApiUsage")

import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.android")
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
        versionCode = 10
        versionName = "2.0"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly(files("libs/telephony.jar"))
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.1.8")
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("com.highcapable.yukihookapi:api:1.1.8")
}
