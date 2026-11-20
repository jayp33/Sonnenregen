package de.jayp33.sonnenregen

object TestData {
    val shortcastJson: String by lazy {
        TestData::class.java.getResourceAsStream("/shortcast.json")!!
            .bufferedReader()
            .use { it.readText() }
    }

    fun page(): String =
        """<script id="ng-state" type="application/json">{"https://api-web.wo-cloud.com/blending/shortcast/v1?test":$shortcastJson}</script>"""
}
