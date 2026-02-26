package com.skrymer.midgaard.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

class ApiKeyAuthenticationFilter(
    private val apiKeyHash: String,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val apiKey = request.getHeader(API_KEY_HEADER)

        if (apiKey != null && isValidApiKey(apiKey)) {
            val authentication =
                UsernamePasswordAuthenticationToken(
                    "api-client",
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_API")),
                )
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }

    private fun isValidApiKey(apiKey: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val providedHash = digest.digest(apiKey.toByteArray()).toHexString()
        return MessageDigest.isEqual(
            providedHash.toByteArray(),
            apiKeyHash.toByteArray(),
        )
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
    }
}
