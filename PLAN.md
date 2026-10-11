# TripPlanner - build plan

One-day trip planner (Hebrew, RTL) with sunset, zmanim, weather and elevation awareness.
Single user, no backend. Each milestone ends with a commit; CI (GitHub Actions) builds every push.

## Verified external dependencies (checked 2026-10-11)

| Area | Choice | Notes |
|---|---|---|
| Build | Gradle 9.8.0, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12 | AGP/Kotlin versions match Google's maintained `android-maps-compose` repo |
| SDK | minSdk 26, compile/target 37 | |
| UI | Compose BOM 2026.09.00, Material 3, Navigation Compose 2.9.8, Reorderable 3.1.0 | No dynamic color |
| Persistence | Room 2.8.4, DataStore Preferences 1.2.0 | |
| Network | Retrofit 3.0.0 + kotlinx.serialization 1.11.0 | |
| Location | play-services-location 21.4.0 (Fused Location Provider) | |
| Places | `com.google.android.libraries.places:places` 5.3.0, `Places.initializeWithNewPlacesApiEnabled` | `places-ktx` is archived; its `await*` extensions now ship in the SDK |
| Map | `maps-compose` 9.0.1 | |
| Routes | Routes API REST `directions/v2:computeRoutes` with field mask | Android-restricted key via `X-Android-Package` / `X-Android-Cert` |
| Weather + elevation + zone | Open-Meteo `/v1/forecast`, free for non-commercial use, no key, `timezone=auto`, `timeformat=unixtime`, `elevation` (90 m DEM downscaling) | Fallback `/v1/elevation` |
| Zmanim | `com.kosherjava:zmanim` 2.5.0 (LGPL 2.1) | |
| Font | Rubik variable font (SIL OFL 1.1) | |

## Milestones

1. **Skeleton and hygiene** - Gradle wrapper, version catalog, `.gitignore`, Secrets Gradle Plugin with placeholder defaults, env-based release signing, CI workflow (tests, lint, debug APK artifact), this plan.
2. **Core domain (pure Kotlin, JVM tests)** - models, sequential schedule with traffic fallback and leg cache, zmanim wrapper, daylight labels and fixes, prayer suggestions, weather mapping, clothing rules, time-zone handling.
3. **Data layer** - Room (trips, stops), DataStore (settings), Open-Meteo client with 1 h cache, Routes API client (field mask, Android key headers), Places, Fused Location, demo fakes with the Morocco sample day.
4. **Main UI** - theme (colors, Rubik, RTL), trip list (open, duplicate, delete), trip screen with timeline, zmanim card, clothing card, alerts, date/time pickers, reorder/edit/delete.
5. **Add-stop flow** - autocomplete sheet (Hebrew, biased to previous stop), map pin preview, confirm sheet with dwell chips, toggles and previews.
6. **Extras** - daylight bar, "יצאתי עכשיו", navigation intents (Google Maps / Waze), share text, JSON export/import, footer totals, visible Hebrew error states.
7. **Notifications (optional)** - off by default, permission only when enabled.
8. **Docs and verification** - README (API key setup, services and data sent), final CI run.

## Known environment limits while building

The build sandbox cannot reach Google's Maven repository or the Android SDK host, so Android
modules are compiled and linted only in GitHub Actions; the pure Kotlin `core` module is also built
and tested locally.
