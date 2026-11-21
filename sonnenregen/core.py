"""Fetch WetterOnline pages, read their embedded forecast data and answer
"when will the sun shine?" / "when will it rain?".

The pages are Angular SSR applications.  Their rendered HTML is only a view of
the data; the original API payloads are embedded in an ``ng-state`` JSON
script.  Reading that payload gives us absolute ISO timestamps and stable
weather-symbol codes instead of having to match translated CSS classes.
"""

from __future__ import annotations

import json
import re
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace
from datetime import datetime, timedelta
from typing import Callable, Sequence
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo

BASE_URL = "https://www.wetteronline.de"
HOURLY_PATH = "/wettertrend/{city}"
NOWCAST_PATH = "/90-minuten-wetter/{city}"
DEFAULT_CITY = "berlin"
LOCAL_TZ = ZoneInfo("Europe/Berlin")

REQUEST_TIMEOUT = 15.0
CACHE_TTL = 300.0
USER_AGENT = "sonnenregen/0.1 (weather forecast reader)"

CITY_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*(?:/[a-z0-9]+(?:-[a-z0-9]+)*)*$")
NG_STATE_RE = re.compile(
    r"<script[^>]*\bid=[\"']ng-state[\"'][^>]*>(.*?)</script>", re.IGNORECASE | re.DOTALL
)
SHORTCAST_KEY = "/blending/shortcast/v1?"
ASTRO_KEY = "/astro/days/v1?"

# WetterOnline symbol codes: first two characters are the base condition.
# "so" = sun, "wb" = wechselnd bewölkt (sun and clouds, day only).
SUN_SYMBOL_PREFIXES = ("so", "wb")
# Characters after the base condition that denote rain, showers or thunderstorms.
RAIN_MARKERS = frozenset("rsg")

# WetterOnline gives an hourly slot a night symbol when it starts before
# sunrise, even if the sky is clear at sunrise. Use the day equivalent on the
# daylight side of the cut so sunshine starts at the real sunrise time.
NIGHT_TO_DAY = {
    "mo": "so",  # klar -> sonnig
    "mb": "wb",  # wechselnd bewölkt (night) -> wechselnd bewölkt (day)
    "mw": "bw",  # bewölkt (night) -> bewölkt (day)
    "md": "bd",  # stark bewölkt (night) -> stark bewölkt (day)
}


class SourceError(RuntimeError):
    """The weather website could not be fetched."""


class ParseError(RuntimeError):
    """The weather website did not contain the expected forecast data."""


@dataclass(frozen=True)
class Slot:
    """One forecast interval."""

    start: datetime
    duration: timedelta
    symbol: str
    source: str
    solar_elevation: float | None = None
    night: bool | None = None
    daylight: bool | None = None

    @property
    def end(self) -> datetime:
        return self.start + self.duration

    @property
    def sunny(self) -> bool:
        if self.daylight is False:
            return False
        if self.night is True or (self.solar_elevation is not None and self.solar_elevation <= 0):
            return False
        return is_sun_symbol(self.symbol)

    @property
    def raining(self) -> bool:
        return is_rain_symbol(self.symbol)


@dataclass(frozen=True)
class Change:
    """The next transition of one condition."""

    currently: bool
    event: str | None  # "starts" or "ends"
    at: datetime | None
    minutes: int | None
    label: str
    summary: str

    def as_dict(self) -> dict:
        return {
            "currently": self.currently,
            "event": self.event,
            "at": self.at.isoformat() if self.at else None,
            "minutes": self.minutes,
            "summary": self.summary,
        }


@dataclass(frozen=True)
class Answer:
    city: str
    now: datetime
    timezone: str
    sun: Change
    rain: Change
    sources: tuple[str, ...]
    horizon: datetime | None
    sunrise: datetime | None = None
    sunset: datetime | None = None

    def as_dict(self) -> dict:
        return {
            "city": self.city,
            "now": self.now.isoformat(),
            "timezone": self.timezone,
            "sun": self.sun.as_dict(),
            "rain": self.rain.as_dict(),
            "horizon": self.horizon.isoformat() if self.horizon else None,
            "sunrise": self.sunrise.isoformat() if self.sunrise else None,
            "sunset": self.sunset.isoformat() if self.sunset else None,
            "sources": list(self.sources),
        }

    def text(self) -> str:
        return f"Sun: {self.sun.summary}\nRain: {self.rain.summary}"


