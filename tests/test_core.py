from __future__ import annotations

import json
import unittest
from datetime import datetime, timedelta
from pathlib import Path

from sonnenregen.core import (
    BASE_URL,
    HOURLY_PATH,
    NOWCAST_PATH,
    _slots_from_shortcast,
    analyze,
    build_timeline,
    extract_shortcast,
    extract_state,
    is_rain_symbol,
    is_sun_symbol,
    Slot,
)

FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "shortcast.json").read_text())
STATE = {"https://api-web.wo-cloud.com/blending/shortcast/v1?test": FIXTURE}
REAL_PAGE = f'<script id="ng-state" type="application/json">{json.dumps(STATE)}</script>'
PAGES = {
    BASE_URL + HOURLY_PATH.format(city="berlin"): REAL_PAGE,
    BASE_URL + NOWCAST_PATH.format(city="berlin"): REAL_PAGE,
}


def make_state(hours: list[tuple[str, str]], items: list[tuple[str, str]], current: tuple[str, str]) -> str:
    payload = {
        "hours": [{"date": date, "symbol": symbol} for date, symbol in hours],
        "nowcast_trend": {
            "items": [{"date": date, "symbol": symbol} for date, symbol in items]
        },
        "current": {"date": current[0], "symbol": current[1], "solar_elevation": 20.0},
    }
    state = {"https://api-web.wo-cloud.com/blending/shortcast/v1?test": payload}
    return f'<script id="ng-state" type="application/json">{json.dumps(state)}</script>'


class SymbolTests(unittest.TestCase):
    def test_sun_symbols(self) -> None:
        self.assertTrue(is_sun_symbol("so____"))
        self.assertTrue(is_sun_symbol("wb____"))
        self.assertFalse(is_sun_symbol("mo____"))
        self.assertFalse(is_sun_symbol("bw____"))
        self.assertFalse(is_sun_symbol("mdr1__"))

    def test_sun_below_horizon(self) -> None:
        now = datetime.fromisoformat("2026-01-01T10:00:00+01:00")
        slot = Slot(now, timedelta(minutes=15), "so____", "current", solar_elevation=-2.0)
        self.assertFalse(slot.sunny)

    def test_rain_symbols(self) -> None:
        self.assertTrue(is_rain_symbol("mdr1__"))
        self.assertTrue(is_rain_symbol("mds1__"))
        self.assertTrue(is_rain_symbol("bdr2__"))
        self.assertFalse(is_rain_symbol("so____"))
        self.assertFalse(is_rain_symbol("mo____"))
        self.assertFalse(is_rain_symbol("wb____"))
        self.assertFalse(is_rain_symbol("mdsn1_"))  # snow shower


class ParsingTests(unittest.TestCase):
    def test_real_payload_is_read(self) -> None:
        shortcast = extract_shortcast(extract_state(REAL_PAGE))
        self.assertEqual(len(shortcast["hours"]), 49)
        self.assertEqual(len(shortcast["nowcast_trend"]["items"]), 7)
        self.assertEqual(shortcast["current"]["symbol"], "mo____")

    def test_stale_nowcast_falls_back_to_hourly(self) -> None:
        hours, nowcast, current = _slots_from_shortcast(FIXTURE)
        assert current is not None
        later = max(slot.end for slot in nowcast) + timedelta(minutes=5)
        timeline = build_timeline(later, hours, nowcast, current)
        self.assertTrue(any(slot.source == "hourly" for slot in timeline))
        self.assertLessEqual(timeline[0].start, later)

    def test_timeline_is_continuous(self) -> None:
        hours, nowcast, current = _slots_from_shortcast(FIXTURE)
        assert current is not None
        timeline = build_timeline(current.start, hours, nowcast, current)
        for previous, following in zip(timeline, timeline[1:]):
            self.assertEqual(previous.end, following.start)
        self.assertEqual(timeline[0].source, "current")


class AnalyzeTests(unittest.TestCase):
    def test_real_forecast_waits_for_sun_and_rain(self) -> None:
        answer = analyze("berlin", fetcher=lambda url: PAGES[url])
        self.assertEqual(answer.city, "berlin")
        self.assertFalse(answer.sun.currently)
        self.assertFalse(answer.rain.currently)
        self.assertEqual(answer.sun.event, "starts")
        self.assertEqual(answer.sun.at.isoformat(), "2026-11-21T08:00:00+01:00")
        self.assertEqual(answer.rain.event, "starts")
        self.assertEqual(answer.rain.at.isoformat(), "2026-11-21T20:00:00+01:00")
        self.assertIn("Sunshine starts in", answer.sun.summary)
        self.assertIn("Rain starts in", answer.rain.summary)
        self.assertEqual(len(answer.as_dict()["sources"]), 2)

    def test_current_sunshine_ends_and_rain_starts(self) -> None:
        page = make_state(
            hours=[
                ("2026-01-01T10:00:00+01:00", "so____"),
                ("2026-01-01T11:00:00+01:00", "so____"),
                ("2026-01-01T12:00:00+01:00", "bw____"),
                ("2026-01-01T13:00:00+01:00", "bdr1__"),
            ],
            items=[
                ("2026-01-01T10:00:00+01:00", "so____"),
                ("2026-01-01T10:15:00+01:00", "so____"),
                ("2026-01-01T10:30:00+01:00", "wb____"),
                ("2026-01-01T10:45:00+01:00", "bw____"),
                ("2026-01-01T11:00:00+01:00", "bdr1__"),
            ],
            current=("2026-01-01T10:00:00+01:00", "so____"),
        )
        answer = analyze("berlin", fetcher=lambda url: page)
        self.assertTrue(answer.sun.currently)
        self.assertEqual(answer.sun.event, "ends")
        self.assertEqual(answer.sun.minutes, 45)
        self.assertFalse(answer.rain.currently)
        self.assertEqual(answer.rain.event, "starts")
        self.assertEqual(answer.rain.minutes, 60)

    def test_current_rain_ends(self) -> None:
        page = make_state(
            hours=[
                ("2026-01-01T10:00:00+01:00", "bdr1__"),
                ("2026-01-01T11:00:00+01:00", "bdr1__"),
                ("2026-01-01T12:00:00+01:00", "bw____"),
            ],
            items=[
                ("2026-01-01T10:00:00+01:00", "bdr1__"),
                ("2026-01-01T10:15:00+01:00", "mds1__"),
                ("2026-01-01T10:30:00+01:00", "bdr1__"),
                ("2026-01-01T10:45:00+01:00", "bw____"),
            ],
            current=("2026-01-01T10:00:00+01:00", "bdr1__"),
        )
        answer = analyze("berlin", fetcher=lambda url: page)
        self.assertTrue(answer.rain.currently)
        self.assertEqual(answer.rain.event, "ends")
        self.assertEqual(answer.rain.minutes, 45)

    def test_no_event_in_horizon(self) -> None:
        page = make_state(
            hours=[("2026-01-01T10:00:00+01:00", "bw____")],
            items=[("2026-01-01T10:00:00+01:00", "bw____")],
            current=("2026-01-01T10:00:00+01:00", "bw____"),
        )
        answer = analyze("berlin", fetcher=lambda url: page)
        self.assertIsNone(answer.sun.event)
        self.assertIsNone(answer.sun.minutes)
        self.assertIsNone(answer.rain.event)
        self.assertIn("No sunshine", answer.sun.summary)

    def test_city_is_validated(self) -> None:
        with self.assertRaises(ValueError):
            analyze("../admin", fetcher=lambda url: "")


if __name__ == "__main__":
    unittest.main()
