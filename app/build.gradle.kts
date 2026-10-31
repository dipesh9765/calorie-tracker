plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.kover)
}

android {
  namespace = "com.calorietracker.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.calorietracker.app"
    minSdk = 26
    targetSdk = 35
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
      isMinifyEnabled = true
      isShrinkResources = true
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
  testOptions {
    unitTests.isReturnDefaultValues = true
    unitTests.isIncludeAndroidResources = true
  }
  buildFeatures { compose = true }
  lint {
    abortOnError = true
    checkDependencies = false
    lintConfig = file("lint.xml")
  }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

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

  // Dependency injection
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)

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
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.robolectric)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.ui.test.junit4)
  testImplementation(libs.androidx.test.core)

  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

kover {
  reports {
    filters {
      excludes {
        // Compose UI, generated Room/KSP code and DI wiring are exercised by instrumented/UI tests,
        // not JVM unit tests.
        packages("com.calorietracker.app.ui.*")
        classes(
          "*_Impl",
          "*_Impl\$*",
          "*ComposableSingletons*",
          "*.BuildConfig",
          "hilt_aggregated_deps.*"
        )
      }
    }
    verify { rule("Minimum line coverage") { minBound(60) } }
  }
}
