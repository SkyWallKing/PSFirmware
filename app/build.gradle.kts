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
        versionCode = 4
        versionName = "1.2.0"
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

// Toda build de release deixa a APK na raiz como PSFirmware.apk (é ela que vai para a release do GitHub).
// Tarefa simples (e não Copy): um Copy "into" a raiz tornaria a pasta inteira saída da tarefa.
val copyReleaseApk by tasks.registering {
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val target = rootProject.layout.projectDirectory.file("PSFirmware.apk")
    inputs.file(apk)
    outputs.file(target)
    doLast { apk.get().asFile.copyTo(target.asFile, overwrite = true) }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseApk) }
