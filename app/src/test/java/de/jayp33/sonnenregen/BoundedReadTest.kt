package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.SourceException
import de.jayp33.sonnenregen.data.readBounded
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedReadTest {
    @Test
    fun accepts_a_response_smaller_than_the_limit() {
        val source = Buffer().writeUtf8("a short forecast page")

        assertEquals("a short forecast page", readBounded(source, 2L * 1024 * 1024))
    }

    @Test
    fun accepts_a_response_exactly_at_the_limit() {
        val body = "x".repeat(64)
        val source = Buffer().writeUtf8(body)

        assertEquals(body, readBounded(source, 64L))
    }

    @Test
    fun rejects_a_response_over_the_limit() {
        val source = Buffer().writeUtf8("x".repeat(65))

        assertThrows(SourceException::class.java) { readBounded(source, 64L) }
    }
}
