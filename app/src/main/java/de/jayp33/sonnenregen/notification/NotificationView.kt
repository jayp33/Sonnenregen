package de.jayp33.sonnenregen.notification

import de.jayp33.sonnenregen.data.CityStatus
import de.jayp33.sonnenregen.domain.displayCity

/** The shape of the notification, independent of Android's builder APIs. */
data class NotificationView(
    val title: String,
    val text: String,
    val lines: List<String>,
    val useInbox: Boolean,
)

object NotificationViewBuilder {
    /**
     * One city keeps the original style: city name as header, compact line as
     * body. Two or more cities use an inbox with one labelled line per city.
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
                title = displayCity(single.city),
                text = textFor(single),
                lines = emptyList(),
                useInbox = false,
            )
        }

        val lines = if (statuses.isEmpty()) {
            listOf(noCitiesText)
        } else {
            statuses.map { lineFor(displayCity(it.city), textFor(it)) }
        }
        return NotificationView(
            title = appName,
            text = lines.first(),
            lines = lines,
            useInbox = true,
        )
    }
}
