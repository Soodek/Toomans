# Toomans

An Android currency and market app for tracking Iranian free-market exchange rates, gold, coins, and related news — with a home-screen widget.

## Features

- **Currency calculator:** Convert between supported currencies and manage your currency list.
- **Live market data:** Currency, 18K gold, and Emami coin prices from public TGJU pages, with cached data and update status.
- **Market charts:** 24-hour, 7-day, and 30-day views based on price samples collected locally by the app.
- **News:** Country and region filters, including Oman, with sources such as Al Jazeera, BBC Persian, DW, Radio Farda, VOA, and Oman Observer. Articles open on their original websites.
- **AI assistant:** Persian-language market Q&A using available cached market data.
- **Personalization:** Persian, English, and Arabic; dark and light themes; RTL support.
- **Home-screen widget:** Displays selected currency rates and uses the app's cached data.

## Install

1. Open [GitHub Actions](https://github.com/Soodek/Toomans/actions).
2. Select the latest successful build from the development branch.
3. Download the **TomanRates-debug-apk** artifact and extract the ZIP.
4. Install `app-debug.apk` on your Android phone. Allow installation from that source if Android asks.

**Requirements:** Android 8.0 (API 26) or newer. Internet access is needed for market updates, news, and AI features.

## Important notes

- Currency refresh attempts have a one-minute minimum interval; gold has a five-minute guard. Android may delay background updates.
- Charts use data collected on your device. They do not include a complete historical price archive.
- News filters rely on matching locations in general RSS feeds, so they may miss relevant stories. Images are shown only when provided by a feed.
- Some screens may still contain untranslated text.
- AI answers are informational, not investment advice.

## AI backend (optional)

The assistant uses a Cloudflare Worker to communicate with the OpenAI API. Keep the API key in the Worker secret store — never embed it in the Android app. See [worker/README.md](worker/README.md) for setup instructions.

## Project structure

- `app/` — Android application and widget.
- `worker/` — AI assistant backend.
- `.github/workflows/` — APK build workflow.
