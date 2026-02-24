package com.skrymer.udgaard.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig {
  @Bean
  fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

  @Bean
  fun corsConfigurationSource(): CorsConfigurationSource {
    val configuration = CorsConfiguration().apply {
      allowedOrigins = listOf("http://localhost:3000", "http://localhost:8080")
      allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
      allowedHeaders = listOf("*")
      allowCredentials = true
    }
    val source = UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration("/**", configuration)
    return source
  }

  @Bean
  @ConditionalOnProperty("app.security.enabled", havingValue = "true", matchIfMissing = true)
  fun securedFilterChain(
    http: HttpSecurity,
    userRepository: UserRepository
  ): SecurityFilterChain {
    http
      .cors { it.configurationSource(corsConfigurationSource()) }
      .csrf { it.disable() }
      .addFilterBefore(
        ApiKeyAuthenticationFilter(userRepository),
        UsernamePasswordAuthenticationFilter::class.java
      ).authorizeHttpRequests { auth ->
        auth
          .requestMatchers("/actuator/health/**", "/login", "/error")
          .permitAll()
          .anyRequest()
          .authenticated()
      }.formLogin { form ->
        form
          .loginProcessingUrl("/api/auth/login")
          .successHandler { _, response, _ ->
            writeJson(response, HttpServletResponse.SC_OK, mapOf("status" to "authenticated"))
          }.failureHandler { _, response, _ ->
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, mapOf("error" to "Invalid credentials"))
          }.permitAll()
      }.logout { logout ->
        logout
          .logoutUrl("/api/auth/logout")
          .logoutSuccessHandler { _, response, _ ->
            writeJson(response, HttpServletResponse.SC_OK, mapOf("status" to "logged_out"))
          }.permitAll()
      }.exceptionHandling { exceptions ->
        exceptions.authenticationEntryPoint { _, response, _ ->
          writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, mapOf("error" to "Authentication required"))
        }
      }
    return http.build()
  }

  @Bean
  @ConditionalOnProperty("app.security.enabled", havingValue = "false")
  fun permissiveFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .cors { it.configurationSource(corsConfigurationSource()) }
      .csrf { it.disable() }
      .authorizeHttpRequests { it.anyRequest().permitAll() }
    return http.build()
  }

  @Bean
  @ConditionalOnProperty("app.security.enabled", havingValue = "true", matchIfMissing = true)
  fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager =
    authConfig.authenticationManager

  private fun writeJson(response: HttpServletResponse, status: Int, body: Map<String, String>) {
    response.status = status
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    val json = ObjectMapper().writeValueAsString(body)
    response.writer.write(json)
  }
}
