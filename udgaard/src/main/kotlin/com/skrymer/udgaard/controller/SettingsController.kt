package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.ApiCredentialsDto
import com.skrymer.udgaard.service.SettingsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = ["http://localhost:3000"])
class SettingsController(
    private val settingsService: SettingsService
) {

    @GetMapping("/credentials")
    fun getCredentials(): ResponseEntity<ApiCredentialsDto> {
        return ResponseEntity.ok(settingsService.getCredentials())
    }

    @PostMapping("/credentials")
    fun saveCredentials(@RequestBody credentials: ApiCredentialsDto): ResponseEntity<Map<String, String>> {
        settingsService.saveCredentials(credentials)
        return ResponseEntity.ok(mapOf("message" to "Credentials saved successfully"))
    }

    @GetMapping("/credentials/status")
    fun getCredentialsStatus(): ResponseEntity<Map<String, Boolean>> {
        val status = settingsService.getCredentialsStatus()
        return ResponseEntity.ok(status)
    }
}
