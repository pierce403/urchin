import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Sync

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("kotlin-kapt")
}

android {
  namespace = "guru.urchin"
  compileSdk = 35
  ndkVersion = "27.2.12479018"

  defaultConfig {
    applicationId = "guru.urchin"
    minSdk = 24
    targetSdk = 35
    versionCode = 5
    versionName = "0.2.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }

    externalNativeBuild {
      cmake {
        cFlags("-O2", "-fPIC")
        arguments("-DANDROID_STL=none")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    viewBinding = true
  }

  sourceSets {
    getByName("debug").assets.srcDir(layout.buildDirectory.dir("generated/rtl433Assets/debug"))
    getByName("release").assets.srcDir(layout.buildDirectory.dir("generated/rtl433Assets/release"))
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
}

val lifecycleVersion = "2.7.0"
val roomVersion = "2.6.1"
val rtl433AssetAbis = listOf("arm64-v8a", "x86_64")

fun String.capitalized(): String =
  replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

fun registerRtl433AssetTask(variantName: String) {
  val variantCapitalized = variantName.capitalized()
  val outputDir = layout.buildDirectory.dir("generated/rtl433Assets/$variantName")
  val cxxDir = layout.buildDirectory.dir("intermediates/cxx/$variantCapitalized")

  val stageTask = tasks.register<Sync>("stage${variantCapitalized}Rtl433Assets") {
    dependsOn(rtl433AssetAbis.map { "buildCMake${variantCapitalized}[$it]" })
    from(cxxDir) {
      rtl433AssetAbis.forEach { abi ->
        include("**/obj/$abi/rtl_433")
      }
      eachFile {
        val objIndex = relativePath.segments.indexOf("obj")
        require(objIndex >= 0 && objIndex + 2 < relativePath.segments.size) {
          "Unexpected rtl_433 asset path: $relativePath"
        }
        val abi = relativePath.segments[objIndex + 1]
        relativePath = RelativePath(true, "sdr-bin", abi, "rtl_433")
      }
      includeEmptyDirs = false
    }
    into(outputDir)
    doLast {
      val missing = rtl433AssetAbis.filterNot { abi ->
        outputDir.get().file("sdr-bin/$abi/rtl_433").asFile.exists()
      }
      require(missing.isEmpty()) {
        "Missing packaged rtl_433 asset(s) for $variantName: ${missing.joinToString()}"
      }
    }
  }

  tasks.matching { it.name == "merge${variantCapitalized}Assets" }.configureEach {
    dependsOn(stageTask)
  }
}

registerRtl433AssetTask("debug")
registerRtl433AssetTask("release")

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.activity:activity-ktx:1.9.0")

  implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")

  implementation("androidx.room:room-runtime:$roomVersion")
  implementation("androidx.room:room-ktx:$roomVersion")
  kapt("androidx.room:room-compiler:$roomVersion")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20240303")
  androidTestImplementation("androidx.test:core:1.6.1")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
  androidTestImplementation("androidx.room:room-testing:$roomVersion")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
