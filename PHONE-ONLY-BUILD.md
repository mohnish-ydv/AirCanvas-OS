# Phone-only build with GitHub Actions

The repository includes a release-gated workflow. GitHub's Linux runner
provides the Android toolchain, so the phone only needs to push the source and
download the completed artifact.

## Recommended: push with Termux

1. Extract the final ZIP into phone storage.
2. Create an empty GitHub repository. Do not add a README or `.gitignore`.
3. In Termux, install the upload tools:

   ```bash
   pkg update
   pkg install git unzip
   termux-setup-storage
   ```

4. Enter the extracted project folder, then push it:

   ```bash
   git init -b main
   git add .
   git commit -m "AirCanvas OS v2.3.0 pinch rebuild"
   git remote add origin https://github.com/YOUR-USER/YOUR-REPO.git
   git push -u origin main
   ```

   Use GitHub's supported authentication flow or a narrowly scoped token when
   prompted. Never commit a token, password, or keystore.

5. Open the repository in the GitHub app or browser.
6. Open **Actions → Build verified APK → Run workflow**.
7. After every step is green, download
   **AirCanvas-OS-Codename-v2.3.0-Pinch-Rebuild** from **Artifacts**.
8. Extract the artifact. It contains the APK, its SHA-256 file, and the merged
   permission list.
9. Install the APK. Android may ask permission to install apps from the browser
   or file manager used to open it.

## What the workflow verifies

The workflow first runs the portable core, camera, Portfolio, recognition-reliability, landscape-readiness, and Pinch Reality harnesses, then runs the Auto Text reflection bridge end to end.
It then runs:

```bash
./gradlew --no-daemon clean testDebugUnitTest lintRelease assembleRelease
```

It then verifies:

- APK signature
- 16 KB ZIP alignment
- Merged manifest permissions
- Presence of camera and one-time language-model download permissions
- Absence of unrelated privacy-sensitive permissions such as microphone,
  location, contacts, SMS, and legacy external-storage access
- APK checksum generation

If compilation, a unit test, release lint, assembly, signing, alignment, or the
permission audit fails, no APK artifact is uploaded.

## Updating the app later

Increase both `versionCode` and `versionName` in `app/build.gradle`. Keep the
same application ID and signing key when the new APK must install as an update.

The codename workflow uses the runner's generated debug key and is intended for
fresh test installs. A stable private keystore is required for reliable
in-place upgrades and any public release.
