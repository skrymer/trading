package com.skrymer.udgaard.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController {
  @GetMapping("/check")
  fun checkAuth(): Map<String, String> = mapOf("status" to "authenticated")
}
