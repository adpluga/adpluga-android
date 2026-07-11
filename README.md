# AdPluga Android SDK

Native Android SDK for ad serving with pluggable server-side mediation.
Talks to the AdPluga edge (`/v1/serve` + `/v1/track`) and renders banner,
native, interstitial, rewarded, HTML5, and video formats.

- **Coordinates**: `com.adpluga:adpluga` on Maven Central
- **minSdk**: 24 (Android 7.0) · **JVM**: 17
- **Zero Google Play Services dependency**
- **License**: Proprietary — see [LICENSE](./LICENSE)

## Install

```kotlin
dependencies {
    implementation("com.adpluga:adpluga:0.2.0")
}
```

```groovy
dependencies {
    implementation 'com.adpluga:adpluga:0.2.0'
}
```

## Quick start

```kotlin
// Application.onCreate()
AdPluga.initialize(context, "pk_live_...")

// Layout XML
<com.adpluga.ui.AdView
    android:id="@+id/ad_view"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:slotId="slot_home_320x100"
    app:format="banner_320x100" />

// Kotlin
adView.load(object : AdListener {
    override fun onImpression() {}
    override fun onClick() {}
    override fun onError(e: AdError) {}
})
```

Full API reference and integration guides: <https://app.adpluga.com/docs/sdk/android>.

## Support

- Issues and questions: <https://github.com/adpluga/adpluga-android/issues>
- Security disclosures: <security@adpluga.com>

This repository is a read-only mirror of the internal monorepo. Pull requests
are accepted for discussion but changes are integrated upstream.
