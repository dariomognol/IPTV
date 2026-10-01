plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.iptv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.iptv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Componenti grafici Material 3
    implementation("androidx.compose.material3:material3")

    // Librerie HTTP Xtream API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Strumenti di debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Media3 ExoPlayer per riproduzione video HLS e MP4
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.0") // O versione più recente
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
        // Aggiungi questa riga per Coil (per caricare le icone dei canali)
        implementation("io.coil-kt:coil-compose:2.6.0")
}

