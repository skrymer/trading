package com.skrymer.midgaard.integration

import org.slf4j.Logger

/**
 * Strips API credentials from error messages before they hit the log.
 *
 * Spring's `RestClientResponseException` includes the request URI in its
 * `message` and stack-trace lines. When that URI carries an `api_token`,
 * `apikey`, or `apiKey` query param, naive `logger.error(..., e)` prints
 * the credential to ERROR-level logs (and any aggregator that ships them).
 *
 * Use `SafeLogging.logFetchFailure` from each provider's `runCatching.onFailure`
 * block instead of passing the throwable directly to the logger.
 */
object SafeLogging {
    private val SECRET_PATTERN =
        Regex(
            "(api[-_]?token|api[-_]?key)=[^&\\s\"]*",
            RegexOption.IGNORE_CASE,
        )

    fun sanitize(message: String?): String =
        (message ?: "").replace(SECRET_PATTERN) { match ->
            "${match.value.substringBefore('=')}=***"
        }

    fun logFetchFailure(
        logger: Logger,
        provider: String,
        label: String,
        symbol: String,
        e: Throwable,
    ) {
        val sanitized = sanitize(e.message ?: e.javaClass.simpleName)
        logger.error("Failed to fetch $label from $provider for $symbol: $sanitized")
        logger.debug("Stack trace for $provider $label fetch failure ($symbol)", e)
    }
}
