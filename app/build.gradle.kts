plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.fitnesee"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fitnesee"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            when (requested.group) {
                "androidx.lifecycle" -> useVersion("2.8.4")
                "androidx.fragment" -> useVersion("1.8.4")
            }
        }
    }
}

dependencies {
    implementation(libs.cardview)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.org.json)
    implementation(libs.lifecycle.runtime)
    implementation(libs.core.splashscreen)
    implementation(libs.datastore.preferences)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.fragment.testing)
    androidTestImplementation(libs.test.rules)
}