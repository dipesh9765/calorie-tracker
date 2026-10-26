plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.calorietracker.app"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.calorietracker.app"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
  }

  // Signing secrets come from the environment (CI) or ~/.gradle/gradle.properties, never source.
  val keystoreFile = file("${rootDir}/release-key.jks")
  val keystorePassword =
    providers.environmentVariable("KEYSTORE_PASSWORD").orNull
      ?: providers.gradleProperty("keystorePassword").orNull
  val keyAliasName =
    providers.environmentVariable("KEY_ALIAS").orNull
      ?: providers.gradleProperty("keyAlias").orNull
      ?: "calorietracker"
  val keyPasswordValue =
    providers.environmentVariable("KEY_PASSWORD").orNull
      ?: providers.gradleProperty("keyPassword").orNull
      ?: keystorePassword
  val canSign = keystoreFile.exists() && keystorePassword != null
  signingConfigs {
    if (canSign) {
      create("release") {
        storeFile = keystoreFile
        storePassword = keystorePassword
        keyAlias = keyAliasName
        keyPassword = keyPasswordValue
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }

  buildTypes {
    debug {
      if (canSign) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    release {
      isMinifyEnabled = false
      if (canSign) {
        signingConfig = signingConfigs.getByName("release")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.material.icons.extended)

  // Room
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  // Generates AppDatabase_Impl / MealDao_Impl; without it Room fails at runtime.
  ksp(libs.androidx.room.compiler)

  // WorkManager Background Tasks
  implementation(libs.androidx.work.runtime)

  // Networking
  implementation(libs.okhttp)
  implementation(libs.gson)
  implementation(libs.kotlinx.coroutines.android)

  // Testing
  testImplementation(libs.junit)

  debugImplementation(libs.androidx.ui.tooling)
}
