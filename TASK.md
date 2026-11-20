I want to create a small service, that parses a weather provider website and extracts specific data:

1. How long until the sun shines
  a. If the sun is currently shining, how long until the sunshine ends
2. How long until the next rain
  a. If it is currently raining, how long until the rain ends

Sample URLs for Berlin, Germany:
- https://www.wetteronline.de/wettertrend/berlin
- https://www.wetteronline.de/90-minuten-wetter/berlin

The first URL provides hourly weather data for the next 48 hours.

The second URL provides weather data for the next 90 minutes in 15 minute steps.
