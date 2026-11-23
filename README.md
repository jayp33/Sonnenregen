# Sonnenregen

Sonnenregen is a standalone Android app that keeps a compact sun/rain forecast in
a permanent notification. It reads WetterOnline directly — no backend, account,
or API key.

For each configured city it answers:

1. How long until the sun shines — or, if it is shining now, how long until it stops?
2. How long until it rains — or, if it is raining now, how long until it stops?

## Android app

The app is the main deliverable of this repository. It is written in Kotlin with
Jetpack Compose (`minSdk 26`, `compileSdk`/`targetSdk 36`) and works in English
and German, selected by device locale.

**Highlights**

- **Permanent notification** with a compact `Sun in 2 h 10 min, no rain` line.
  The sooner event comes first. With one city the place name is the notification
  header; with two or more cities a wrapping text block shows `City: summary`
  lines in your chosen city order. The body always wraps, so long city names or
  summaries don't hide the forecast on narrow screens. Per-city **Show in
  notification** switches keep the app overview broad while the notification
  stays concise. If rain resumes inside the 90-minute window, the rain phrase
  gains `(again in 45 min)`. Tapping the notification opens the app directly;
  the only action is **Refresh**.
- **Per-city sun/rain alerts**: each city's details offer optional **Notify when
  sun starts** and **Notify when rain starts** switches, both off by default.
  They post separate, auto-cancelling notifications on a dedicated channel when
  a fresh forecast changes from no sun/rain to sun/rain; ongoing conditions never
  re-alert. Detection happens on the 15-minute refresh cycle.
- **Multiple cities**: add or remove WetterOnline slugs (`berlin`, `hamburg`, …)
  in the app. Slugs stay lowercase for URLs; the app shows the real place name
  from WetterOnline (`frankfurt-oder` → `Frankfurt (Oder)`,
  `michelbach-an-der-bilz/gschlachtenbretzingen` → `Gschlachtenbretzingen`).
  For places with a two-part WetterOnline path, enter the whole path. Use the
  ↑/↓ controls in a city’s details to change the order used by the UI and the
  notification.
- **48-hour colour overview**: one row per day, prefixed with its short weekday
  (`Mo`/`Di`/…), split into four six-hour blocks in the city's own local time.
  Yellow means sun, blue means rain, and one very light gray covers clouds and
  all night hours — so sun and rain pop out at a glance without scrolling or
  icons. The grid sits right under the city name. Past hours are blanked out as
  the clock advances when a refresh fails; their slots stay in place, and a fully
  stale grid shows a `48 h outdated` hint. Tap the city line or grid to
  reveal the detailed Sun/Rain, sunrise/sunset and Remove controls; tap again to
  hide them. The controls also work for cities without a forecast, so broken
  places can be removed. Adding a city happens in a dialog behind a small + button;
  its input field takes focus right away so typing can start immediately.
  When sun or rain is expected in the next 90 minutes, an extra six-cell row
  prefixed `90` appears underneath with 15-minute detail in the same colours; each cell
  carries its start time (`21:15`, `21:30`, …). Elapsed cells drop out as the clock
  advances, even when the last refresh failed; once none remain, the row stays
  visible with an `outdated` hint.
- **Exact sunrise/sunset** from WetterOnline's own astro data, shown in the app
  UI. Sunshine never starts before sunrise or continues past sunset.
- **Offline-friendly**: each city keeps its last good forecast. A failed refresh
  marks only that city `Weather unavailable` instead of wiping the others.
- **Freshness at a glance**: a global `Updated x min ago` under the title, plus a
  per-city one inside each collapsed details block. Both tick live.
- **Battery-friendly**: WorkManager refreshes every 15 minutes while connected.
  There is no foreground service and no exact unlock-event detection; Doze
  defers work and pending jobs catch up after unlock. The notification's
  chronometer shows the age of the last successful fetch.
- **Localized**: English/German UI, including `Std.`/`Min.` durations in German.

Example notifications:

```text
Berlin
Sun in 2 h 10 min, no rain
```

```text
Sonnenregen
Berlin: Sun in 2 h 10 min, no rain
Hamburg: Rain in 30 min, sun in 5 h
```

### Build and install

