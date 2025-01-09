plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ydh.translator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ydh.translator"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX 및 Compose 라이브러리
    implementation(libs.androidx.core.ktx) // 최신 버전 확인 및 적용
    implementation(libs.androidx.lifecycle.runtime.ktx) // 최신 버전 확인 및 적용
    implementation(libs.androidx.activity.compose) // 최신 버전 확인 및 적용
    implementation(platform(libs.androidx.compose.bom)) // 최신 버전 확인 및 적용
    implementation(libs.androidx.ui) // 최신 버전 확인 및 적용
    implementation(libs.androidx.ui.graphics) // 최신 버전 확인 및 적용
    implementation(libs.androidx.ui.tooling.preview) // 최신 버전 확인 및 적용
    implementation(libs.androidx.material3) // 최신 버전 확인 및 적용
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("io.coil-kt:coil-gif:2.4.0")


    // OkHttp 및 JSON 라이브러리
    implementation("com.squareup.okhttp3:okhttp:4.11.0") // 최신 버전 확인 및 적용
    implementation("org.json:json:20230227") // 최신 버전 확인 및 적용

    // CameraX 라이브러리
    val cameraVersion = "1.5.0-alpha04" // 최신 버전 확인 및 적용
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")


    // ML Kit 텍스트 인식 라이브러리
    implementation("com.google.mlkit:text-recognition:16.0.0") // 최신 버전 확인 및 적용
    implementation ("com.google.mlkit:text-recognition-korean:16.0.0")

    // 테스트 라이브러리
    testImplementation(libs.junit) // 최신 버전 확인 및 적용
    androidTestImplementation(libs.androidx.junit) // 최신 버전 확인 및 적용
    androidTestImplementation(libs.androidx.espresso.core) // 최신 버전 확인 및 적용
    androidTestImplementation(platform(libs.androidx.compose.bom)) // 최신 버전 확인 및 적용
    androidTestImplementation(libs.androidx.ui.test.junit4) // 최신 버전 확인 및 적용
    debugImplementation(libs.androidx.ui.tooling) // 최신 버전 확인 및 적용
    debugImplementation(libs.androidx.ui.test.manifest) // 최신 버전 확인 및 적용
}
