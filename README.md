# Sonnenregen

A small, dependency-free service that answers two questions for a WetterOnline
location:

1. How long until the sun shines — or, if it is shining now, how long until it stops?
2. How long until it rains — or, if it is raining now, how long until it stops?

## How it works

The pages at `https://www.wetteronline.de/wettertrend/<city>` and
`https://www.wetteronline.de/90-minuten-wetter/<city>` are Angular SSR apps. Their
HTML contains an `ng-state` JSON payload with the original forecast data:

- hourly slots for the next 48 hours (`hours`, from the *wettertrend* page),
- 15-minute slots for the next 90 minutes (`nowcast_trend.items`, from the
  *90-minuten-wetter* page),
- the provider's current observation (`current`, including solar elevation).

Reading that payload avoids depending on translated labels, CSS class names and
icon layouts. Each slot has an absolute ISO timestamp and a WetterOnline symbol
code. The service stitches the 15-minute slots to the hourly slots (stretching
the last 15-minute slot over the gap to the next full hour) and walks forward
from the provider's current timestamp to the first slot where the condition
changes.

Definitions used:

- **Sunshine**: symbol `so`/`wb` (sunny or partly cloudy during the day), with
  the current observation additionally requiring solar elevation above the horizon.
- **Rain**: symbol containing a rain/shower marker (`r` or `s`) after the base
  condition, e.g. `mdr1__` (light rain) or `mds1__` (shower). Snow is not counted
  as rain.

Both pages are cached in memory for 5 minutes (`CACHE_TTL` in `sonnenregen/core.py`)
to avoid hammering the provider.

## Run

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
    "at": "2026-11-21T08:00:00+01:00",
    "minutes": 633,
    "summary": "Sunshine starts in 10 h 33 min (2026-11-21 08:00 +0100)."
  },
  "rain": {
    "currently": false,
    "event": "starts",
    "at": "2026-11-21T20:00:00+01:00",
    "minutes": 1353,
    "summary": "Rain starts in 22 h 33 min (2026-11-21 20:00 +0100)."
  },
  "horizon": "...",
  "sources": [".../wettertrend/berlin", ".../90-minuten-wetter/berlin"]
}
```

`event` is `starts` or `ends`, and `minutes` is the rounded duration from the
provider's observation time to that transition. If no transition occurs inside
the forecast horizon, `event`, `at` and `minutes` are `null` and `summary`
explains why.

## Tests

```bash
python -m unittest discover -s tests -v
```

The tests use a trimmed snapshot of the real embedded payload plus small
synthetic forecasts; they never hit the network.

## Android app

The repository also contains a standalone Kotlin/Jetpack Compose prototype in
`app/`. It fetches and parses WetterOnline itself and does not call this Python
service. The UI and notification are localized in English and German (selected
by device locale). Multiple WetterOnline cities can be added and removed in the
app; one ongoing notification shows a compact sun/rain line per city. See
[ANDROID.md](ANDROID.md) for the design and refresh behaviour.

```bash
# Set sdk.dir in local.properties first (the file is gitignored).
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
