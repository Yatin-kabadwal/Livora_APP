# Corbett The Vedant By Livora - Android app

Native Kotlin / Jetpack Compose app for the 10-room resort in Jim Corbett, Uttarakhand.
One app, two experiences: **guests** (browse, book, manage stays, message, order room service) and
**staff** (dashboard, bookings, housekeeping, room-service queue, inbox) chosen by the account role.

Package `com.livora.corbett` - applicationId `com.livora.corbett.vedant` - v1.0.0 (1) - minSdk 24 - target/compile 35.

> The sources were written and statically checked (`python3 tools/check_imports.py`) but have **not been compiled**
> in the authoring environment (no Android SDK / Maven access). Expect to fix a small number of compiler
> complaints on first sync; see "First build" below.

## 1. Point the app at your backend (the only edit needed)

Open `app/build.gradle.kts` and change **one line** near the top:

```kotlin
val apiBaseUrl = "https://YOUR-RENDER-APP.onrender.com/api/"   // keep the trailing slash
```

`SOCKET_URL` is derived automatically (the URL with `/api/` stripped). `SITE_URL` (used for share links) is
the next line. To test against a backend on your computer, set `useLocalBackendInDebug = true`
(emulator URL `http://10.0.2.2:5000/api/`). Cleartext HTTP is permitted **only** in debug builds.

## 2. First build

1. Android Studio Koala (2024.1) or newer, JDK 17 (bundled).
2. *File > Open* this `android/` folder. Studio downloads Gradle 8.9 (see `gradle/wrapper/gradle-wrapper.properties`)
   and generates the wrapper scripts; if you want them on the command line run `gradle wrapper --gradle-version 8.9` once.
3. Run the `app` configuration on a device/emulator (API 24+).

Command line: `./gradlew :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Signed release build

```bash
keytool -genkeypair -v -keystore corbett-release.jks -alias corbett -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.template keystore.properties   # then fill in the passwords (git-ignored)
./gradlew :app:assembleRelease      # APK: app/build/outputs/apk/release/
./gradlew :app:bundleRelease        # AAB for Play: app/build/outputs/bundle/release/
```

Release builds use R8 (minify + resource shrinking) with the rules in `app/proguard-rules.pro`
(kotlinx.serialization, Retrofit, socket.io/engine.io, Hilt). Back up the keystore: Play needs the same key for updates.

### Play Store notes
- Enrol in Play App Signing and upload the `.aab`. Bump `versionCode` in `app/build.gradle.kts` for every upload.
- Data safety: the app collects name, email, phone and booking details to operate the service; traffic is HTTPS only;
  tokens are stored in EncryptedSharedPreferences. No ads, no analytics SDKs.
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (staff alerts only, asked once, Android 13+).
- Provide a demo staff login to the reviewers if you want them to see the staff side.

## 4. Changing the app icon

Adaptive icon: `res/drawable/ic_launcher_foreground.xml` (vector), background colour `ic_launcher_background`
in `res/values/colors.xml`, themed/monochrome layer `ic_launcher_monochrome.xml`. Replace the vector, or use
*New > Image Asset* in Android Studio. The splash icon is `ic_splash_icon.xml`.

## 5. Architecture

Single-module, clean layering, Hilt DI (KSP), unidirectional state (`StateFlow`) per screen:

```
data/api       Retrofit ApiService, DTOs (tolerant kotlinx.serialization), interceptors, token authenticator
data/local     SessionStore (encrypted), AppPrefs (DataStore)
data/realtime  SocketManager (socket.io, foreground only)
data/repo      Repositories returning ApiResult (never throw), shared holders (search criteria, last booking)
work           WorkManager 15-minute poller for staff local notifications
ui/theme       Palette, Cormorant Garamond + Manrope, dark/light schemes, reduce-motion locals
ui/components  ForestBackdrop (canvas, tilt sensor, mist, fireflies), tilt/flip cards, calendar, confetti, shimmer ...
ui/auth|guest|staff|profile  Screens + ViewModels;  ui/navigation  routes + animated NavHost
```

- Auth: access token attached by an interceptor; on 401 a single-flight authenticator refreshes with the rotating
  refresh token and retries. If refresh fails the session is cleared and the app returns to Login.
- Errors: every call is wrapped (`safeApi`), the API `error` message is shown to the user; a "server is waking up"
  hint appears after 6 s of loading (Render free-tier cold start).
- Bookings and messages sent from the app use `source: "app"`.
- Accessibility: content descriptions, 48 dp touch targets, semantics roles; animations respect the system
  "Remove animations" setting (`ANIMATOR_DURATION_SCALE`).
- Fonts (Cormorant Garamond, Manrope; SIL OFL) are bundled as static instances in `res/font`.

## 6. Known limitations

- **No push while the app is closed.** Real-time updates use Socket.IO while the app is in the foreground; in the
  background staff get a local notification from a 15-minute WorkManager poll (Android's minimum interval).
  True push needs Firebase Cloud Messaging plus a backend sender, which is outside this scope.
- Screen transitions use crossfade/scale; shared-element transitions were deliberately not used (still experimental).
- Endpoints whose response shape is not fixed in the API contract are parsed tolerantly (arrays or wrapper objects);
  if your backend returns something unexpected, `data/api/Json.kt` (`decodeList`) is the place to adapt.
- Not compiled or run in the authoring environment; verify on device before publishing.

## 7. Tooling

`python3 tools/check_imports.py` - static checks without a toolchain: project imports resolve, cross-package symbols
are imported, common Compose/AndroidX imports are present, brackets balance, no leftover markers.
