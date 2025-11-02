plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// Function to extract version from git tag
fun getVersionFromGit(): Pair<Int, String> {
    return try {
        val gitTag = Runtime.getRuntime()
            .exec("git describe --tags --abbrev=0")
            .inputStream.bufferedReader().readText().trim()
        
        // Parse version tag (e.g., "v1.2.3" or "1.2.3")
        val versionMatch = Regex("""v?(\d+)\.(\d+)\.(\d+)""").find(gitTag)
        if (versionMatch != null) {
            val (major, minor, patch) = versionMatch.destructured
            // Calculate versionCode as major*10000 + minor*100 + patch
            val versionCode = major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
            val versionName = "$major.$minor.$patch"
            Pair(versionCode, versionName)
        } else {
            // Fallback if no valid tag found
            Pair(1, "0.1.0")
        }
    } catch (e: Exception) {
        // Fallback to default version if git command fails
        println("Warning: Could not read git tag, using default version. Error: ${e.message}")
        Pair(1, "0.1.0")
    }
}

val (appVersionCode, appVersionName) = getVersionFromGit()

android {
    namespace = "com.nfcbumber"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nfcbumber"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Expose version name in BuildConfig for dynamic version display
        buildConfigField("String", "VERSION_NAME", "\"${appVersionName}\"")
    }

    signingConfigs {
        create("release") {
            // ⚠️ SECURITY WARNING: Using debug keystore for development/testing only
            // The debug keystore uses publicly known credentials and should NOT be used
            // for production releases or apps distributed to untrusted users.
            //
            // This configuration enables APK installation without certificate errors,
            // which is useful for:
            // - Development builds shared with testers
            // - Personal use builds
            // - Open source projects without sensitive data
            // 
            // For production releases with proper signing:
            // 1. Create a release keystore using Android Studio or keytool
            // 2. Set environment variables or use gradle.properties:
            //    - RELEASE_STORE_FILE=/path/to/release.keystore
            //    - RELEASE_STORE_PASSWORD=your_password
            //    - RELEASE_KEY_ALIAS=your_alias
            //    - RELEASE_KEY_PASSWORD=your_key_password
            val debugKeystorePath = "${System.getProperty("user.home")}/.android/debug.keystore"
            val debugKeystoreFile = file(debugKeystorePath)
            
            if (debugKeystoreFile.exists()) {
                storeFile = debugKeystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                // If debug keystore doesn't exist, signing will fail at build time
                // This is intentional to prevent unsigned APKs
                logger.warn("Debug keystore not found at: $debugKeystorePath")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only apply signing config if keystore exists
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile?.exists() == true) {
                signingConfig = releaseSigningConfig
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    
    // Configure APK output naming for better compatibility with Obtainium
    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val variantName = name
            val versionName = defaultConfig.versionName
            val appName = "Wolle"
            
            if (variantName.contains("release")) {
                outputImpl.outputFileName = "${appName}-${versionName}.apk"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Modules
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":presentation"))

    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.bundles.lifecycle)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation("androidx.fragment:fragment:1.8.9")
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}
