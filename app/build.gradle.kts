import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * 版本号与发行签名。
 *
 * - 版本号默认写在下面；发布流水线按标签 `vX.Y.Z` 用 `-PversionName` / `-PversionCode` 覆盖。
 * - 发行签名材料来自 `keystore.properties`（本地，不入库）或同名环境变量（CI Secrets）。
 *   两者都没有时发行包回退到调试签名，保证任意分支都能产出可安装的包。
 */
val releaseVersionName = providers.gradleProperty("versionName").orNull
val releaseVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull()

val signingProperties = Properties()
rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.inputStream()
    ?.use { signingProperties.load(it) }

fun signingValue(property: String, environment: String): String? =
    signingProperties.getProperty(property) ?: System.getenv(environment)

val signingStorePath = signingValue("storeFile", "ZEROPLAYER_STORE_FILE")
val signingStorePassword = signingValue("storePassword", "ZEROPLAYER_STORE_PASSWORD")
val signingKeyAlias = signingValue("keyAlias", "ZEROPLAYER_KEY_ALIAS")
val signingKeyPassword = signingValue("keyPassword", "ZEROPLAYER_KEY_PASSWORD")
val hasReleaseSigning = listOf(signingStorePath, signingStorePassword, signingKeyAlias, signingKeyPassword)
    .all { it != null } && rootProject.file(signingStorePath!!).exists()

android {
    namespace = "com.privateplayer.media"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.privateplayer.media"
        minSdk = 26
        targetSdk = 37
        // 默认值与标签 v1.0.13 的推导值一致（major*10000 + minor*100 + patch），避免本地安装被降级。
        versionCode = releaseVersionCode ?: 10013
        versionName = releaseVersionName ?: "1.0.13"
        ndk {
            abiFilters += "arm64-v8a"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.common)
    implementation(libs.media3.session)

    implementation(project(":core:common"))
    implementation(project(":core:design"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:storage"))
    implementation(project(":core:playback"))
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(project(":feature:home"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:importer"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Android Gradle 设备测试结束时会卸载被测应用，可能清空应用私有媒体。
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    val allowed = providers.gradleProperty("allowDestructiveConnectedTests").orNull == "true"
    doFirst {
        check(allowed) {
            "设备测试结束会卸载应用并清除私有数据。仅在可丢弃的模拟器上运行，并以 -PallowDestructiveConnectedTests=true 确认。"
        }
    }
}
