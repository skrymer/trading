package com.skrymer.midgaard.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DnsPrewarmerTest {
    @Test
    fun `extractHosts returns the host portion of each URL`() {
        // Given: a mix of provider base URLs
        val urls =
            listOf(
                "https://eodhd.com/api",
                "https://www.alphavantage.co/query",
                "https://finnhub.io/api/v1",
            )

        // When
        val hosts = DnsPrewarmer.extractHosts(urls)

        // Then
        assertEquals(listOf("eodhd.com", "finnhub.io", "www.alphavantage.co"), hosts)
    }

    @Test
    fun `extractHosts filters out blank URLs`() {
        // Given: provider URLs with some unconfigured (blank string from missing properties)
        val urls = listOf("https://eodhd.com/api", "", "  ", "https://finnhub.io")

        // When
        val hosts = DnsPrewarmer.extractHosts(urls)

        // Then: only the configured ones come through
        assertEquals(listOf("eodhd.com", "finnhub.io"), hosts)
    }

    @Test
    fun `extractHosts deduplicates so each host is warmed once`() {
        // Given: two provider URLs that share the same host
        val urls = listOf("https://eodhd.com/api/eod", "https://eodhd.com/api/fundamentals")

        // When
        val hosts = DnsPrewarmer.extractHosts(urls)

        // Then
        assertEquals(listOf("eodhd.com"), hosts)
    }

    @Test
    fun `extractHosts skips malformed URLs without crashing`() {
        // Given: one valid URL and one garbage string
        val urls = listOf("https://eodhd.com/api", "not a url at all")

        // When
        val hosts = DnsPrewarmer.extractHosts(urls)

        // Then: the malformed URL is silently dropped (URI.host returns null), valid one survives
        assertEquals(listOf("eodhd.com"), hosts)
    }

    @Test
    fun `extractHosts returns empty when no URLs are configured`() {
        // Given: every property is blank (no providers configured)
        val urls = listOf("", "  ", "")

        // When
        val hosts = DnsPrewarmer.extractHosts(urls)

        // Then
        assertEquals(emptyList(), hosts)
    }
}
