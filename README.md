# AdPluga Android SDK

Native Android SDK for ad serving with pluggable server-side mediation.
Talks to the AdPluga edge (`/v1/serve` + `/v1/track`) and renders banner,
native, interstitial, rewarded, HTML5, and video formats.

- **Coordinates**: `com.adpluga:adpluga` on Maven Central
- **minSdk**: 24 (Android 7.0) · **JVM**: 17
- **Zero Google Play Services dependency**
- **License**: Proprietary — see [LICENSE](./LICENSE)

## Why AdPluga

- **100,000 ad decisions free every month.** No card, no expiry.
- **No traffic minimum.** When there is no demand, a house ad fills the slot so it never renders empty.
- **Test mode first.** A `pk_test_` key serves ads with no billing and no quota use; switch to `pk_live_` when you are ready.
- **One integration, every demand source.** Direct deals, network demand and mediation behind the same slot.

Create a free account at <https://adpluga.com/en/> and get your keys in the dashboard.

## Install

```kotlin
dependencies {
    implementation("com.adpluga:adpluga:0.7.4")
}
```

```groovy
dependencies {
    implementation 'com.adpluga:adpluga:0.7.4'
}
```

## Quick start

```kotlin
// Application.onCreate()
AdPluga.initialize(publisherKey = "pk_test_...")
```

```xml
<!-- Layout -->
<com.adpluga.ui.AdView
    android:id="@+id/ad_view"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
```

```kotlin
// Activity or Fragment
findViewById<AdView>(R.id.ad_view).load(
    slotId = "your-slot-id",
    listener = object : AdListener {
        override fun onImpression() {}
        override fun onClick() {}
        override fun onError(error: Throwable) {}
    },
)
```

Imports: `com.adpluga.AdPluga`, `com.adpluga.AdListener`, `com.adpluga.ui.AdView`. Min SDK 24; the `INTERNET` permission comes with the library.

Integration guides and API reference: <https://adpluga.com/en/devs/sdks/> · quick start in two minutes: <https://adpluga.com/en/devs/quickstart/>.

## Support

- Issues and questions: <https://github.com/adpluga/adpluga-android/issues>
- Security disclosures: <security@adpluga.com>

This repository is a read-only mirror of the internal monorepo. Pull requests
are accepted for discussion but changes are integrated upstream.
