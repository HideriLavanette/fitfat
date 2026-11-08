import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Версию задаёт CI из git-тега: -PversionName=0.2.0 -PversionCode=105.
// Локальные сборки получают 0.0.0-dev и код 1, поэтому любой релиз считается новее.
val appVersionName = (findProperty("versionName") as String?) ?: "0.0.0-dev"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
val updateRepo = (findProperty("updateRepo") as String?) ?: "HideriLavanette/FitFat"
val keystorePath: String? = System.getenv("KEYSTORE_FILE")
// Вход через Google: идентификатор веб-клиента OAuth из Google Cloud (не секрет, виден в любом APK).
// Пусто — кнопка «Подключить» объясняет, что вход ещё не настроен.
val googleWebClientId = (findProperty("googleWebClientId") as String?) ?: ""
// Ассистент: адрес Worker'а из server/assistant (DESIGN.md, «Ассистент»). Пусто — кнопки ассистента нет.
val assistantUrl = (findProperty("assistantUrl") as String?)?.trimEnd('/') ?: ""

kotlin {
    // expect/actual-классы (Prefs, Platform, конструктор Room) — убрать предупреждение «в бете»
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure { compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes") }
        }
    }

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // iOS: статический фреймворк для обёртки в iosApp. На Windows эти цели отключены (см. gradle.properties).
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Fitfat"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.jb.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.haze)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.material.icons.extended)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.code.scanner)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.googleid)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "app.fitfat"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.fitfat"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "ASSISTANT_URL", "\"$assistantUrl\"")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: Compose без оптимизации заметно медленнее (переключение вкладок, первые кадры экранов)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Без ключа (локальная сборка) подписываем отладочным, чтобы APK всё равно собирался.
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Room генерирует код для каждой цели отдельно
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    debugImplementation(compose.uiTooling)
    testImplementation(libs.junit)
}
