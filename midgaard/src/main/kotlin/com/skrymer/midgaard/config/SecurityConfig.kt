package com.skrymer.midgaard.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig(
    private val securityProperties: SecurityProperties,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }

        if (securityProperties.enabled) {
            http
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
                .authorizeHttpRequests { auth ->
                    auth
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated()
                }.formLogin { form ->
                    form.defaultSuccessUrl("/", true).permitAll()
                }.exceptionHandling { it.authenticationEntryPoint(jsonUnauthorizedEntryPoint()) }
                .addFilterBefore(
                    ApiKeyAuthenticationFilter(securityProperties.apiKeyHash),
                    UsernamePasswordAuthenticationFilter::class.java,
                )
        } else {
            http
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
                .authorizeHttpRequests {
                    it.anyRequest().permitAll()
                }
        }

        return http.build()
    }

    private fun jsonUnauthorizedEntryPoint() =
        AuthenticationEntryPoint { _: HttpServletRequest, response: HttpServletResponse, _: AuthenticationException ->
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            objectMapper.writeValue(
                response.outputStream,
                mapOf("error" to "Unauthorized", "message" to "Valid API key required"),
            )
        }
}
