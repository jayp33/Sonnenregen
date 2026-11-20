package de.jayp33.sonnenregen

object TestData {
    val shortcastJson: String by lazy {
        TestData::class.java.getResourceAsStream("/shortcast.json")!!
            .bufferedReader()
            .use { it.readText() }
    }

    /** A clear night symbol at 07:00, i.e. before the 07:38 sunrise. */
    val sunnyMorningShortcast: String = """
        {
          "current": {"date":"2026-11-21T06:45:00+01:00","symbol":"mo____","solar_elevation":-5.0},
          "nowcast_trend": {"items":[]},
          "hours": [
            {"date":"2026-11-21T07:00:00+01:00","symbol":"mo____"},
            {"date":"2026-11-21T08:00:00+01:00","symbol":"so____"},
            {"date":"2026-11-21T09:00:00+01:00","symbol":"mo____"}
          ]
        }
    """.trimIndent()

    val astroJson: String = """
        {
          "days": [
            {"date":"2026-11-20T00:00:00+01:00","sun":{"course_type":"sunrise_and_sunset","rise":"2026-11-20T07:35:00+01:00","set":"2026-11-20T16:07:00+01:00"}},
            {"date":"2026-11-21T00:00:00+01:00","sun":{"course_type":"sunrise_and_sunset","rise":"2026-11-21T07:38:00+01:00","set":"2026-11-21T16:05:00+01:00"}},
            {"date":"2026-11-22T00:00:00+01:00","sun":{"course_type":"polar_night","rise":null,"set":null}}
          ]
        }
    """.trimIndent()

    fun page(astroJson: String? = null): String = pageWith(shortcastJson, astroJson)

    fun pageWith(shortcast: String, astroJson: String? = null): String {
        val astroEntry = if (astroJson != null) {
            """"https://api-web.wo-cloud.com/astro/days/v1?test":$astroJson,"""
        } else {
            ""
        }
        return """<script id="ng-state" type="application/json">{$astroEntry"https://api-web.wo-cloud.com/blending/shortcast/v1?test":$shortcast}</script>"""
    }
}
