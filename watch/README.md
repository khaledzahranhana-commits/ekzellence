# eKZellence Watch

Android camera recorder and password-protected browser viewer for Khaled Zahran.

**Version 0.1.0 beta. This is an installable engineering build, not a production security system. USB compatibility, screen-off recording, thermal behavior, simultaneous cameras and SD persistence require tests on the actual Galaxy Tab A8 SM-X205 / Android 14.**

## Included

- UVC USB webcam capture with explicit Android USB permission.
- One USB camera and up to three H.264 RTSP-over-TCP cameras; hardware capacity is not guaranteed.
- Camera names, individual start/stop controls, reconnect attempts for RTSP and status indicators.
- MP4 recording in approximately one-minute segments, without audio.
- Live JPEG preview in the tablet and authenticated browsers at approximately two frames per second. This beta does not provide full-frame-rate browser streaming.
- Local password setup on the tablet, PBKDF2 password hashing, encrypted camera/cloud credentials, expiring browser sessions, login attempt limiting and CSRF checks.
- HTTPS with a device-generated, self-signed certificate. The Android WebView pins its own certificate. Browsers must trust the tablet certificate; there is no public certificate or public service provisioned.
- Android folder picker for SD recordings. UUID folders prevent names from causing path traversal; names are displayed through the database.
- Recordings filtered by camera and date, video playback, range requests, full-file download and keyframe-aligned video clip exports.
- Deletion of finalized recordings older than 30 days, on startup and every 24 hours while the service runs. Capture pauses below 512 MB rather than silently erasing newer footage.
- Optional private Cloudflare R2 backup uploader and Worker source, disabled by default.

## Install and first run

1. Transfer the supplied APK to the Samsung tablet. Allow installation from that source when Android asks. This debug-signed beta is not listed on Google Play.
2. Open **eKZellence Watch** and allow the camera and recording notification permissions.
3. Create a password of at least eight characters. The only account is the recorder owner; this version does not offer separate viewer accounts or password recovery.
4. Connect the USB webcam with an OTG data adapter. Tap **Start recording**, and allow USB device access. Confirm both the live image and new MP4 files.
5. Wait about a minute and check **Recordings**. Play and download one file before relying on the recorder.
6. To use SD storage, stop cameras, choose a writable folder in **Settings → Choose SD folder**, then start recording again. Keep at least 512 MB free in internal working storage as well.
7. After a tablet restart or Android force-stop, open the app and start cameras manually. Capture does not silently restart at boot.

A powered USB hub may be required to power the camera and tablet together. Charging while acting as USB host is hardware-dependent and has not been verified for this tablet. Screen-off operation is implemented through a foreground service and wake lock, but Samsung battery management and overnight temperature testing remain necessary.

## View from a new phone

On the same Wi-Fi, open an address displayed in **Settings → Your recorder address**. Verify it belongs to your tablet, trust its certificate if appropriate, and enter the recorder password. The phone does not need the Android APK. Use **Recordings** to play, download or extract footage.

Away from home, establish a private VPN connection between the tablet and the viewing device, for example with Tailscale. The tablet VPN address should appear in Settings. This project does not configure the VPN, a domain, router port forwarding, or an internet relay. Do not expose the raw recorder port to the public internet.

The tablet must remain powered, with its camera connected. Remotely viewing recordings requires that their SD storage remains inserted and readable. A downloaded phone copy is independent of the recorder's retention cleanup.

## Add Wi-Fi cameras

The camera must expose an **H.264 RTSP** stream. Enter its full address, for example `rtsp://USERNAME:PASSWORD@CAMERA_IP:554/stream1`. Use the camera manufacturer's actual stream path. Wi-Fi alone does not imply RTSP support; proprietary cloud-only cameras may not work. H.265, ONVIF auto-discovery, audio, PTZ, motion detection and notification delivery are not included in this beta.

The custom transport supports RTP/TCP single-NAL, STAP-A and FU-A packets, SPS/PPS from SDP or the stream, and Basic or MD5 Digest authentication. Stream settings should remain fixed during a recording session.

## Storage planning

One stream at 1 Mbps uses approximately **10.8 GB/day or 324 GB/30 days**, before filesystem overhead. USB capture targets 1 Mbps / 15 fps, subject to encoder behavior. RTSP recording retains the camera's original bitrate. Two 1 Mbps cameras use about 648 GB/month. A nominal 512 GB card does not guarantee 30 days across multiple cameras.

This version keeps recent footage rather than deleting it early when the card fills. A visible warning appears below 1 GB and recording pauses below 512 MB. SD free-space availability can be unknown for some document providers; write failures also stop recording and remain visible in the camera status. Internal pending files are retained if finalization fails; incomplete MP4 files after sudden power loss may not be playable.

## Optional cloud backup

The Android app can upload finalized files over Wi-Fi to the included `cloud/worker.mjs` endpoint. **Nothing is deployed and no footage is uploaded by default.** R2 storage and network traffic may incur charges. This is backup only: cloud library browsing and offsite live relay are not part of this version.

1. Create a private R2 bucket named `ekzellence-watch-recordings`, or update `cloud/wrangler.toml`.
2. Set a randomly generated token of at least 24 characters using `wrangler secret put UPLOAD_TOKEN`.
3. Deploy the Worker after reviewing account costs and access. The Worker requires the token for every upload/download; do not make the bucket public.
4. Enter its HTTPS URL and token in the recorder's Cloud section, and explicitly enable backup.
5. Verify a successful upload before relying on it. Failed uploads retry later. The Worker rejects files larger than 250 MiB.

The scheduled Worker removes objects whose recording end timestamp is older than 30 days. There is no indefinite cloud archive. If the service is offline or backup falls behind retention, expired local files can be deleted before they are backed up. A factory reset or uninstall removes the local index and encrypted credentials; cloud objects remain until their retention cleanup.

## Build

JDK 17, Android SDK 34 and internet access for Maven dependencies are required.

```sh
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

Dependencies: UVCAndroid 1.0.13 (including native USB/video components), NanoHTTPD 2.3.1, AndroidX DocumentFile and Kotlin BOM. Preserve the upstream licenses and notices. Debug signing is for testing; distribute production updates with a stable, private release signing key kept outside the repository.

## Validation

- The APK build is checked with Gradle and Android SDK tools.
- `tests/CoreTest.java` checks exact retention boundaries, multi-camera capacity estimation, H.264 SPS parsing and malformed input against a generated real H.264 fixture.
- `node tests/worker.test.mjs` checks cloud authorization, metadata validation, safe object paths and retention selection.
- Browser flow checks cover setup/login, names, camera controls, date filtering, settings and responsive layout using test fixtures. They do not certify physical camera operation.

Before normal use, perform an overnight test, review gaps across segment boundaries, unplug/reconnect the camera, restart the tablet, remove/reinsert the SD card safely, and verify a download on the new phone. Uninstalling clears app-owned internal recordings and database; export any important files first.
