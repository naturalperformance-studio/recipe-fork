# Photos are taken with the phone's own camera app, then cropped in the app

**Status:** accepted — supersedes `20261002-in-app-camera-with-camerax.md`
**Date:** 2026-10-03
**Requirements:** FLOW-026, MEDIA-002, MEDIA-003

**Context:** v0.6 replaced the system camera with an in-app CameraX camera because the system camera shot at its own 3:4 default; v0.8 added a 4:3 crop after the shot (camera and gallery alike). The owner then found the in-app camera's interface poor and asked for one "like the Claude app's" — which is the phone's own camera app, launched by intent.

**Decision:**
- "Take a photo" launches the system camera (`ACTION_IMAGE_CAPTURE`) into a FileProvider URI under `cache/capture/` (authority `.capture`, not exported), granting read and write to that one URI only.
- The shot opens the app's 4:3 crop; "Retake" relaunches the camera; the crop honours the shot's EXIF orientation and the cut goes through `PhotoAttaching` → `PhotoStore` (≤ 1 MB, ≤ 1600 px, no metadata).
- The pending capture's path is kept in saved state, so a shot survives the app being ended while the camera is in front.
- The app declares no CAMERA permission (removed from the merged manifest with `tools:node="remove"`) and asks for none; CameraX is removed. No camera app → a dialog offering the gallery.

**Consequences:** the release APK fell from 4.98 MB to 3.60 MB; the camera's look and controls are whatever the phone's camera app offers; framing happens in the crop.

**Alternatives considered:** keeping CameraX and restyling it — rejected: the owner wanted the phone's own camera, and the crop already gives the frame.
