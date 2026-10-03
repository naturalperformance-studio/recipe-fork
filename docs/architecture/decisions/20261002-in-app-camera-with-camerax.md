# The app's own camera is built with CameraX

**Status:** accepted
**Date:** 2026-10-02
**Requirements:** FLOW-026, MEDIA-002, MEDIA-003

**Context:** v0.5 took photos through the system camera app, which shoots in whatever frame its own settings hold (often 3:4). The user wants the app to set the frame: a 4:3 landscape shot for the cover and for step photos, taken with the phone upright. That needs a camera inside the app, and the CAMERA permission.

**Decision:**
- The camera screen uses CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) inside the `:app` module, in `app.recipefork.app.media.camera`.
- ~~The viewfinder and the capture share one crop.~~ Amended 2026-10-03 (v0.8, T-093): the viewfinder shows and the shutter takes the sensor's whole frame; after the shot — and for a gallery photo — a crop screen offers a 4:3 landscape frame the author moves and zooms, cut from source pixels with the shot's rotation applied, never narrower than 1600 px when the source allows. The cut goes through the existing `PhotoAttaching` → `PhotoStore` pipeline unchanged; no temp file is written.
- The CAMERA permission is requested on the first "Take a photo", never at start. "Choose from gallery" (the system photo picker) needs no permission and stays.

**Consequences:**
- The APK grows by about 1–2 MB.
- The app now holds a runtime permission besides notifications; the store listing will name it.
- The system-camera path and its FileProvider for the capture are removed.

**Alternatives considered:**
- *Keep the system camera and crop afterwards.* Rejected: the user would frame one picture and get another.
- *Camera2 directly.* Rejected: far more code for the same result; CameraX is the platform's own wrapper.
