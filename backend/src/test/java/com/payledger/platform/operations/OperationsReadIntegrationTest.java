package com.payledger.platform.operations;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.operations.api.LinkKeycloakIdentityRequest;
import com.payledger.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OperationsReadIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void customerTokenCannotReadOperationsResources() throws Exception {
        mockMvc.perform(
                        get("/api/v1/operations/audit-events")
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(
                                        "ops-read-customer-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenCanReadOperationsResources() throws Exception {
        Customer customer = createCustomer("admin-read");

        mockMvc.perform(
                        get("/api/v1/operations/customers")
                                .param("status", customer.getStatus().name())
                                .param("page", "0")
                                .param("size", "5")
                                .with(com.payledger.platform.shared.security.TestJwtSupport.adminJwt(
                                        "ops-read-admin-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").exists());
    }

    @Test
    void auditReadsAreFilteredAndPaginatedWithStableOrdering()
            throws Exception {
        String actionType = "OPS_READ_TEST_" + uniqueSuffix();
        UUID firstResource = UUID.randomUUID();
        UUID secondResource = UUID.randomUUID();
        UUID thirdResource = UUID.randomUUID();

        recordAudit(actionType, firstResource);
        recordAudit(actionType, secondResource);
        recordAudit(actionType, thirdResource);

        String firstPage = mockMvc.perform(
                        get("/api/v1/operations/audit-events")
                                .param("actionType", actionType)
                                .param("page", "0")
                                .param("size", "2")
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        "ops-read-operator-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String replayedFirstPage = mockMvc.perform(
                        get("/api/v1/operations/audit-events")
                                .param("actionType", actionType)
                                .param("page", "0")
                                .param("size", "2")
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        "ops-read-operator-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(
                        get("/api/v1/operations/audit-events")
                                .param("actionType", actionType)
                                .param("page", "1")
                                .param("size", "2")
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        "ops-read-operator-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items.length()").value(1));

        assertThat(replayedFirstPage).isEqualTo(firstPage);
    }

    @Test
    void identityLinkMutationRecordsOperatorAndReason() throws Exception {
        Customer customer = createCustomer("identity-link");
        String operatorSubject = "ops-identity-link-" + UUID.randomUUID();
        String linkedSubject = "linked-keycloak-" + UUID.randomUUID();
        LinkKeycloakIdentityRequest request = new LinkKeycloakIdentityRequest(
                linkedSubject,
                "Link customer after controlled onboarding."
        );

        mockMvc.perform(
                        post(
                                "/api/v1/operations/customers/{customerId}/identities/keycloak",
                                customer.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        operatorSubject
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId")
                        .value(customer.getId().toString()))
                .andExpect(jsonPath("$.externalSubject")
                        .value(linkedSubject));

        Long auditCount = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM audit_events
                        WHERE action_type = 'CUSTOMER_IDENTITY_LINKED'
                          AND actor_external_subject = ?
                          AND actor_customer_id = ?
                          AND metadata ->> 'reason' = ?
                          AND metadata ->> 'linkedExternalSubject' = ?
                        """,
                Long.class,
                operatorSubject,
                customer.getId(),
                request.reason(),
                linkedSubject
        );

        assertThat(auditCount).isOne();
    }

    private Customer createCustomer(String label) {
        String suffix = uniqueSuffix().toLowerCase();

        return customerService.createCustomer(
                CustomerType.PERSONAL,
                "Operations " + label + " " + suffix,
                "operations-" + label + "-" + suffix + "@example.test"
        );
    }

    private void recordAudit(String actionType, UUID resourceId) {
        auditEventService.record(
                new AuditEventCommand(
                        actionType,
                        "ops-read-test-actor",
                        null,
                        "OPERATIONS_READ_TEST",
                        resourceId,
                        Map.of("purpose", "pagination")
                )
        );
    }

    private String uniqueSuffix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}
