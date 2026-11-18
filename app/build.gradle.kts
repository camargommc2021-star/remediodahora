plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.meuapp.lembretes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meuapp.lembretes"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "5"
    }

    // A chave fica criptografada no repositório (app/assinatura.jks.enc).
    // O GitHub abre com o Secret ASSINATURA_SENHA e passa o caminho e a senha por variáveis.
    val arquivoChave = System.getenv("ASSINATURA_ARQUIVO")
    val senhaChave = System.getenv("ASSINATURA_SENHA")

    signingConfigs {
        create("fixa") {
            if (arquivoChave != null && senhaChave != null) {
                storeFile = file(arquivoChave)
                storePassword = senhaChave
                keyAlias = "remedio"
                keyPassword = senhaChave
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("fixa")
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Ler receitas em PDF no próprio celular
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
