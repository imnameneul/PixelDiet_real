// build.gradle.kts (Module: app)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.pixeldiet" // ⭐️ 패키지 이름
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pixeldiet" // ⭐️ 패키지 이름
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

    buildFeatures {
        compose = true // ⭐️ Compose 활성화
    }

    // ⭐️ [수정됨] Kotlin 2.0 플러그인과 충돌하므로 이 블록을 삭제합니다.
    /*
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    */

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("com.google.android.material:material:1.12.0")
    // 기본 Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // --- MVVM (ViewModel, LiveData) ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- Jetpack Compose 의존성 ---
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 🔑 ArrowForwardIos 같은 Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")

    // 🔑 LiveData -> observeAsState() 를 위한 확장 함수
    implementation("androidx.compose.runtime:runtime-livedata")

    // viewModel() 등 Compose + Lifecycle 연동
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // --- Compose Navigation ---
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- 3rd Party (차트, 캘린더) ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.github.prolificinteractive:material-calendarview:2.0.1")

    // ⭐️ Coil 이미지 로딩 라이브러리 추가
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ⭐️ [신규] WorkManager(백그라운드 작업) 라이브러리
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx:22.3.0")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")


    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")

    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("com.google.firebase:firebase-auth-ktx:22.3.0")
    implementation("com.google.firebase:firebase-firestore-ktx:24.3.0")
    implementation("com.google.firebase:firebase-analytics-ktx:21.3.0")
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")

}