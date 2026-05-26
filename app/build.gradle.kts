plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ... your standard plugins
    id("com.google.devtools.ksp")
    alias(libs.plugins.dagger.hilt.android)
}

android {
    namespace = "com.natighajiyev.alphametricsanalyticssdk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.natighajiyev.alphametricsanalyticssdk"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":analytics-core"))
    implementation(project(":analytics-di-hilt"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Hilt Core Android Runtime
    implementation(libs.dagger.hilt.android)

    // Process Hilt code generation using KSP instead of Kapt
    ksp(libs.dagger.hilt.compiler)

    // Core Room Runtime dependencies
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}