def is_sun_symbol(symbol: str) -> bool:
    """True for symbols that show the sun (clear or with a few clouds)."""
    return symbol.startswith(SUN_SYMBOL_PREFIXES)


def is_rain_symbol(symbol: str) -> bool:
    """True for symbols showing rain, showers or thunderstorms (not snow)."""
    detail = symbol[2:]
    if "n" in detail:  # snow
        return False
    return any(char in RAIN_MARKERS for char in detail)


def _parse_timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=LOCAL_TZ)
    return parsed


def _decode(response) -> str:
    charset = response.headers.get_content_charset() or "utf-8"
    return response.read().decode(charset, errors="replace")


_CACHE: dict[str, tuple[float, str]] = {}


def fetch_page(url: str, *, timeout: float = REQUEST_TIMEOUT, cache_ttl: float = CACHE_TTL) -> str:
    """Fetch a page with a small in-process TTL cache."""
    cached = _CACHE.get(url)
    if cached and cached[0] > time.monotonic():
        return cached[1]
    request = Request(
        url,
        headers={"User-Agent": USER_AGENT, "Accept-Language": "de-DE,de;q=0.9"},
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            body = _decode(response)
    except Exception as exc:  # network, timeout, HTTP errors
        raise SourceError(f"Could not fetch {url}: {exc}") from exc
    if cache_ttl > 0:
        _CACHE[url] = (time.monotonic() + cache_ttl, body)
    return body


def clear_cache() -> None:
    """Drop all cached pages (useful for tests and long-running processes)."""
    _CACHE.clear()


def extract_state(page: str) -> dict:
    match = NG_STATE_RE.search(page)
    if not match:
        raise ParseError("No ng-state payload found in the page")
    try:
        state = json.loads(match.group(1))
    except json.JSONDecodeError as exc:
        raise ParseError(f"Invalid ng-state payload: {exc}") from exc
    if not isinstance(state, dict):
        raise ParseError("Unexpected ng-state payload")
    return state


def extract_shortcast(state: dict) -> dict:
    for key, value in state.items():
        if SHORTCAST_KEY in key and isinstance(value, dict):
            return value
    raise ParseError("No shortcast data found in the ng-state payload")


def extract_astro(state: dict) -> dict | None:
    """Optional exact sunrise/sunset data from the same embedded payload."""
    for key, value in state.items():
        if ASTRO_KEY in key and isinstance(value, dict):
            return value
    return None


def daylight_windows(astro: dict | None) -> list[tuple[datetime, datetime]]:
    windows: list[tuple[datetime, datetime]] = []
    for day in (astro or {}).get("days") or []:
        sun = day.get("sun") or {}
        if not sun.get("rise") or not sun.get("set"):
            continue  # polar day/night
        try:
            rise = _parse_timestamp(sun["rise"])
            set_time = _parse_timestamp(sun["set"])
        except (TypeError, ValueError):
            continue
        if set_time > rise:
            windows.append((rise, set_time))
    return sorted(windows)


def apply_daylight(
    timeline: Sequence[Slot],
    windows: Sequence[tuple[datetime, datetime]],
) -> list[Slot]:
    """Split slots at sunrise/sunset and mark which parts are in daylight."""
    if not windows or not timeline:
        return list(timeline)

    cuts = [
        moment
        for rise, set_time in windows
        for moment in (rise, set_time)
        if timeline[0].start < moment < timeline[-1].end
    ]
    split: list[Slot] = []
    for slot in timeline:
        start = slot.start
        for cut in cuts:
            if start < cut < slot.end:
                split.append(replace(slot, start=start, duration=cut - start))
                start = cut
        split.append(replace(slot, start=start, duration=slot.end - start))

    result: list[Slot] = []
    for slot in split:
        in_daylight = any(rise <= slot.start < set_time for rise, set_time in windows)
        if in_daylight:
            base = slot.symbol[:2]
            day_symbol = NIGHT_TO_DAY.get(base, base) + slot.symbol[2:]
            result.append(
                replace(
                    slot,
                    symbol=day_symbol,
                    daylight=True,
                    night=None,
                    solar_elevation=None,
                ),
            )
        else:
            result.append(replace(slot, daylight=False))
    return result


def _slots_from_shortcast(shortcast: dict) -> tuple[list[Slot], list[Slot], Slot | None]:
    hourly: list[Slot] = []
    for hour in shortcast.get("hours") or []:
        if not hour.get("date") or not hour.get("symbol"):
            continue
        hourly.append(
            Slot(
                start=_parse_timestamp(hour["date"]),
                duration=timedelta(hours=1),
                symbol=hour["symbol"],
                source="hourly",
            )
        )

    nowcast: list[Slot] = []
    trend = shortcast.get("nowcast_trend") or {}
    for item in trend.get("items") or []:
        if not item.get("date") or not item.get("symbol"):
            continue
        night = item.get("weather_condition_image") == "night"
        nowcast.append(
            Slot(
                start=_parse_timestamp(item["date"]),
                duration=timedelta(minutes=15),
                symbol=item["symbol"],
                source="nowcast",
                night=night,
            )
        )

    current_data = shortcast.get("current") or {}
    current: Slot | None = None
    if current_data.get("date") and current_data.get("symbol"):
        current = Slot(
            start=_parse_timestamp(current_data["date"]),
            duration=timedelta(minutes=15),
            symbol=current_data["symbol"],
            source="current",
            solar_elevation=current_data.get("solar_elevation"),
            night=current_data.get("weather_condition_image") == "night",
        )

    hourly.sort(key=lambda slot: slot.start)
    nowcast.sort(key=lambda slot: slot.start)
    return hourly, nowcast, current


def build_timeline(
    now: datetime,
    hourly: Sequence[Slot],
    nowcast: Sequence[Slot],
    current: Slot | None,
) -> list[Slot]:
    """Create one continuous timeline: current -> 15-minute -> hourly slots.

    The hourly grid overlaps the 90-minute grid.  We use the finer grid for
    the near future and stitch it to the first hourly slot that starts after
    it; the final 15-minute slot is stretched to cover the gap.
    """
    timeline: list[Slot] = []
    if nowcast:
        last_nowcast = max(slot.start for slot in nowcast)
        following_hourly = next((slot for slot in hourly if slot.start > last_nowcast), None)
        timeline = [slot for slot in nowcast if slot.end > now]
        if not timeline:  # stale 90-minute payload: fall back to hourly data
            timeline = [slot for slot in hourly if slot.end > now]
        elif following_hourly:
            timeline[-1] = replace(
                timeline[-1],
                duration=following_hourly.start - timeline[-1].start,
            )
            timeline.extend(slot for slot in hourly if slot.start >= following_hourly.start)
        else:
            timeline.extend(slot for slot in hourly if slot.start > last_nowcast)
    else:
        timeline.extend(slot for slot in hourly if slot.end > now)

    if current and (not timeline or current.start < timeline[0].start):
        duration = timeline[0].start - current.start if timeline else timedelta(hours=1)
        if duration > timedelta(0):
            timeline.insert(0, replace(current, duration=duration))
    elif current and timeline and current.start < timeline[0].end:
        # The provider's "current" observation is fresher than the 15-minute
        # slot it falls in: keep the slot boundaries but use its conditions.
        first = timeline[0]
        timeline[0] = replace(current, start=first.start, duration=first.duration)

    # Remove overlaps and stale slots, then make sure "now" has a slot.
    cleaned: list[Slot] = []
    for slot in sorted(timeline, key=lambda slot: slot.start):
        if slot.duration <= timedelta(0):
            continue
        if cleaned and slot.start < cleaned[-1].end:
            slot = replace(slot, start=cleaned[-1].end, duration=slot.end - cleaned[-1].end)
            if slot.duration <= timedelta(0):
                continue
        cleaned.append(slot)

    if not cleaned:
        raise ParseError("No forecast slots available")
    if now < cleaned[0].start:
        cleaned.insert(0, Slot(now, cleaned[0].start - now, cleaned[0].symbol, "interpolated"))
    return cleaned


def _current_index(now: datetime, timeline: Sequence[Slot]) -> int:
    for index, slot in enumerate(timeline):
        if slot.start <= now < slot.end:
            return index
    return 0


def _change(now: datetime, timeline: Sequence[Slot], condition: Callable[[Slot], bool], label: str) -> Change:
    index = _current_index(now, timeline)
    currently = condition(timeline[index])
    event = "ends" if currently else "starts"
    wanted = not currently
    target = next((slot for slot in timeline[index + 1 :] if condition(slot) is wanted), None)
    if target is None:
        if currently:
            summary = f"{label} is not expected to end within the forecast horizon."
        else:
            summary = f"No {label.lower()} expected within the forecast horizon."
        return Change(currently, None, None, None, label, summary)

    minutes = int(round((target.start - now).total_seconds() / 60))
    summary = f"{label} {event} in {format_minutes(minutes)} ({target.start:%Y-%m-%d %H:%M %z})."
    return Change(currently, event, target.start, minutes, label, summary)


def format_minutes(minutes: int) -> str:
    sign = "-" if minutes < 0 else ""
    minutes = abs(minutes)
    hours, rest = divmod(minutes, 60)
    if hours and rest:
        return f"{sign}{hours} h {rest} min"
    if hours:
        return f"{sign}{hours} h"
    return f"{sign}{rest} min"


def analyze(
    city: str = DEFAULT_CITY,
    *,
    now: datetime | None = None,
    fetcher: Callable[[str], str] = fetch_page,
) -> Answer:
    """Return sun/rain timings for a WetterOnline city slug."""
    city = (city or DEFAULT_CITY).strip().lower()
    if not CITY_RE.fullmatch(city):
        raise ValueError(f"Invalid city slug: {city!r}")

    hourly_url = f"{BASE_URL}{HOURLY_PATH.format(city=city)}"
    nowcast_url = f"{BASE_URL}{NOWCAST_PATH.format(city=city)}"
    with ThreadPoolExecutor(max_workers=2) as pool:
        hourly_future = pool.submit(fetcher, hourly_url)
        nowcast_future = pool.submit(fetcher, nowcast_url)
        hourly_page = hourly_future.result()
        nowcast_page = nowcast_future.result()

    hourly_state = extract_state(hourly_page)
    hourly_shortcast = extract_shortcast(hourly_state)
    nowcast_shortcast = extract_shortcast(extract_state(nowcast_page))
    windows = daylight_windows(extract_astro(hourly_state))
    hours, nearcast, current = _slots_from_shortcast(hourly_shortcast)
    _, nowcast_items, nowcast_current = _slots_from_shortcast(nowcast_shortcast)
    if not nowcast_items:
        nowcast_items = nearcast
    if current is None:
        current = nowcast_current
    if not hours:
        raise ParseError("The hourly forecast is empty")

    reference = now or (current.start if current else datetime.now(LOCAL_TZ))
    if reference.tzinfo is None:
        reference = reference.replace(tzinfo=LOCAL_TZ)
    timeline = build_timeline(reference, hours, nowcast_items, current)
    sun_timeline = apply_daylight(timeline, windows)

    return Answer(
        city=city,
        now=reference,
        timezone=str(LOCAL_TZ),
        sun=_change(reference, sun_timeline, lambda slot: slot.sunny, "Sunshine"),
        rain=_change(reference, timeline, lambda slot: slot.raining, "Rain"),
        sources=(hourly_url, nowcast_url),
        horizon=timeline[-1].end,
        sunrise=next((rise for rise, _ in windows if rise >= reference), None),
        sunset=next((set_time for _, set_time in windows if set_time >= reference), None),
    )
