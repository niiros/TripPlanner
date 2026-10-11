# TripPlanner - מתכנן יום טיול

Native Android app (Kotlin, Jetpack Compose, Material 3) for planning **one day of travel**, with
awareness of sunset, Jewish prayer times (zmanim), weather and elevation at every stop. Hebrew UI,
right-to-left. Personal use: single user, no backend, no login, everything stored on the phone.

It is built to work abroad, where conditions change sharply between stops. Example: a day from
Marrakech over the High Atlas (Tizi n'Tichka, about 2,260 m) to Ouarzazate, where the pass is much
colder and windier than the valleys.

## What it does

- **Trip setup**: date (default today) and departure time (default now, rounded up to 5 minutes).
  Start point is the current location, or a searched place if location permission is denied.
- **Stops**: "הוספת יעד" opens a sheet with Google place autocomplete (Hebrew, biased to the
  previous stop), a map pin preview and a confirm step: dwell time (15/30/60/120 min or custom),
  "דורש אור יום", "מנחה כאן", "ערבית כאן", plus a preview of drive time, arrival, sunset there,
  forecast at arrival and elevation.
- **Timeline**: start, then each stop with arrival, departure, dwell, weather and elevation, and
  between stops the drive duration and distance. Drag to reorder (or use move up/down), edit,
  delete. Any change recalculates the whole chain.
- **Schedule**: `depart(i) = arrive(i) + dwell(i)`, `arrive(i+1) = depart(i) + drive(i, i+1)`.
  Each leg is requested from the Routes API with predicted traffic for `depart(i)`, in order. A
  past departure time or a region without traffic data falls back to non-traffic duration with a
  "ללא נתוני תנועה" note on that leg.
- **Time zones**: all times are shown in the trip's zone (taken from the start point's
  coordinates), never the phone's. Stops in another zone are shown in their own zone and flagged.
- **Zmanim** per stop (KosherJava): chatzot, mincha gedola, mincha ketana, plag hamincha,
  sea-level sunset, tzeit. Sha'ot zmaniyot per the Gra; tzeit method is a setting (8.5 degrees,
  18 minutes, 72 minutes Rabbeinu Tam). A dark summary card shows the last stop's zmanim.
- **Daylight**: per-stop label (midday or time before/after sunset); for "דורש אור יום" stops an
  OK / warning / late status against a sunset buffer (default 30 min) with one-tap fixes
  ("לצאת ב-HH:MM", "לקצר שהייה ב-N דק'").
- **Prayer**: mincha and arvit suggestions, manual overrides, and alerts when no stop fits.
- **Weather** (Open-Meteo, elevation-aware) at the arrival hour: icon plus text, "מרגיש",
  strong wind, rain probability, high UV. "אין תחזית עדיין" beyond the forecast horizon.
- **Clothing card** ("לבוש להיום"): apparent-temperature range and spread across the day, where
  the extremes happen, the high point when the elevation range exceeds 800 m, and item chips.
- **Extras**: daylight bar, "יצאתי עכשיו" (re-anchor to the real time), Google Maps / Waze
  navigation, Hebrew share text, JSON export/import, footer totals, optional local reminders.
- **Demo mode**: without an API key the app runs on fakes with a Morocco sample day.

## Project layout

| Module | Contents |
|---|---|
| `core/` | Pure Kotlin, no Android: schedule, leg planning and caching, zmanim adapter, daylight, prayer, weather mapping, clothing rules, demo data and fakes. JVM unit tests in `core/src/test`. |
| `app/` | Android app: Compose UI (one ViewModel per screen), Room, DataStore, Retrofit clients (Open-Meteo, Routes API), Places SDK, Fused Location, Maps Compose. |

See [PLAN.md](PLAN.md) for milestones and the verified dependency versions.

## API key setup

The app needs **one Google Maps Platform API key**. It is never committed: it is read from
`local.properties` (git-ignored) by the Secrets Gradle Plugin; the committed
`local.defaults.properties` holds only a placeholder, which makes the app start in demo mode.

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and enable billing.
2. Enable exactly these APIs:
   - **Places API (New)** - autocomplete and place details (used by the Places SDK for Android)
   - **Maps SDK for Android** - the pin preview map
   - **Routes API** - traffic-aware drive times
