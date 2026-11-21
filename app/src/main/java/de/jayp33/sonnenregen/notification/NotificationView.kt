package de.jayp33.sonnenregen.notification

import de.jayp33.sonnenregen.data.CityStatus

/** The shape of the notification, independent of Android's builder APIs. */
data class NotificationView(
    val title: String,
    val text: String,
    val lines: List<String>,
    val useBigText: Boolean,
)

object NotificationViewBuilder {
    /**
     * One city keeps the city name as header and the compact line as body;
     * two or more cities use the app name with one labelled line per city.
     * Both shapes use wrapping big text so long summaries aren't ellipsized.
     */
    fun build(
        statuses: List<CityStatus>,
        appName: String,
        noCitiesText: String,
        textFor: (CityStatus) -> String,
        lineFor: (city: String, text: String) -> String,
    ): NotificationView {
        val single = statuses.singleOrNull()
        if (single != null) {
            return NotificationView(
                title = single.displayName,
                text = textFor(single),
                lines = emptyList(),
                useBigText = true,
            )
        }

        val lines = if (statuses.isEmpty()) {
            listOf(noCitiesText)
        } else {
            statuses.map { lineFor(it.displayName, textFor(it)) }
        }
        return NotificationView(
            title = appName,
            text = lines.first(),
            lines = lines,
            useBigText = true,
        )
    }
}
