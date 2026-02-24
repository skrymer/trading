package com.skrymer.udgaard.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.time.LocalDateTime

class ApiKeyAuthenticationFilter(
  private val userRepository: UserRepository
) : OncePerRequestFilter() {
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ) {
    val apiKey = request.getHeader("X-API-Key")

    if (apiKey != null && SecurityContextHolder.getContext().authentication == null) {
      val hash = UserSeeder.sha256(apiKey)
      val user = userRepository.findByApiKeyHash(hash)

      if (user != null && user.enabled && !isExpired(user.apiKeyExpiresAt)) {
        val authorities = listOf(
          SimpleGrantedAuthority("ROLE_USER"),
          SimpleGrantedAuthority("ROLE_API")
        )
        val auth = UsernamePasswordAuthenticationToken(user.username, null, authorities)
        SecurityContextHolder.getContext().authentication = auth
      }
    }

    filterChain.doFilter(request, response)
  }

  private fun isExpired(expiresAt: LocalDateTime?): Boolean =
    expiresAt != null && expiresAt.isBefore(LocalDateTime.now())
}
