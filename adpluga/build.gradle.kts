plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.vanniktech.maven.publish")
}

group = "com.adpluga"
version = "0.2.0"

android {
    namespace = "com.adpluga"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"0.2.0\"")
        buildConfigField("String", "SDK_PLATFORM", "\"android\"")
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
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.adpluga", "adpluga", "0.2.0")

    pom {
        name.set("AdPluga Android SDK")
        description.set("AdPluga native Android SDK for ad serving with pluggable mediation.")
        inceptionYear.set("2025")
        url.set("https://adpluga.com")
        licenses {
            license {
                name.set("Proprietary")
                url.set("https://adpluga.com/legal/sdk-license")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("adpluga")
                name.set("adPluga")
                url.set("https://github.com/adpluga")
            }
        }
        scm {
            url.set("https://github.com/adpluga/adpluga-android")
            connection.set("scm:git:git://github.com/adpluga/adpluga-android.git")
            developerConnection.set("scm:git:ssh://github.com:adpluga/adpluga-android.git")
        }
    }
}
