# eKZellence Watch

Android camera recorder with a private Cloudflare live viewer and recording archive.

**0.2.0 beta — cloud infrastructure is prepared in source, not deployed. No Cloudflare account connection or production URL has been provisioned. The Samsung Galaxy Tab A8 SM-X205 and supplied USB camera still require physical testing.**

## How it works

Cloudflare R2 is the primary recording archive when cloud mode is enabled. The Android tablet records short local MP4 segments, targeting approximately 10 seconds, and immediately queues each completed segment for upload. This temporary local encoding buffer is required even while online. Upload speed, camera keyframes and finalization affect the delay; there is no guarantee that every captured frame is already in the cloud.

A SHA-256 receipt confirms the object key, size and checksum after R2 accepts the upload. Only then does the tablet mark it uploaded and remove its temporary local copy. The recording remains accessible from the cloud portal. Interrupted uploads retry the entire short segment under the same immutable key. An identical retry returns the same receipt; changed content cannot replace an existing recording.

When internet connectivity drops, the tablet retains completed recordings and queue state in SQLite. Capture continues until local free space reaches the 512 MB reserve. On reconnection the active recorder attempts uploads immediately and continues draining the backlog. Android WorkManager also persists network-constrained recovery work across ordinary process death and reboot; Android controls when these background jobs run. Force-stopping the app prevents background work until it is reopened. Queued files are never deleted by 30-day local retention while cloud mode is enabled.

Live preview uses a separate authenticated JPEG relay through Cloudflare Durable Objects. Only the latest preview image is held temporarily in memory; it is not the permanent recording archive. Remote viewers do not need a VPN or access to the home router. Stale live frames are hidden after five seconds. The beta previews about 1–2 images per second, not full-frame-rate video. Live preview stops while the tablet is offline, but previously uploaded recordings remain accessible.

## Separate credentials

1. **Tablet app password:** protects local recorder controls.
2. **Cloud viewing password:** protects internet live view and cloud recordings. It must differ from the tablet password.
3. **Deletion PIN:** separately confirms manual deletion from the cloud portal. It must differ from both passwords.
4. **Device upload token:** stored encrypted on the tablet. It can upload new recordings and preview frames, but cannot read, delete or overwrite existing cloud recordings.

The cloud uses PBKDF2 password records, signed one-hour HttpOnly sessions, CSRF checks, persisted attempt limits and a private R2 bucket. The deletion PIN is verified on the Worker and is never stored by the tablet. Changing the cloud password invalidates existing signed sessions.

These are application-level permissions. The Cloudflare account owner retains administrative control of the account and bucket. Automated expiration after 30 days follows the configured retention policy without prompting for the manual deletion PIN.

## Theft and outages

If the tablet is stolen, footage that has already finished uploading remains in R2 and can be viewed from another device. Its upload token alone does not grant archive access or deletion rights. Footage captured while offline, or still in an unfinished/unuploaded segment, cannot be recovered from the cloud unless it successfully uploads first. This app cannot upload without internet or recover physical storage after theft.

## Recorder features

- One UVC USB webcam and up to three H.264 RTSP-over-TCP cameras, with individual names and controls. Hardware throughput is not guaranteed.
- Video-only MP4 recording in short segments. Screen-off operation uses a foreground service and wake lock; Samsung battery management, USB power and overnight heat require testing.
- Tablet memory or a selected SD folder for temporary offline storage.
- Per-camera/date local archive and queued footage playback, full downloads and keyframe-aligned clip exports for files still available locally.
- Cloud portal for internet live JPEG previews, per-camera/date recordings, range-based playback, downloads and PIN-confirmed deletion. Cloud clip extraction is not implemented; download the recording first.
- Daily cloud expiration of recordings older than 30 days. Recovery queue retries and periodic Android fallback work.
- Automatic RTSP reconnection and visible storage/upload status. Recording stops before exhausting local storage instead of discarding queued footage.

H.265, ONVIF auto-discovery, audio, PTZ, motion alerts and automatic camera capture after reboot are not included. Queued upload work can resume independently; reopening the app and starting cameras is still required to resume capture after reboot.

## Install / update

Install the supplied `eKZellence-Watch-0.2.0-beta.apk` on the tablet. It uses the same beta signing key as 0.1 in this workspace and migrates the recordings database from schema 1 to schema 2 without clearing it. Do not uninstall the old app before updating, because uninstalling removes app-owned internal footage and its index.

Allow camera and recording notification permissions, create the tablet password, connect the webcam through an OTG data adapter and press **Start recording**. Allow USB device access when prompted. Confirm a live image and a playable finalized recording before relying on it.

To choose an SD folder, stop all cameras and use Settings. USB host plus simultaneous tablet charging is hardware-dependent and has not been tested. A powered hub may be required.

## Deploy the cloud service

Deployment requires access to the user's Cloudflare account. No account login, tokens, bucket or DNS changes have been performed by this project.

From `cloud/`, with authenticated Wrangler:

```sh
wrangler r2 bucket create ekzellence-watch-recordings
python3 create-secrets.py
wrangler deploy
wrangler secret bulk .cloud-secrets.json
```

The secret generator asks for the tablet password only to compare it, then creates a different cloud password record, a different deletion PIN record, a random upload token and a session signing secret. It does not save the tablet password. The output `.cloud-secrets.json` is private and ignored by git; never commit it. `wrangler.toml` binds the private R2 bucket, persisted security gate and transient live-frame objects.

After deployment, enter the HTTPS Worker origin and **UPLOAD_TOKEN only** in the tablet's cloud settings, and enable cloud recording. The viewing password and deletion PIN are not put on the tablet. Open that HTTPS origin on the phone and use the separate cloud password. It works through the mobile browser; this project does not include a native iOS companion app.

Enabling cloud mode makes R2 the main archive and clears local copies only after verified upload. Disabling cloud mode stops uploads; it does not delete existing cloud objects. Live frames and cloud storage, requests, Durable Objects and bandwidth may incur charges. Sufficient upstream bandwidth is needed to upload new recordings and clear an outage backlog at the same time.

One 1 Mbps recording stream is about 10.8 GB/day or 324 GB/30 days, before overhead and live-preview traffic. RTSP uses the source camera bitrate, so additional cameras can require substantially more storage and upload bandwidth. The Worker rejects segments larger than 250 MiB.

## Build and validation

JDK 17 and Android SDK 34:

```sh
./gradlew clean :app:assembleDebug :app:lintDebug
node tests/worker.test.mjs
```

The source includes tests for immutable uploads, checksum receipts, retries, upload-only permissions, cloud login, separate deletion PIN, CSRF, range playback, stale live frames, persisted attempt limits and 30-day retention. Browser tests cover tablet controls, safe names, responsive layout and cloud login/deletion flows using fixtures. Core tests check retention boundaries and real H.264 SPS parsing.

The APK is debug-signed for evaluation. A production release needs a stable private signing key, third-party license review, real-device recording and outage tests, performance/security review, and monitoring. Tests performed without physical cameras do not certify continuous capture or live service operation.
