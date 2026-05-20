package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.service.ApiKeyService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

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
}
