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

        vectorDrawables {
            useSupportLibrary = true
        }

        multiDexEnabled = true

        val zhipuApiKey = project.findProperty("ZHIPU_API_KEY") as? String
            ?: throw GradleException("ZHIPU_API_KEY is not defined in gradle.properties. Please check the file.")
        buildConfigField("String", "ZHIPU_API_KEY", "\"$zhipuApiKey\"")
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
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/DEPENDENCIES"
        )
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.lifecycle" -> useVersion(libs.versions.lifecycle.runtime.get())
            "androidx.fragment" -> useVersion(libs.versions.fragment.testing.get())
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    implementation(libs.org.json)
    implementation(libs.lifecycle.runtime)
    implementation(libs.core.splashscreen)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.rules)
    debugImplementation(libs.fragment.testing)
}
