# Toomans — Android Currency & Market App

Toomans is an Android app and home-screen widget for tracking Iranian free-market currency prices, gold and coin quotes, market movements, and related news. It has grown from a lightweight currency-rate widget into a multi-section market companion.

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
- WorkManager schedules background refresh work; background refresh can be disabled in Settings.
- Android battery optimization may delay background work. On Samsung devices, check that Toomans is not placed in Sleeping apps if timely background updates are important.

## How it works

1. **Fetch market data:** The app requests public currency and gold/coin market pages from TGJU. It parses the available quotes and market details into structured data. No Alanchand API token is required by the current market-data implementation.
2. **Save a local snapshot:** After a successful currency update, the app stores the latest response in private app storage and records a local price-history sample. Gold and coin quotes are cached separately and are also added to local history.
3. **Display data with its status:** The calculator, currency list, gold section, chart, and widget read from the locally stored data. The app distinguishes live, delayed, and unavailable currency data so an old cached quote is not silently presented as a fresh update.
4. **Refresh at controlled intervals:** Currency refresh attempts are guarded by a one-minute local interval. Gold has a separate five-minute refresh guard. While the app is open, automatic refresh checks run about once per minute when enabled; WorkManager also schedules background work. Android may delay background execution, and source updates may be less frequent than app checks.
5. **Build the chart from samples collected on this device:** Price history is recorded as the app successfully receives quotes. The chart offers 24-hour, 7-day, and 30-day windows, but it cannot display historical prices from before collection began. A smooth or populated chart requires enough real samples over time.
6. **Load news from RSS:** The news section downloads public RSS feeds, caches their contents on the device, and filters the latest items by country/region names found in headlines and summaries. Oman Observer is included in the Oman feed view. Selecting an article opens the publisher's original page; images appear only when a feed provides a usable image.
7. **Send AI questions through the Worker:** The assistant sends the user's question, recent chat context, and the app's available cached market snapshot to the configured Cloudflare Worker. The Worker communicates with the OpenAI API and returns the assistant's response. The API key is kept on the Worker side rather than inside the Android app.

### Current update intervals and limitations
- Currency: one-minute minimum interval between requests.
- Gold/coin: five-minute refresh guard.
- News: cached for up to three hours; a manual refresh is also throttled.
- Background work is best-effort and can be delayed by Android battery management.
- The chart is based on locally collected samples, not a complete third-party historical price feed.
- Country news filters search general RSS content; they are not guaranteed to find every story about a country.

## Install the app

To install Toomans on an Android phone:

1. Download the latest successful **TomanRates-debug-apk** artifact from the repository's [GitHub Actions](https://github.com/Soodek/Toomans/actions) page. Open the latest successful run for the development branch, then download the artifact named **TomanRates-debug-apk**.
2. Extract the downloaded ZIP file. Inside it, locate **app-debug.apk**.
3. Transfer the APK to your Android phone if you downloaded it on another device.
4. Open the APK using Files / My Files and approve **Install unknown apps** for that app if Android asks.
5. Tap **Install**, then open Toomans.
6. To add the currency widget, long-press an empty area of the home screen, open **Widgets**, find Toomans / Toman Rates, and place the widget.

**Requirements:** Android 8.0 (API 26) or newer and an internet connection for current market prices, news, and AI features.

**Updating an existing installation:** Install the newer APK over the existing app when Android accepts its signing certificate. If Android reports a signature or package conflict, the APK was signed with a different key. Uninstalling the old app may then be required before installing the new one; uninstalling erases the app's locally stored settings, cached data, and chart history.

## AI Worker setup (optional self-hosting)

The Android app currently points to a configured Worker endpoint in `Assistant.kt`. To use your own Worker:

1. Deploy the Worker from the `worker/` directory with Wrangler.
2. Add your OpenAI API key as a Worker secret; never commit it to Git.
3. Update the configured Worker URL in `Assistant.kt` in a developer build.

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
