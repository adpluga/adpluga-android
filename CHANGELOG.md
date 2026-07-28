# Changelog

All notable changes to the AdPluga Android SDK are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

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
