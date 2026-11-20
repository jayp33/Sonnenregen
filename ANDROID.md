# Sonnenregen Android app

## Goal

Sonnenregen should also be a native Android app showing a permanent notification
with both forecasts for a location (default: Berlin):

- how long until the sun shines, or until the current sunshine ends;
- how long until it rains, or until the current rain ends.

The app fetches and parses the WetterOnline pages itself. It does **not** depend
on the Python HTTP service in this repository. That service remains useful as a
reference implementation and for debugging, but the mobile app is standalone.

## Platform decisions

- **Kotlin + Jetpack Compose**, minSdk 26 (or enable core library desugaring if
  supporting older versions, for `java.time.OffsetDateTime`).
- **WorkManager** for periodic work. A 15-minute interval is the minimum
  supported by WorkManager, and is accepted for this app. This avoids
  foreground-service restrictions entirely.
- No foreground service. `ACTION_USER_PRESENT` / `ACTION_SCREEN_ON` are not
  exempt implicit broadcasts on API 26+, so a manifest receiver cannot reliably
  trigger an immediate refresh. Instead we rely on Doze: WorkManager jobs are
  deferred while the device is in Doze and are run when it becomes active again,
  so a pending 15-minute job normally executes shortly after unlock. This gives
  refresh-on-unlock without an FGS. If truly exact unlock timing is required,
  that is a separate design change involving a foreground service and its
  Android 12+/15+ constraints.
- The notification is still "permanent" in the sense that it is posted as an
  ongoing notification (`setOngoing(true)`); the system keeps it visible even
  when the app process is killed.

## Data source and parsing

The app fetches both pages with OkHttp (or Ktor), concurrently:

```
https://www.wetteronline.de/wettertrend/{city}
https://www.wetteronline.de/90-minuten-wetter/{city}
```

Use a normal browser-like `User-Agent` and `Accept-Language: de-DE`.

Each page is an Angular SSR app. Parse the embedded state with a regex:

```kotlin
Regex("""<script[^>]*\bid=["']ng-state["'][^>]*>(.*?)</script>""",
    RegexOption.IGNORE_CASE or RegexOption.DOTALL)
```

Then:

1. JSON-decode the captured script content with `kotlinx.serialization`
   (`ignoreUnknownKeys = true`).
2. Find the state entry whose key contains `/blending/shortcast/v1?`.
3. Read `current`, `nowcast_trend.items` (15-minute slots) and `hours`
   (1-hour slots). Each item has an ISO-8601 `date` and a `symbol` code.
4. Stitch the 15-minute grid to the hourly grid, as in the Python reference:
   use the fine grid for the near future and stretch its last slot to the next
   full hour.
5. Classify each slot:
   - **sunshine**: symbol starts with `so` or `wb`, and for the current
     observation `solarElevation > 0`;
   - **rain**: symbol contains `r`, `s` or `g` after the first two characters,
     but not snow (`n`).
6. Walk forward from the provider's `current.date` to the first slot where each
   condition changes. Produce `currently`, `event` (`starts` / `ends`), `at`,
   `minutes`, and a human-readable summary.

City slugs are validated with `^[a-z0-9]+(?:-[a-z0-9]+)*$` before building URLs.

## Refresh behaviour

`WeatherWorker` is enqueued as a `PeriodicWorkRequest` with a 15-minute interval
and constraints `NetworkType.CONNECTED`, not requiring charging or idle.

On each run:

1. Fetch and parse both pages. Do **not** skip the run based on
   `PowerManager.isInteractive()`; see the note below.
2. On success, update the notification and persist the result plus timestamp in
   DataStore so it can be restored after reboot or app restart.
3. On failure, keep the last good result, mark the notification as stale (e.g.
   "Updated 25 min ago" or "Weather unavailable"), and let WorkManager retry
   with backoff.

### Why the worker must not skip while the screen is off

An early draft had the worker check `isInteractive()` and do nothing when the
screen was off. That is a staleness bug: WorkManager's periodic run happens at a
wall-clock cadence, so if every screen-on period is short and misses those
moments, the worker can skip indefinitely and the notification never updates.

Doze already provides the desired "idle while screen off" behaviour for the
common case: a phone that is unplugged, stationary and screen-off enters Doze,
WorkManager/JobScheduler defers periodic jobs, and the pending job runs when the
device wakes on unlock. Removing the explicit screen check therefore gives both
properties:

- while the screen is on, data is at most ~15 minutes old (plus normal
  scheduling delay);
- after unlock, the deferred run executes promptly, which is the refresh.

The residual trade-off: if the device is screen-off but *not* in Doze (for
example charging, or being carried around), the periodic job may still run and
fetch. We accept an occasional background refresh in that state rather than
risking stale data. Strictly forbidding it would require a foreground service
with screen-state receivers.

A "Refresh" notification action enqueues a one-off `OneTimeWorkRequest` for
manual refreshes.

## Notification

- Channel `weather`, `IMPORTANCE_LOW`, category `Notification.CATEGORY_WEATHER`.
- `setOngoing(true)`, small icon, title = city.
- `BigTextStyle` with two lines:
  - `Sun: Sunshine starts in 10 h 30 min (08:00).`
  - `Rain: Rain starts in 22 h 30 min (20:00).`
- Freshness: store `lastSuccessfulAt` in DataStore on every successful fetch.
  Call `setWhen(lastSuccessfulAt)` and `setShowWhen(true)` so the system shows
  when the data was fetched. For a live "time since update" indicator, also use
  `setUsesChronometer(true)`: SystemUI then renders an automatically ticking
  elapsed time since that timestamp, with no extra work from the app.
  Do **not** put a literal `"Updated 25 min ago"` string in `BigTextStyle` —
  notification text is a snapshot and would freeze until the next WorkManager
  run, and WorkManager cannot tick more often than every 15 minutes. An absolute
  `Updated 14:32` line in the text is fine if a fixed label is preferred.
- Actions: **Refresh** and **Open app**.
- On Android 13+ request `POST_NOTIFICATIONS` on first launch.

## Lifecycle

- A `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` receiver re-enqueues the periodic
  work and reposts the last known notification. These broadcasts are exempt
  from Android's implicit-broadcast restrictions.
- A setting in the app enables/disables updates; disabling cancels the work and
  removes the notification.
- After a user force-stops the app, Android will not restart it until the user
  opens it again; this is accepted behaviour.

## Error handling

- Keep the last successful forecast when a fetch or parse fails.
- Handle: network errors, HTTP errors, missing `ng-state`, invalid JSON, missing
  shortcast key, empty forecast, and unknown symbol codes.
- Log parse errors with the symbol code / URL so markup changes are detectable.
- Avoid duplicate concurrent fetches; WorkManager already serialises the work.

## Suggested source layout

```
app/src/main/java/de/jayp33/sonnenregen/
  data/WetterOnlineClient.kt
  data/ForecastParser.kt
  domain/Models.kt
  domain/ForecastAnalyzer.kt
  work/WeatherWorker.kt
  notification/WeatherNotifier.kt
  receiver/BootReceiver.kt
  ui/MainActivity.kt
  ui/WeatherScreen.kt
```

Key dependencies: WorkManager, OkHttp, kotlinx.serialization, DataStore,
Jetpack Compose, AndroidX Core KTX.

## Open questions / out of scope

- Multiple cities or location-based city detection.
- Notification icons / small weather images.
- Exact sunrise/sunset times (the hourly data only gives an approximate "08:00
  sunshine starts").
- WetterOnline terms of service and attribution requirements for scraping.
- Making the instant-unlock behaviour exact (would require revisiting the
  foreground-service decision).
