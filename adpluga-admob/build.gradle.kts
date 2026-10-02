plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.adpluga"
version = "0.7.6.0"

android {
    namespace = "com.adpluga.mediation.admob"
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
    // The host app already ships the Google Mobile Ads SDK; the adapter never
    // picks its version. Compiled against the oldest supported release so it
    // cannot reach for newer API; the mediation interfaces are unchanged in 25.x.
    compileOnly("com.google.android.gms:play-services-ads:24.0.0")
    implementation("androidx.annotation:annotation:1.9.1")

    testImplementation("com.google.android.gms:play-services-ads:24.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
