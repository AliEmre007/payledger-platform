package com.payledger.platform.shared.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class OpenApiController {

    @GetMapping("/api-docs")
    public Map<String, Object> apiDocs() {
        return Map.of(
                "openapi", "3.1.0",
                "info", Map.of(
                        "title", "PayLedger API",
                        "version", "0.19.0",
                        "description",
                        "Simulated wallet, merchant payment, operations, and observability APIs."
                ),
                "servers", List.of(Map.of("url", "http://localhost:18080")),
                "security", List.of(Map.of("bearerAuth", List.of())),
                "paths", paths(),
                "components", components()
        );
    }

    private Map<String, Object> paths() {
        return Map.ofEntries(
                Map.entry("/api/v1/wallets", Map.of(
                        "get", operation(
                                "List my wallets",
                                "Returns wallets owned by the authenticated customer."
                        )
                )),
                Map.entry("/api/v1/wallets/{walletId}/balance", Map.of(
                        "get", operation(
                                "Read wallet balance",
                                "Returns ledger, held, and available balance for an owned wallet."
                        )
                )),
                Map.entry("/api/v1/wallets/{walletId}/statement", Map.of(
                        "get", operation(
                                "Read wallet statement",
                                "Returns immutable ledger postings for an owned wallet."
                        )
                )),
                Map.entry("/api/v1/transfers", Map.of(
                        "post", idempotentOperation(
                                "Create transfer",
                                "Moves funds between same-currency wallets with double-entry ledger posting."
                        )
                )),
                Map.entry("/api/v1/payment-intents", Map.of(
                        "post", idempotentOperation(
                                "Authorize payment intent",
                                "Creates an active hold for an authenticated customer payment."
                        )
                )),
                Map.entry("/api/v1/payment-intents/{paymentIntentId}/cancel", Map.of(
                        "post", operation(
                                "Cancel payment intent",
                                "Releases an uncaptured customer payment hold."
                        )
                )),
                Map.entry("/api/v1/provider/webhooks", Map.of(
                        "post", operation(
                                "Receive provider webhook",
                                "Accepts HMAC-signed fake provider callbacks."
                        )
                )),
                Map.entry("/api/v1/operations/payment-intents/{paymentIntentId}/capture", Map.of(
                        "post", idempotentOperation(
                                "Capture payment intent",
                                "Operations-only capture of an authorized payment."
                        )
                )),
                Map.entry("/api/v1/operations/payment-intents/{paymentIntentId}/refund", Map.of(
                        "post", idempotentOperation(
                                "Refund payment intent",
                                "Operations-only refund of a captured payment."
                        )
                )),
                Map.entry("/api/v1/operations/settlements", Map.of(
                        "post", idempotentOperation(
                                "Create settlement batch",
                                "Operations-only settlement of captured merchant payable balances."
                        )
                )),
                Map.entry("/api/v1/operations/settlements/{settlementBatchId}/reconcile", Map.of(
                        "post", operation(
                                "Reconcile settlement",
                                "Operations-only reconciliation against simulated provider payout records."
                        )
                )),
                Map.entry("/actuator/health", Map.of(
                        "get", publicOperation(
                                "Health",
                                "Liveness/readiness health endpoint."
                        )
                )),
                Map.entry("/actuator/metrics", Map.of(
                        "get", operation(
                                "Metrics catalog",
                                "Authenticated Micrometer metrics endpoint."
                        )
                )),
                Map.entry("/actuator/prometheus", Map.of(
                        "get", operation(
                                "Prometheus metrics",
                                "Authenticated Prometheus scrape endpoint."
                        )
                ))
        );
    }

    private Map<String, Object> components() {
        return Map.of(
                "securitySchemes", Map.of(
                        "bearerAuth", Map.of(
                                "type", "http",
                                "scheme", "bearer",
                                "bearerFormat", "JWT"
                        )
                ),
                "schemas", Map.of(
                        "ApiError", Map.of(
                                "type", "object",
                                "required", List.of(
                                        "code",
                                        "message",
                                        "traceId",
                                        "timestamp"
                                )
                        )
                )
        );
    }

    private Map<String, Object> idempotentOperation(
            String summary,
            String description
    ) {
        Map<String, Object> operation = operation(summary, description);
        operation.put(
                "parameters",
                List.of(Map.of(
                        "name", "Idempotency-Key",
                        "in", "header",
                        "required", true,
                        "schema", Map.of("type", "string")
                ))
        );
        return operation;
    }

    private Map<String, Object> operation(String summary, String description) {
        return mutableOperation(summary, description, true);
    }

    private Map<String, Object> publicOperation(
            String summary,
            String description
    ) {
        return mutableOperation(summary, description, false);
    }

    private Map<String, Object> mutableOperation(
            String summary,
            String description,
            boolean authenticated
    ) {
        Map<String, Object> operation = new java.util.LinkedHashMap<>();
        operation.put("summary", summary);
        operation.put("description", description);
        operation.put(
                "responses",
                Map.of(
                        "200",
                        Map.of("description", "Successful response."),
                        "400",
                        Map.of("description", "Invalid request."),
                        "401",
                        Map.of("description", "Authentication failed."),
                        "403",
                        Map.of("description", "Authenticated caller is not allowed."),
                        "409",
                        Map.of("description", "Conflict or idempotency-key misuse."),
                        "422",
                        Map.of("description", "Business rule rejected the request."),
                        "429",
                        Map.of("description", "Rate limit exceeded.")
                )
        );
        if (!authenticated) {
            operation.put("security", List.of());
        }
        return operation;
    }
}
