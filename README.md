# Trip Scout (Android)

Enter the places you'll visit and the dates you'll be there. For **every day and every place**,
Trip Scout builds a briefing covering weather, safety, events and what's going on locally.

## What it collects

| Data | Source | Key needed |
|---|---|---|
| Place lookup (coordinates, country) | Open-Meteo Geocoding | No |
| Weather: real forecast up to 16 days ahead, then a typical-weather estimate (3-year average) | Open-Meteo forecast and archive | No |
| Public holidays (closures, crowds) | Nager.Date | No |
| Official travel advice and alert level | UK FCDO (GOV.UK content API) | No |
| Community safety tips ("Stay safe") | Wikivoyage | No |
| News: latest, safety-related, events on your dates, local-language news | Google News RSS | No |
| Reddit threads from the last month | Reddit public search | No |
| **Daily safety rating, events, local news, Reddit/forums, blogs, official city sources** | **Claude with web search** | **Anthropic API key** |

Without a key, the app works with the free sources only. With a key (Settings), Claude researches each
place for your exact dates and fills in a per-day safety level, events, happenings and tips.

## Get the APK

There is no local Android SDK needed. GitHub Actions builds the app on every push:

1. Open the repo's **Actions** tab, then the latest **Build APK** run.
2. Download the **TripScout-debug-apk** artifact (a zip containing `app-debug.apk`).
3. Copy it to your phone and install it (allow "Install unknown apps" for your file manager or browser).

The debug APK is signed with a debug key. That's fine for personal use, but not for the Play Store.

## Build locally

You need Android Studio (or the Android SDK with API 36) and JDK 17.

```bash
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Using it

1. Tap **New trip**, name it, and add places with date ranges. Use `City, Country` (e.g. `Kotor, Montenegro`).
2. Tap **Save and research**.
3. **Day by day** tab: each date, each place you're at that day (travel days show both).
4. **Places** tab: overview, official advice, Wikivoyage tips, headlines, Reddit, the sources Claude used,
   and any source that failed (failures are listed, never hidden).

## Claude settings

- Default model: `claude-opus-5-5` (changeable in Settings).
- Up to 10 web searches per place per run. Costs go to your Anthropic account, so set a spend limit
  in the Anthropic Console.
- The API key is stored in the app's private storage on the phone. Use a dedicated key with a low limit.

## Limitations

- Typical weather for dates more than 16 days out is an average of past years, not a forecast.
- Reddit sometimes blocks anonymous requests; this shows as a warning under "Sources with problems".
- Event coverage without Claude is limited to news headlines.
- Reports are kept in memory only. Reopening the app re-runs the research.

## Project layout

```
app/src/main/java/com/muri/tripscout/
  model/     Trip, Stop, report types, JSON storage format
  data/      One class per free source + TripResearcher (orchestration)
  ai/        ClaudeResearcher (Anthropic Java SDK + web search tool)
  storage/   SharedPreferences store for trips and settings
  ui/        Jetpack Compose screens and ViewModel
```
