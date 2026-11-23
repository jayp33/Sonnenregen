package de.jayp33.sonnenregen.data

import de.jayp33.sonnenregen.domain.BASE_URL
import de.jayp33.sonnenregen.domain.DEFAULT_CITY
import de.jayp33.sonnenregen.domain.isValidCitySlug
import de.jayp33.sonnenregen.domain.normalizeCity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)

class WetterOnlineClient {
    private val client = OkHttpClient()

    suspend fun fetch(city: String = DEFAULT_CITY): Pair<String, String> = withContext(Dispatchers.IO) {
        val normalizedCity = normalizeCity(city)
        require(isValidCitySlug(normalizedCity)) { "Invalid city slug: $normalizedCity" }

        val hourlyUrl = "$BASE_URL/wettertrend/$normalizedCity"
        val nowcastUrl = "$BASE_URL/90-minuten-wetter/$normalizedCity"

        val hourly = async { get(hourlyUrl) }
        val nowcast = async { get(nowcastUrl) }
        hourly.await() to nowcast.await()
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Sonnenregen/0.1 (Android)")
            .header("Accept-Language", "de-DE,de;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SourceException("Could not fetch $url: HTTP ${response.code}")
            }
            val source = response.body?.source()
                ?: throw SourceException("Empty response from $url")
            if (source.request(MAX_RESPONSE_BYTES + 1)) {
                throw SourceException("Oversized response from $url")
            }
            return source.readUtf8(MAX_RESPONSE_BYTES)
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
    }
}