3. Create an API key, then edit it:
   - **Application restrictions: Android apps.** Add package name `com.niiros.tripplanner` and the
     SHA-1 fingerprint of the certificate that signs your APK. For a local debug build:
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`.
     A CI build signs with the CI runner's own debug keystore, which has a different SHA-1;
     add that fingerprint too if you install the APK from GitHub Actions (or sign with your own
     key, see below).
   - **API restrictions: Restrict key** to the three APIs above only.
4. Set a **daily quota cap** for each API (Console, *APIs & Services* > the API > *Quotas*), for
   example a few hundred requests per day for Routes API and Places API. This bounds the cost of a
   leaked key. A billing budget alert is a good second line.
5. Put the key in `local.properties` in the project root:
   ```
   MAPS_API_KEY=AIza...your key...
   ```

**Be aware:** a key inside an APK is extractable by anyone who has the APK. The restrictions above
(Android app + SHA-1, API restriction, quota cap) are the mitigation; they do not make the key
secret. Do not publish APKs built with your key.

### How the Routes API call is authenticated

Places and Maps use their Android SDKs, which send the app identity automatically. The Routes API
is called directly over REST (`POST https://routes.googleapis.com/directions/v2:computeRoutes`),
so the app adds the headers Google's Maps Platform security guidance names for Android apps calling
web services directly: `X-Goog-Api-Key`, `X-Android-Package` (the package name) and
`X-Android-Cert` (the SHA-1 of the signing certificate, upper-case hex without separators, computed
at runtime from the installed APK). A field mask
(`routes.duration,routes.staticDuration,routes.distanceMeters`) limits the response to what the
schedule needs. The key stays Android-restricted; it is not weakened.

This was not tested end to end (no real key was available while building). If the Routes API
answers 403, the app shows "מפתח ה-API נדחה"; check that the SHA-1 on the key matches the
certificate that signed the installed APK and that Routes API is in the key's API list.

## Build locally

Requirements: JDK 17 or newer, Android SDK with platform 37 (Android Studio installs it).

```bash
./gradlew :core:test                               # pure Kotlin unit tests
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug                       # app/build/outputs/apk/debug/app-debug.apk
```

Without `local.properties` the build still succeeds and the app runs in demo mode.

### Release signing

No keystore lives in the repository. A release build is signed only if these environment
variables are set: `TRIPPLANNER_KEYSTORE_FILE`, `TRIPPLANNER_KEYSTORE_PASSWORD`,
`TRIPPLANNER_KEY_ALIAS`, `TRIPPLANNER_KEY_PASSWORD`. Add that certificate's SHA-1 to the key.

### Before every push

```bash
./scripts/check-secrets.sh
```

It fails if `local.properties`, a keystore or anything that looks like a Google API key is tracked
or staged. CI runs it too.

## Get the APK from GitHub Actions

Every push runs `.github/workflows/android.yml`: secret check, unit tests, lint, debug APK.

1. Open the repository's **Actions** tab and pick the latest green "Android CI" run on `main`.
2. Download the **TripPlanner-debug-apk** artifact (a zip) and unzip it.
3. Copy `app-debug.apk` to the phone and install it (allow installs from that source).

The workflow reads the key from a repository secret named **`MAPS_API_KEY`** (Settings > Secrets and
variables > Actions). If the secret is absent the build still succeeds and the APK runs in demo
mode. Note the SHA-1 caveat above: the CI debug certificate differs from your local one, and it
can change between runs unless you sign with your own keystore.

## External services and what is sent

No account, no analytics, no backend. Only coordinates and times leave the phone, plus the search
text you type into the place search.

| Service | Used for | Data sent |
|---|---|---|
| Google Places API (New), via Places SDK for Android | Autocomplete, place details | The typed search text, the previous stop's coordinates as a location bias, a session token, the app's package and certificate identity |
| Google Maps SDK for Android | Pin preview map | Map tile requests around the chosen place |
| Google Routes API | Drive time and distance per leg | Origin and destination coordinates, departure time, travel mode, language; package name and certificate SHA-1 headers |
| Open-Meteo (`api.open-meteo.com`) | Hourly forecast, elevation, time zone | Coordinates and dates only; no key. Free for non-commercial use; weather data by Open-Meteo.com, CC BY 4.0 |
| Google Maps / Waze apps (intents) | Turn-by-turn navigation | The destination coordinates, handed to the installed app |

Zmanim are computed on the phone with the KosherJava library; no network call. The API key is never
written to exported JSON files or share text.

## Licenses

- KosherJava Zmanim: LGPL 2.1 (used unmodified as a library dependency).
- Rubik font: SIL Open Font License 1.1, see [licenses/Rubik-OFL.txt](licenses/Rubik-OFL.txt).
- Weather data: Open-Meteo.com, CC BY 4.0.
