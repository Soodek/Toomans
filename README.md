# Toomans — Android Currency & Market App

Toomans is an Android app and home-screen widget for tracking Iranian free-market currency prices, gold and coin quotes, market movements, and related news. It has grown from a lightweight currency-rate widget into a multi-section market companion.

> **Development status:** The feature branch contains the additions described below. Build and device verification are tracked separately; a successful CI build does not replace testing on a physical Android device.

## What changed since the first version

The first version focused on displaying currency selling prices in a home-screen widget and a basic in-app calculator. The current development version adds or expands the following:

### 1. Currency market data
- Reworked the market-data integration to read public market information from TGJU pages instead of relying on the original Alanchand token-based request.
- Parses available selling and buying prices, opening price, daily high/low, daily change, source timestamp, and currency names.
- Keeps the last successful response locally so previously fetched prices remain available when the network is unavailable.
- Shows market-data states such as live, delayed, and unavailable instead of silently presenting old prices as current.
- Applies a local one-minute minimum interval between currency refresh attempts.

### 2. Currency calculator and list management
- In-app currency calculator.
- Selectable currency list: add and remove symbols, reorder currencies, and choose a default currency.
- Currency catalog with codes, flags, and display names; unknown symbols can fall back to names supplied by the market source.
- Calculator-linked market chart: choosing a currency (or a supported gold/coin asset) changes the chart's selected asset.

### 3. Gold and coin market
- Dedicated Gold section with Iranian 18-karat gold and Emami coin quotes when available from the source.
- Displays available market change, daily high/low, source time, and cached data.
- Includes a gram/karat value calculator. Its estimate is metal value only; it does not include making charges, tax, seller margin, or coin premium.
- 24-karat value, where shown, is a mathematical equivalent derived from the 18-karat quote, not a separate live quote.
- Gold and coin prices are also recorded locally for charting.

### 4. Market chart and price history
- A chart is embedded below the calculator rather than being a separate bottom-navigation destination.
- The selected asset can be a currency, 18-karat gold, or Emami coin when its data is available.
- Offers 24-hour, 7-day, and 30-day views.
- History is collected locally after successful refreshes (up to 31 days and a bounded number of samples). It is **not** a complete historical market archive: older points from before the app began collecting data are not available.
- Chart availability and shape depend on the number and timing of locally collected samples.

### 5. News room
- Dedicated news screen with country/region filters, including Oman, Iraq, the United States, Europe, Iran, and other mapped markets.
- RSS sources currently include Al Jazeera, BBC Persian, DW Persian, Radio Farda, and Voice of America. Oman Observer is additionally loaded for the Oman view.
- News items can show their source, publication time, title, and an image when the feed provides one; tapping an item opens the original article.
- Includes an immigration/migration-focused filter and an all-mentions filter.
- Country matching is based on country and city names found in article titles and summaries. These are general feeds, not dedicated country-specific feeds, so the filter can miss relevant stories or include an imperfect match.
- Feed responses are cached locally; refreshes are throttled to avoid repeatedly downloading the same feeds.

### 6. AI market assistant
- Adds a Persian-language conversational market assistant.
- The assistant can receive the app's cached currency prices, gold/coin information, market status, and recent conversation context to answer market-related questions.
- Requests are routed through a Cloudflare Worker backend that calls the OpenAI Responses API; the OpenAI API key belongs in the Worker secret, not in the Android app.
- The Worker includes an IP-based rate-limit binding. Cloudflare rate limiting is approximate and should not be treated as a guaranteed global spending cap.
- AI responses are informational and should not be treated as definitive investment advice.

### 7. Settings, appearance, and navigation
- Dark and light appearance options.
- Language selector for Persian, English, and Arabic, with right-to-left layout direction for Persian and Arabic.
- Dedicated bottom-navigation destinations for Calculator, Currencies, Gold, AI, and Settings; News is opened from the calculator's news shortcut.
- Automatic refresh controls for foreground and background work.
- Refresh feedback for success, failure, and requests made before the next permitted refresh time.

**Localization note:** Language selection and RTL support are implemented, but not every screen and every news/assistant string is fully translated yet. Some areas may still show mixed-language text.

### 8. Home-screen widget and background updates
- Retains the original home-screen currency widget.
- Widget and app use the same stored currency selection and cached market data.
- WorkManager handles scheduled background refreshes; background refresh can be disabled in Settings.
- Android battery optimization may delay background work. On Samsung devices, check that Toomans is not placed in Sleeping apps if timely background updates are important.

## Data sources and update behavior

- **Currency and gold/coin data:** TGJU public market pages, parsed by the app.
- **News:** Public RSS feeds listed above.
- **AI:** Cloudflare Worker calling the OpenAI Responses API.
- Currency refresh attempts have a local one-minute guard. Gold refreshes have their own five-minute guard. News feeds are cached for up to three hours, with a shorter minimum interval for forced refreshes.
- A refresh schedule is not a promise that the source itself publishes new prices at that frequency. The app can only display the latest information available from its sources.

## Build the Android APK

1. Install the current stable Android Studio.
2. Open the repository in Android Studio and allow Gradle sync to complete.
3. Install/accept Android SDK 35 if prompted.
4. Use **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The debug APK is normally written to `app/build/outputs/apk/debug/app-debug.apk`.

Command line (with the Gradle wrapper available):

```sh
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

## AI Worker setup (optional self-hosting)

The Android app currently points to a configured Worker endpoint in `Assistant.kt`. To use your own Worker:

1. Deploy the Worker from the `worker/` directory with Wrangler.
2. Add your OpenAI API key as a Worker secret; never commit it to Git.
3. Update the configured Worker URL in `Assistant.kt`, then build the app.

Example commands:

```sh
npm install -g wrangler@latest
wrangler login
wrangler secret put OPENAI_API_KEY
wrangler deploy
```

See [worker/README.md](worker/README.md) for deployment and rate-limit details.

## Privacy and security notes

- The OpenAI API key should remain in the Cloudflare Worker secret store and must not be embedded in the Android client.
- App preferences and cached market data are stored in app-private local storage.
- Do not share a build containing personal credentials or private configuration.
- News content is fetched from third-party RSS providers and article links open on their original websites.

## Known limitations

- Local chart history cannot show prices from before the app started collecting samples; no full historical archive provider is integrated.
- News country filters depend on matching names in general RSS headlines and summaries; they are not comprehensive country-news search.
- News images appear only when a feed exposes a usable image URL.
- Localization is not yet complete across every screen.
- Background execution can be delayed by Android or manufacturer battery management.
- CI build success confirms compilation and artifact creation only; real-device behavior still needs to be checked on target devices.

## Project structure

- `app/src/main/java/com/example/tomanrates/` — Android app, market repositories, widget, UI, chart, news, and assistant.
- `worker/` — Cloudflare Worker backend for the AI assistant.
- `.github/workflows/build-apk.yml` — GitHub Actions APK build workflow.
