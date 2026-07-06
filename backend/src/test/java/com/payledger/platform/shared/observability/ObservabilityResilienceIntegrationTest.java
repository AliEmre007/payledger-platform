package com.payledger.platform.shared.observability;

import com.payledger.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "payledger.resilience.max-request-body-bytes=16",
                "payledger.resilience.rate-limit.max-requests-per-window=1",
                "payledger.resilience.rate-limit.window=PT1M"
        }
)
@AutoConfigureMockMvc
class ObservabilityResilienceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void propagatesValidIncomingTraceId() throws Exception {
        String traceId = UUID.randomUUID().toString();

        mockMvc.perform(
                        get("/actuator/health")
                                .header("X-Trace-Id", traceId)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", traceId));
    }

    @Test
    void protectsMetricsAndExposesPrometheusForAuthenticatedCallers()
            throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics").with(operationsJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());

        mockMvc.perform(get("/actuator/prometheus").with(operationsJwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "payledger_outbox_backlog"
                )));
    }

    @Test
    void publishesOpenApiDescription() throws Exception {
        mockMvc.perform(get("/api-docs").with(operationsJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.paths['/api/v1/transfers']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type")
                        .value("http"));
    }

    @Test
    void rejectsOversizedRequestBeforeBusinessWorkflow() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transfers")
                                .with(customerJwt("oversized-subject"))
                                .contentType("application/json")
                                .content("{\"payload\":\"this body is too large\"}")
                )
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_TOO_LARGE"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void rateLimitsMoneyMovingRequestsBeforePartialTransaction()
            throws Exception {
        mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .with(customerJwt("rate-limit-subject"))
                                .header("Idempotency-Key", "rate-limit-1")
                                .contentType("application/json")
                                .content("{}")
                )
                .andExpect(status().is4xxClientError());

        mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .with(customerJwt("rate-limit-subject"))
                                .header("Idempotency-Key", "rate-limit-2")
                                .contentType("application/json")
                                .content("{}")
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor
    customerJwt(String subject) {
        return jwt().jwt(token -> token
                .subject(subject)
                .claim("preferred_username", "customer")
                .claim(
                        "realm_access",
                        Map.of("roles", List.of("CUSTOMER"))
                )
        );
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor
    operationsJwt() {
        return jwt().jwt(token -> token
                .subject("operations-observability")
                .claim("preferred_username", "ops")
                .claim(
                        "realm_access",
                        Map.of("roles", List.of("OPERATIONS"))
                )
        );
    }
}
