# Android client stack

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** CON-005, QUAL-005, FLOW-001, FLOW-002, FLOW-004, FLOW-009, FLOW-013, MEDIA-002, MEDIA-003, MEDIA-004, AUTH-001, AUTH-002, EXT-002, EXT-009, A11Y (whole section)

**Context:** CON-005 makes the client a native Android application. It must hold a substantial local store (favourites, own recipes in every state, both dictionaries), queue work across restarts and connectivity changes, run timers from the device itself (EXT-009), convert photographs and video on the device (MEDIA-002..004), and obtain a device verdict with per-device slot state (AUTH-001, AUTH-002). Minimum Android 10, API 29 (QUAL-005).

**Decision:**
- UI: Jetpack Compose with Material 3, a single Activity, Navigation Compose.
- Local store: Room over SQLite, including an FTS table for favourites and own-recipe search.
- Background work: WorkManager for the synchronisation outbox and media uploads.
- Media: photographs decoded by the platform and re-encoded to JPEG with rotation applied to the pixels and no metadata written; video through Media3 Transformer (scale, drop audio, re-encode).
- Device verdict: Play Integrity API with device recall.
- Networking: Ktor client with kotlinx.serialization, using the `:core` contract types.
- Dependency wiring by hand, through one application container; no DI framework.
- `minSdk` 29.

**Consequences:**
- Offline behaviour is a property of the local store, not of a cache: the device's Room database is the device's truth until the server has accepted a change.
- Media3 Transformer and Play Integrity need a real device or emulator to exercise; see the test strategy ADR.
- Manual wiring costs a little boilerplate in the container and buys builds without annotation processing for DI and fewer places for a generated graph to go wrong.

**Alternatives considered:**
- *Flutter or React Native.* Rejected: CON-005 asks for native, and timers, alarms and media conversion would go through bridges.
- *Android Views with XML layouts.* Rejected: Compose is the platform's current toolkit and makes screen-reader semantics (A11Y) explicit in code.
- *Hilt.* Rejected: annotation processing, slower builds, and a generated graph that agents misconfigure; the application is small enough to wire by hand.
- *FFmpeg for video.* Rejected: a large native binary with licensing questions, where the platform codecs through Media3 do the job.
