# CityCare CDO Android App

Native Android app for CityCare CDO residents to submit waste reports and follow their status.

## Project contents

- `app/` — Kotlin and Jetpack Compose Android application.
- `backend/` — Express API and MongoDB data models used by the mobile app.

## Requirements

- Android Studio with the Android SDK for building and installing the app.
- JDK 17 (Android Studio's bundled JBR is suitable).
- Node.js 16 or later for the API.
- MongoDB Atlas or another reachable MongoDB database.

## Configure the API

1. Start the CityCare API backend and confirm `http://<COMPUTER_LAN_IP>:5000/api/health` returns `ONLINE` and `CONNECTED`. Keep MongoDB credentials on the backend only; never put them in the Android app.
2. Set `CityCareApiService.BASE_URL` in `app/src/main/java/com/example/razoproject/CityCareApiService.kt`:

   ```powershell
   var BASE_URL = "http://<COMPUTER_LAN_IP>:5000/api"
   ```

   - This checkout currently uses `http://192.168.1.8:5000/api`; replace that LAN IP if the computer changes networks or receives a new address. Reserve the computer's LAN address in the router so it does not change.
   - Android emulator using an API on the same computer: `http://10.0.2.2:5000/api`
   - Deployed API: use its HTTPS address, such as `https://<API_HOST>/api`.

3. For a physical phone, connect it and the computer to the same Wi-Fi, allow inbound TCP port `5000` through Windows Firewall on Private networks, then rebuild and reinstall the app after changing the URL.
4. A local API address works only while the API computer is on and reachable. MongoDB Atlas hosts the database, not the API. For a deployed API, configure the HTTPS address above.
5. Add `app/google-services.json` if building the Firebase-enabled app. Obtain it from Firebase project settings and keep it out of version control.
6. To enable Facebook sign-in:
   - Create/configure a Facebook app with the Facebook Login product. Add the Android package `com.example.razoproject` and the signing-key hashes used for your debug/release builds.
   - Add `FACEBOOK_APP_ID` and `FACEBOOK_CLIENT_TOKEN` to `%USERPROFILE%\.gradle\gradle.properties`; rebuild and reinstall the Android app.
   - Set the same `FACEBOOK_APP_ID` and the private `FACEBOOK_APP_SECRET` in `backend/.env`. Never put the app secret in Android/Gradle properties or commit it.
   - Restart the backend. The API validates each Facebook access token with Facebook before creating or signing in to a CityCare account. Use HTTPS for a deployed API; do not send access tokens over public plain HTTP.
   - Facebook accounts in Development mode can only sign in if they have a role on the Facebook app. Request the required permissions/review and make the app available to users before production.
   - Add `MAPS_API_KEY` to Gradle properties if enabling Google Maps.

## Build and install

Open this directory in Android Studio and let Gradle sync, or build from PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Install it on a connected device with Android Debug Bridge:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Collection schedules

The Home screen uses the signed-in resident's barangay and registered purok, zone, or street to find an area-specific route from `GET /api/schedules`. The Schedule service lists published area routes by barangay. A missing area match is shown as unavailable; the app does not guess a route from another barangay or substitute a general schedule.

## Reports and status updates

The app saves submitted reports on the device and retries upload when the API can be reached. A report with a photo is only marked as synced after the API confirms the image was stored. While the resident app is open, it fetches every page of the signed-in resident's server reports and retries uploads of saved local photos missing from those reports, including reports previously marked as synced. Admin status, assigned unit, and dispatcher notes then appear in ticket details. Demo and other residents' reports are excluded from the resident's report list. Captured JPEG photos are resized and stored with the report in MongoDB, without an inactivity-based expiry; keep the server database and its backups for long-term retention. If a phone is lost, app data is cleared, or an image was never saved locally or uploaded, it cannot be recovered from that device. Rebuild and install the updated APK over the existing app to enable photo backfill; do not clear app data or uninstall first.

## Sign-in behavior

For privacy, opening the app always shows the sign-in screen. A previous sign-in session is cleared at startup; residents must authenticate again before opening their dashboard.
