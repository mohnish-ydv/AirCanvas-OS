# Third-party notices

This project uses the following third-party components through Gradle:

- AndroidX AppCompat, Activity, and CameraX — Apache License 2.0
- MediaPipe Tasks Vision — Apache License 2.0
- Google ML Kit Digital Ink Recognition — Google APIs Terms / applicable
  component licenses
- JUnit 4 — Eclipse Public License 1.0 (tests only)
- `org.json` Java package — JSON License (tests only)

The bundled hand-landmarker task file was obtained from Google's official
MediaPipe model bucket:

```text
https://storage.googleapis.com/mediapipe-models/hand_landmarker/
hand_landmarker/float16/latest/hand_landmarker.task
```

Bundled model SHA-256:

```text
fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1
```

Refer to each upstream project and model source for its complete license and
usage terms. No third-party source code has been copied into the app's own Java
packages.
