package com.skrymer.udgaard.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty("app.security.enabled", havingValue = "true", matchIfMissing = true)
class AuthController {
  @GetMapping("/check")
  fun checkAuth(): Map<String, String> = mapOf("status" to "authenticated")
}