```bash
# Set sdk.dir in local.properties first (the file is gitignored).
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See
[ANDROID.md](ANDROID.md) for the full design and refresh behaviour.

To share a debug build via GitHub, use the upload script:

```bash
# finds a JDK automatically (Android Studio's JBR on this machine)
scripts/upload-debug-apk.sh                  # tag = local YYYYMMDD-HHMMSS
scripts/upload-debug-apk.sh v0.1.0-debug     # or choose your own tag
```

It builds the APK, creates the pre-release if needed and uploads it as
`sonnenregen-<tag>.apk`. Without an argument the timestamp includes seconds, so
each run gets its own release. Reusing an explicit tag replaces that release's
APK. Tags are free-form, so `v0.1.0-debug-2` and `20261121-211942` both work.
New releases are created on GitHub's default branch; the script warns if local
commits or edits are not on GitHub.

## How WetterOnline data is read

Both the app and the Python helper use the same approach. The pages at
`https://www.wetteronline.de/wettertrend/<city>` and
`https://www.wetteronline.de/90-minuten-wetter/<city>` (where `<city>` can also be
a nested slug such as `michelbach-an-der-bilz/gschlachtenbretzingen`) are Angular
SSR apps whose
HTML embeds an `ng-state` JSON payload with the original forecast data:

- hourly slots for the next 48 hours (`hours`),
- 15-minute slots for the next 90 minutes (`nowcast_trend.items`),
- the provider's current observation (`current`, including solar elevation),
- optional exact sunrise/sunset times (`astro/days`).

Reading that payload avoids depending on translated labels, CSS class names and
icon layouts. The 15-minute slots are stitched to the hourly slots, then the
timeline is walked from the provider's current timestamp to the first condition
change.

Definitions used:

- **Sunshine**: symbol `so`/`wb` (sunny or partly cloudy during the day). The
  timeline is split at sunrise/sunset; on the daylight side of sunrise a night
  symbol is converted to its day counterpart (`mo` → `so`, `mb` → `wb`), so a
  clear night starts sunshine at the *exact* sunrise instead of at the next
  hourly grid boundary.
- **Rain**: symbol containing a rain/shower marker (`r`, `s` or `g`) after the
  base condition, e.g. `mdr1__` (light rain) or `mds1__` (shower). Snow (`n`) is
  not counted as rain.

When no astro data is available (e.g. polar day/night), the symbol and solar
elevation rules are used unchanged.

## Python helper service (optional)

The original dependency-free Python service in `sonnenregen/` is kept as a small
CLI/HTTP tool for inspecting the same data while debugging. **The Android app
does not call it.** Pages are cached in memory for 5 minutes
(`CACHE_TTL` in `sonnenregen/core.py`) to avoid hammering the provider.

```bash
python -m sonnenregen.server                 # http://127.0.0.1:8080/?city=berlin
HOST=0.0.0.0 PORT=9000 python -m sonnenregen.server

python -m sonnenregen.cli berlin             # pretty JSON
python -m sonnenregen.cli berlin --human     # two human-readable lines
```

The HTTP service exposes `GET /` (alias `/weather`) with `?city=<slug>`, and
`GET /health`. The default city is `berlin`. Errors are returned as JSON
(`400` for an invalid slug, `502` if the provider cannot be read).

Example response (abbreviated):

```json
{
  "city": "berlin",
  "now": "2026-11-20T21:26:32+01:00",
  "timezone": "Europe/Berlin",
  "sun": {
    "currently": false,
    "event": "starts",
    "at": "2026-11-21T07:38:00+01:00",
    "minutes": 611,
    "summary": "Sunshine starts in 10 h 11 min (2026-11-21 07:38 +0100)."
  },
  "rain": {
    "currently": false,
    "event": "starts",
    "at": "2026-11-21T20:00:00+01:00",
    "minutes": 1353,
    "summary": "Rain starts in 22 h 33 min (2026-11-21 20:00 +0100)."
  },
  "horizon": "...",
  "sunrise": "2026-11-21T07:38:00+01:00",
  "sunset": "2026-11-21T16:05:00+01:00",
  "sources": [".../wettertrend/berlin", ".../90-minuten-wetter/berlin"]
}
```

`event` is `starts` or `ends`, and `minutes` is the rounded duration from the
provider's observation time to that transition. If no transition occurs inside
the forecast horizon, `event`, `at` and `minutes` are `null` and `summary`
explains why.

## Tests

Both suites are offline and use trimmed snapshots of the real embedded payload
plus small synthetic forecasts.

```bash
python -m unittest discover -s tests -v
./gradlew :app:testDebugUnitTest
```

## Project layout

```text
app/            Android Kotlin/Compose app (the main deliverable)
sonnenregen/    Python CLI/HTTP helper service
tests/          Python tests and fixtures
ANDROID.md      Android design and refresh notes
```
