plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// versionCode = número do run do GitHub (o app compara este número com a tag da Release)
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.wrbuildhelper.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wrbuildhelper.app"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.$runNumber"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("wrbuildhelper.keystore")
            storePassword = "wrbuild123"
            keyAlias = "wrbuild"
            keyPassword = "wrbuild123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Leitura de texto da tela (OCR) feita dentro do celular, sem internet e sem Firebase
    implementation("com.google.mlkit:text-recognition:16.0.0")
}
