import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// Credenciais de assinatura de release lidas de keystore.properties (fora do
// controle de versão) — mesmo padrão do Match/Caronas. Sem o arquivo, o
// release cai para a chave de debug em vez de falhar.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val temKeystoreDeRelease = keystorePropertiesFile.exists()
if (temKeystoreDeRelease) {
    keystoreProperties.load(keystorePropertiesFile.inputStream().reader(Charsets.UTF_8))
}

// google-services precisa de app/google-services.json pra sequer configurar.
// Enquanto o projeto Firebase do Start não existir, o build continua
// funcionando (só o Firebase não inicializa em tempo de execução).
val temGoogleServices = file("google-services.json").exists()
if (temGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.cjstudio.start"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cjstudio.start"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    signingConfigs {
        if (temKeystoreDeRelease) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            // R8 ligado desde o início (no Caronas o Play reclamou de
            // "Ofuscação 2%"). Modelos do Firestore ficam protegidos em
            // proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            signingConfig = if (temKeystoreDeRelease) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Três apps separados na Play, mesmo código e mesmo Firebase:
    // passageiro (pede corrida), motorista (aceita corrida) e admin (painel).
    // BuildConfig.TIPO decide o fluxo de telas (ver AppTipo/SplashActivity).
    flavorDimensions += listOf("type")

    productFlavors {
        create("passageiro") {
            applicationId = "com.cjstudio.start.passageiro"
            versionName = "1.0-passageiro"
            resValue("string", "app_name", "Start")
            buildConfigField("String", "TIPO", "\"passageiro\"")
        }
        create("motorista") {
            applicationId = "com.cjstudio.start.motorista"
            versionName = "1.0-motorista"
            resValue("string", "app_name", "Start Motorista")
            buildConfigField("String", "TIPO", "\"motorista\"")
        }
        create("admin") {
            applicationId = "com.cjstudio.start.admin"
            versionName = "1.0-admin"
            resValue("string", "app_name", "Admin Start")
            buildConfigField("String", "TIPO", "\"admin\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
}

configurations.all {
    resolutionStrategy {
        // Mesmo ajuste do Match/Caronas: o compilador Kotlin embutido no AGP 9
        // é fixo numa versão; libs mais novas puxam um kotlin-stdlib que ele
        // não entende.
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
    implementation(libs.androidx.cardview)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
