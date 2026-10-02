plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.adpluga"
// The AdPluga SDK version plus the adapter release.
version = "0.7.6.0"

android {
    namespace = "com.adpluga.mediation.levelplay"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "ADAPTER_VERSION", "\"${project.version}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":adpluga"))
    // The host app already ships the LevelPlay SDK; the adapter never
    // picks its version. Compiled against 8.4.0, the oldest release it
    // supports, so it cannot reach for newer API.
    compileOnly("com.unity3d.ads-mediation:mediation-sdk:8.4.0")
    implementation("androidx.annotation:annotation:1.9.1")

    testImplementation("com.unity3d.ads-mediation:mediation-sdk:8.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
