# Changelog

All notable changes to the AdPluga Android SDK are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [0.7.2] — 2026-09

### Added
- `alt_text` on the served creative, announced in place of the image. An ad is
  never decorative — it carries meaning and opens a destination — so rendering
  it unlabelled left the tap target with no accessible name at all, failing
  WCAG 2.2 SC 1.1.1 and SC 2.4.4 (both level A). Where the advertiser wrote no
  alternative text the renderer falls back to the creative's title, and a
  carousel card with no copy of its own inherits the ad's label.

### Note
- This release also carries the 0.7.0 and 0.7.1 work, which was written but
  never published: the version manifest stayed on 0.5.1, so the release
  workflow never tagged those versions. The changelog entries below were
  already written for them.

## [0.7.1] — 2026-09

### Fixed
- A slot that failed to fill stayed blank for the rest of the session. Only the
  success path armed the next attempt, so a transient miss — or a widget that
  mounted before the SDK was initialised — cost the publisher that slot until
  the view was recreated. Every failure path now schedules another attempt,
  backing off exponentially from the client's cadence floor up to five minutes.
  Retry is independent of the slot's rotation cadence, which is off by default:
  tying recovery to rotation is what made a single miss permanent.

## [0.7.0] — 2026-09

### Fixed
- Tapping an image creative now opens the advertiser destination. HTML and
  video creatives always did; image ones reported the click and went nowhere,
  so the advertiser paid for a tap that never arrived.
- `initialize` with a **different** publisher key now fails instead of quietly
  returning the existing instance. Rotating a key revokes the previous one at
  once, so the silent path left an app serving with a dead key. The same key
  stays idempotent; call `destroy()` first to re-initialize deliberately.

### Added
- `AdPluga.DEFAULT_ENDPOINT` is public, so a host reading the endpoint from
  configuration no longer has to hardcode an AdPluga hostname.

## [0.6.0] — 2026-09

### Added
- `type=carousel` decks render in a snapping horizontal track built on the
  framework's own `HorizontalScrollView`, keeping the SDK dependency-free. Every
  card reports the same click: one advertiser, one auction, one impression.
- `Slide` model and `Ad.slides`, parsed in advertiser order; a slide without a
  creative is dropped.

### Changed
- A scheduled rotation now backs off while the reader is swiping the deck and
  resumes one full cadence after the last swipe.
- A slot cadence below the client floor is raised to it instead of being
  ignored, so a slot set to 15s rotates every 15s on a `pk_test_` key and every
  30s on a live one.

## [0.5.1] — 2026-09

### Fixed
- Frequency capping and first-party audiences now work on mobile. The SDK
  releases a first-party install id as `u` (the parameter the server actually
  reads) whenever the consent state allows personalisation; without consent no
  id leaves the device and the server skips both gates, as before.
- The query parameter was `user_hash`, which the server never reads; it is now
  `u`. The id lives in memory for the process lifetime; pass `userHash`
  explicitly to key the daily cap across app launches.

## [0.5.0] — 2026-09

### Added
- Slot rotation: `AdView` now re-serves on the cadence the publisher configures
  for the slot (`refresh_after_seconds` on the serve response), so a long-lived
  screen no longer shows a single frozen creative.
- Rotation is gated so it cannot waste the publisher's budget or produce
  non-viewable impressions: it only fires while the view meets the IAB pixel
  threshold (`ViewabilityTracker.isVisible`), is floored at 30s
  (`Constants.MIN_REFRESH_SECONDS`), and the pending callback is removed on
  detach and on every reload.
- Each rotation sends its index (`rq`) so refreshed impressions stay segregable
  from the initial render, as the MRC guidelines require.

## [0.4.2] — 2026-08

### Added
- Sandbox creatives (served by a `pk_test_` key) now render a small,
  non-interactive orange "TEST" badge in the top-left of every ad surface
  (`AdView`, `HtmlAdView`, `VideoAdView`, `NativeAd`, `InterstitialAd`,
  `RewardedAd`). Driven by the new authoritative `test` boolean on the serve
  response ad, surfaced as `Ad.isTest`. The badge never intercepts touches.

## [0.4.1] — 2026-08

### Fixed
- Native assets are now mapped from the flat serve fields
  (`title`, `body`, `cta_text`, `sponsored_by`, `icon_url`,
  `main_image_url`); the legacy nested `native` object is only a fallback.
  Fixes `Ad.nativeAssets` always being null for native creatives.

## [0.4.0] — 2026-07

### Fixed
- QuartileFirer now resolves relative ping URLs against the SDK endpoint
  using `java.net.URL(URL(base), raw)`, fixing quartile tracking when the
  backend returns relative paths.
- VideoAdView passes `pluga.endpoint` to QuartileFirer constructor.

## [0.3.0] — 2026-07

### Added
- IAB viewability dispatch: `AdPluga.fireViewable(slotId, trackToken)` posts
  `/v1/track/viewable` from the same viewability callback that already
  recorded the impression on `AdView`, `NativeAd`, `InterstitialAd` and
  `RewardedAd`.

## [0.2.0] — 2025-11

### Added
- HTML5 / WebView ad format via native `WebView` (no external deps).
- Video and rewarded video via native `MediaPlayer` + `SurfaceView`
  with VAST-style quartile beacons.
- `QuartileFirer` helper (fire-and-forget beacons at 0/25/50/75/100%).

### Changed
- Rewarded countdown is now driven by real player progress
  (`MediaPlayer.currentPosition`) instead of a static timer.

## [0.1.0] — 2025-10

### Added
- Initial public release: banner, native, and interstitial formats.
- `AdView` custom View, `AdListener` callback interface.
- Choreographer-based viewability tracker and consent adapter.
