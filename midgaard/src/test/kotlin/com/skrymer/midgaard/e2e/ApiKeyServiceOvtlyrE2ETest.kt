package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.service.ApiKeyService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiKeyServiceOvtlyrE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var apiKeyService: ApiKeyService

    @Test
    fun `ovtlyr cookie credentials saved through ApiKeyService are retrievable`() {
        // Given / When: the three ovtlyr credentials are saved
        apiKeyService.saveOvtlyrCredentials(
            cookieUserId = "user-123",
            cookieToken = "token-abc",
            projectId = "proj-xyz",
        )

        // Then: each reads back
        assertEquals("user-123", apiKeyService.getOvtlyrCookieUserId())
        assertEquals("token-abc", apiKeyService.getOvtlyrCookieToken())
        assertEquals("proj-xyz", apiKeyService.getOvtlyrProjectId())
    }

    @Test
    fun `ovtlyr appears in getStatus and getMaskedKeys, masked, once credentials are saved`() {
        // Given: all three ovtlyr credentials saved
        apiKeyService.saveOvtlyrCredentials(
            cookieUserId = "ovtlyr-user-value",
            cookieToken = "ovtlyr-token-value",
            projectId = "ovtlyr-project-value",
        )

        // Then: the settings UI sees ovtlyr as configured
        assertEquals(true, apiKeyService.getStatus()["ovtlyrConfigured"])

        // Then: the token is exposed only masked — never in plaintext
        val maskedToken = apiKeyService.getMaskedKeys()["ovtlyrCookieToken"]
        assertEquals(true, maskedToken != "ovtlyr-token-value")
        assertTrue(maskedToken!!.startsWith("•"))
        assertTrue(maskedToken.endsWith("alue"))
    }
}
