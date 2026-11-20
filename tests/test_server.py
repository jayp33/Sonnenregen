from __future__ import annotations

import json
import threading
import unittest
from datetime import datetime
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import urlopen

from sonnenregen.core import Answer, Change
from sonnenregen.server import Handler, ThreadingHTTPServer


def fake_answer() -> Answer:
    now = datetime.fromisoformat("2026-01-01T10:00:00+01:00")
    change = Change(False, "starts", now, 60, "Sunshine", "Sunshine starts in 1 h.")
    return Answer("berlin", now, "Europe/Berlin", change, change, ("url",), now)


class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)

    def test_weather_endpoint(self) -> None:
        with patch("sonnenregen.server.analyze", return_value=fake_answer()) as analyze:
            with urlopen(f"{self.base}/?city=berlin") as response:
                payload = json.load(response)
                self.assertEqual(response.headers["Content-Type"], "application/json; charset=utf-8")
                self.assertEqual(response.headers["Access-Control-Allow-Origin"], "*")
        analyze.assert_called_once_with("berlin")
        self.assertEqual(payload["sun"]["minutes"], 60)
        self.assertFalse(payload["rain"]["currently"])

    def test_health_endpoint(self) -> None:
        with urlopen(f"{self.base}/health") as response:
            self.assertEqual(json.load(response), {"status": "ok"})

    def test_invalid_city(self) -> None:
        with patch("sonnenregen.server.analyze", side_effect=ValueError("Invalid city slug")):
            with self.assertRaises(HTTPError) as error:
                with urlopen(f"{self.base}/?city=..") as response:
                    response.read()
        self.assertEqual(error.exception.code, 400)
        error.exception.close()


if __name__ == "__main__":
    unittest.main()
