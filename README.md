# Toman Rates (Android widget)

Home-screen widget + app showing Iranian free-market **selling** prices (Tomans) for any currency
Alanchand returns. Kotlin, RemoteViews widget, Compose app, WorkManager. No third-party HTTP/JSON libraries.

## Build the APK
1. Install Android Studio (current stable). Open this folder (`TomanRates`). Let Gradle sync
   (it downloads Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21; accept SDK 35 when prompted).
2. Build > Build Bundle(s) / APK(s) > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.
   Command line (after Android Studio created the wrapper, or with a local Gradle 8.9): `gradle assembleDebug`.
3. Copy the APK to the phone and install it (allow "install unknown apps"), or run it from Android Studio over USB.
4. Open the app > Settings > paste your Alanchand token > "Save token and update".
5. Long-press the home screen > Widgets > Toman Rates. Resize to show more currencies.

## How it works
 require API token one call returns every currency
  (observed: 44 entries) as `{slug: {sell, buy, dolar_rate, open, high, low, dayChange, updated_at, name}}`.
- Only `sell` is used (Tomans). Five currencies (JPY, IQD, AMD, KRW, SYP) are quoted per 100 units; the API has
  no unit field, so the app detects the Persian "صد" prefix in `name`.
- Rate limit: 1 request/hour. All network access goes through `RateRepository.refresh()`, which refuses to call
  more than once per 60 min (also for the Update button). Periodic work runs every 65 min.
- Honesty rules: LIVE (last attempt ok, < 3 h old) / DELAYED (last attempt failed, rates < 3 h old, amber) /
  UNAVAILABLE (older than 3 h or never; the widget shows "—" and "Source unavailable · last update HH:mm").
  The widget never draws a price older than 3 h. Widget refreshes triggered by the system re-render from cache only.
- Adding currencies = data, not code: the list, order and default are in `Store`; the widget and calculator read
  them. `Catalog` only adds English names/flags and falls back to the API's own name for unknown slugs.

## Not verified (read this)
- This code was written without an Android SDK and **has not been compiled or run**. Expect to fix a typo or
  two on first sync. Send me the error text and I'll fix it.
- Unknown: the API's error / wrong-token / rate-limit response bodies (any non-200 or unparseable reply is shown
  as a failure), whether `updated_at` is Tehran time, and whether the limit is rolling or per clock-hour.
- Token is kept in app-private storage (not encrypted), backups are off. Don't share the APK after saving a token.
- Samsung may stop background work for apps it considers unused: Settings > Battery > Background usage limits >
  make sure Toman Rates is not in "Sleeping apps".
