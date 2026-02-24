package com.skrymer.udgaard.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty("app.security.enabled", havingValue = "true", matchIfMissing = true)
class AppUserDetailsService(
  private val userRepository: UserRepository
) : UserDetailsService {
  override fun loadUserByUsername(username: String): UserDetails {
    val appUser = userRepository.findByUsername(username)
      ?: throw UsernameNotFoundException("User not found: $username")

    return User
      .builder()
      .username(appUser.username)
      .password(appUser.passwordHash)
      .roles(appUser.role)
      .disabled(!appUser.enabled)
      .build()
  }
}
