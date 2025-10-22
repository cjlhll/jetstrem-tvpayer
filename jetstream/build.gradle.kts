/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.google.jetstream"
    // Needed for latest androidx snapshot build
    compileSdk = 35

    defaultConfig {
        applicationId = "com.google.jetstream"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // ASSRT API token. Configure via gradle.properties: ASSRT_TOKEN=your_token
        val assrtToken = project.findProperty("ASSRT_TOKEN") as String? ?: ""
        buildConfigField("String", "ASSRT_TOKEN", "\"$assrtToken\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            // 如需混淆，请恢复下方 proguardFiles 并解决 R8 冲突
            // proguardFiles(
            //     getDefaultProguardFile("proguard-android-optimize.txt"),
            //     "proguard-rules.pro"
            // )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 仅生成 armeabi-v7a 安装包，适配老旧/32位 Android TV 设备
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // 移除可选的 androidx.graphics.path 原生库，避免 ABI 不匹配导致不可安装
            excludes += listOf("**/libandroidx.graphics.path.so")
        }
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    // Compose UI core for AndroidView interop
    implementation("androidx.compose.ui:ui")
    implementation(libs.androidx.compose.ui.tooling.preview)

    // extra material icons
    implementation(libs.androidx.material.icons.extended)

    // Material components optimized for TV apps
    implementation(libs.androidx.tv.material)

    // Material3 components for input fields
    implementation("androidx.compose.material3:material3")

    // ViewModel in Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose Navigation
    implementation(libs.androidx.navigation.compose)

    // Coil
    implementation(libs.coil.compose)

    // JSON parser
    implementation(libs.kotlinx.serialization)

    // OkHttp for ASSRT client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    // Core UI widgets (SubtitleView) required for native subtitle rendering
    implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")

    // SplashScreen
    implementation(libs.androidx.core.splashscreen)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Baseline profile installer
    implementation(libs.androidx.profileinstaller)

    // WebDAV client
    implementation(libs.sardine.android)

    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Compose Previews
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Testing
    testImplementation("junit:junit:4.13.2")

    // For baseline profile generation
    baselineProfile(project(":benchmark"))
}
