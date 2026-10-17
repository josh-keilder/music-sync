plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.joshkeilder.musicsync"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.joshkeilder.musicsync"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    
    buildFeatures {
        compose = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
}