package com.payledger.platform.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnauthenticatedApiRequests() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/wallets/{walletId}/balance",
                                UUID.randomUUID()
                        )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAuthenticatedJwtToReachApplicationLayer() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/wallets/{walletId}/balance",
                                UUID.randomUUID()
                        )
                                .with(jwt().jwt(token -> token
                                        .subject("keycloak-alice-subject")
                                        .claim(
                                                "preferred_username",
                                                "alice"
                                        )
                                        .claim(
                                                "realm_access",
                                                Map.of(
                                                        "roles",
                                                        List.of("CUSTOMER")
                                                )
                                        )
                                ))
                )
                .andExpect(status().isNotFound());
    }
}
