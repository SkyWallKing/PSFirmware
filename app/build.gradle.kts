plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.etawen.psfirmware"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.etawen.psfirmware"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Assinado com a chave de debug para poder instalar direto no celular.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Os idiomas são trocados em tempo de execução (notificação), então todos precisam estar no APK.
    bundle {
        language {
            enableSplit = false
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
