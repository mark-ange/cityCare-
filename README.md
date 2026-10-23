# CityCare CDO Android App

Native Android app for CityCare CDO residents to submit waste reports and follow their status.

## Project contents

- `app/` — Kotlin and Jetpack Compose Android application.
- `backend/` — Express API and MongoDB data models used by the mobile app.

## Requirements

- Android Studio with the Android SDK for building and installing the app.
- JDK 17 (Android Studio's bundled JBR is suitable).
- Node.js 18 or later for the API.
- MongoDB Atlas or another reachable MongoDB database.

## Configure the API

1. Start the Android project's API backend from the repository root with `npm --prefix backend start`. Its private `backend/.env` must use a reachable MongoDB URI (Atlas or another hosted MongoDB; `localhost` only works if MongoDB is installed and running locally) and set `MONGO_REPORT_DATABASE=citycare_db` so the admin portal and resident app share reports, collection schedules, and published community content. The API listens on port `5001`. The web frontend is optional and is not needed for mobile sign-in. Confirm `http://localhost:5001/api/health` returns `ONLINE` and `CONNECTED`. Keep MongoDB credentials on the backend only; never put them in the Android app.
2. The app's default API address is `http://10.0.2.2:5001/api` for an Android emulator. For a physical phone, start an HTTPS tunnel to port `5001` or use the computer's reachable Wi-Fi address, then tap **Server settings** on the sign-in screen and enter that API address. The backend and tunnel must remain running while the phone uses the app. The resident API stores reports in `citycare_db`, which is also used by the web-admin backend, so submitted reports are available in the admin portal.

   ```powershell
   # Android emulator using a backend running on this computer
   .\gradlew.bat assembleDebug -PCITYCARE_API_URL=http://10.0.2.2:5001/api
   ```

   - For local Wi-Fi only, pass `-PCITYCARE_API_URL=http://<COMPUTER_LAN_IP>:5001/api` when building.
   - Deployed API: use its HTTPS address, such as `https://<API_HOST>/api`.

3. A phone using an HTTPS tunnel does not need to be on the same Wi-Fi as this computer. The computer must stay on, with the Android backend and tunnel running. Port `5000` may be used by the separate web-admin backend.
4. A local API address works only while the API computer is on and reachable. MongoDB Atlas hosts the database, not the API. For a deployed API, enter its HTTPS `/api` address in **Server settings**.
5. Add `app/google-services.json` if building the Firebase-enabled app. Obtain it from Firebase project settings and keep it out of version control.
6. Google sign-in sends its ID token to the API for verification. Set `GOOGLE_CLIENT_ID` in `backend/.env` to the same Web OAuth client ID used by the app's `.requestIdToken(...)` configuration.
7. To enable Facebook sign-in:
   - Create/configure a Facebook app with the Facebook Login product. Add the Android package `com.example.razoproject` and the signing-key hashes used for your debug/release builds.
   - Add `FACEBOOK_APP_ID` and `FACEBOOK_CLIENT_TOKEN` to `%USERPROFILE%\.gradle\gradle.properties`; rebuild and reinstall the Android app.
   - Replace the example values in `backend/.env.example` with the same `FACEBOOK_APP_ID` and the private `FACEBOOK_APP_SECRET` in `backend/.env`. Never put the app secret in Android/Gradle properties or commit it.
   - Restart the backend after setting both values. If the app reports that Facebook sign-in is not configured on the server, the backend is missing one or both values or is still running with its old environment.
   - The API validates each Facebook access token with Facebook before creating or signing in to a CityCare account. Use HTTPS for a deployed API; do not send access tokens over public plain HTTP.
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
