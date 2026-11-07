plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.simpletools.nfcreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simpletools.nfcreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signature de publication : uniquement si un keystore est fourni par l'environnement (jamais dans le dépôt).
    // Sans ces variables, l'AAB release est produit NON signé : aucune signature n'est inventée.
    val releaseKeystore = System.getenv("SIMPLETOOLS_KEYSTORE_PATH")
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SIMPLETOOLS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIMPLETOOLS_KEY_ALIAS")
                keyPassword = System.getenv("SIMPLETOOLS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        lintConfig = file("lint.xml")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { test ->
                // Optionnel : jar Robolectric android-all prétéléchargé (environnements à réseau limité).
                System.getenv("ROBOLECTRIC_DEPENDENCY_DIR")?.let { dir ->
                    test.systemProperty("robolectric.dependency.dir", dir)
                    test.systemProperty("robolectric.offline", "true")
                }
            }
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.billing.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.json)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
