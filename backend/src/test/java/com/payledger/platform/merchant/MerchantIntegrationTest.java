package com.payledger.platform.merchant;

import tools.jackson.databind.ObjectMapper;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountOwnerType;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.merchant.api.OnboardMerchantRequest;
import com.payledger.platform.merchant.application.MerchantDetails;
import com.payledger.platform.merchant.application.MerchantService;
import com.payledger.platform.merchant.application.OnboardMerchantCommand;
import com.payledger.platform.merchant.domain.MerchantStatus;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.shared.error.ConflictException;
import com.payledger.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MerchantIntegrationTest extends PostgresIntegrationTest {

    private static final String OPERATIONS_ACTOR =
            "merchant-test-operations-actor";

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void activationCreatesCurrencyBoundMerchantPayableAccount() {
        MerchantDetails merchant = onboardMerchant("payable-account");

        MerchantDetails activated = merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate merchant for payable account testing."
        );

        LedgerAccount payableAccount = ledgerAccountRepository
                .findByMerchantIdAndCurrency(merchant.id(), "TRY")
                .orElseThrow();

        assertThat(activated.status()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(payableAccount.getOwnerType())
                .isEqualTo(LedgerAccountOwnerType.MERCHANT_PAYABLE);
        assertThat(payableAccount.getAccountType())
                .isEqualTo(LedgerAccountType.LIABILITY);
        assertThat(payableAccount.getCurrency()).isEqualTo("TRY");
        assertThat(payableAccount.getMerchantId()).isEqualTo(merchant.id());
        assertThat(payableAccount.getWalletId()).isNull();
    }

    @Test
    void inactiveOrSuspendedMerchantCannotReceivePaymentIntent() {
        MerchantDetails merchant = onboardMerchant("payment-eligibility");

        assertThatThrownBy(() ->
                merchantService.getPaymentEligibleMerchant(
                        merchant.id(),
                        "TRY"
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("MERCHANT_NOT_ACTIVE")
                );

        merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate before suspension."
        );
        merchantService.suspend(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Suspend merchant for payment eligibility testing."
        );

        assertThatThrownBy(() ->
                merchantService.getPaymentEligibleMerchant(
                        merchant.id(),
                        "TRY"
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("MERCHANT_NOT_ACTIVE")
                );
    }

    @Test
    void duplicateMerchantSetupIsRejected() {
        String displayName = uniqueName("duplicate-merchant");
        merchantService.onboard(command(displayName, "TRY"));

        assertThatThrownBy(() ->
                merchantService.onboard(command(displayName.toUpperCase(
                        Locale.ROOT
                ), "TRY"))
        )
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("display name");
    }

    @Test
    void operationsMutationsAreAuditedAndEmitted() {
        MerchantDetails merchant = onboardMerchant("audit-outbox");

        merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate merchant for audit testing."
        );
        merchantService.suspend(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Suspend merchant for audit testing."
        );
        merchantService.close(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Close merchant for audit testing."
        );

        assertThat(countLifecycleEvents(merchant.id())).isEqualTo(4);
        assertThat(countAuditEvents("MERCHANT_ONBOARDED", merchant.id()))
                .isOne();
        assertThat(countAuditEvents("MERCHANT_ACTIVATED", merchant.id()))
                .isOne();
        assertThat(countAuditEvents("MERCHANT_SUSPENDED", merchant.id()))
                .isOne();
        assertThat(countAuditEvents("MERCHANT_CLOSED", merchant.id()))
                .isOne();
        assertThat(countOutboxEvents("MERCHANT_ONBOARDED", merchant.id()))
                .isOne();
        assertThat(countOutboxEvents("MERCHANT_ACTIVATED", merchant.id()))
                .isOne();
        assertThat(countOutboxEvents("MERCHANT_SUSPENDED", merchant.id()))
                .isOne();
        assertThat(countOutboxEvents("MERCHANT_CLOSED", merchant.id()))
                .isOne();
    }

    @Test
    void operationsEndpointsRequireOperationsRole() throws Exception {
        OnboardMerchantRequest request = new OnboardMerchantRequest(
                "Customer Role Merchant LLC",
                uniqueName("customer-role-merchant"),
                "TRY",
                1,
                "Onboard merchant through operations API."
        );

        mockMvc.perform(
                        post("/api/v1/operations/merchants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(
                                        "merchant-customer-role-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/v1/operations/merchants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        "merchant-operations-role-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.settlementCurrencies[0].currency")
                        .value("TRY"));
    }

    @Test
    void customerFacingReadOnlyExposesActiveMerchantSafeFields()
            throws Exception {
        MerchantDetails merchant = onboardMerchant("public-read");

        mockMvc.perform(
                        get("/api/v1/merchants/{merchantId}", merchant.id())
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(
                                        "merchant-public-read-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isNotFound());

        merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate merchant for public read testing."
        );

        mockMvc.perform(
                        get("/api/v1/merchants/{merchantId}", merchant.id())
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(
                                        "merchant-public-read-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchant.id().toString()))
                .andExpect(jsonPath("$.displayName")
                        .value(merchant.displayName()))
                .andExpect(jsonPath("$.currencies[0]").value("TRY"))
                .andExpect(jsonPath("$.legalName").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    private MerchantDetails onboardMerchant(String label) {
        return merchantService.onboard(command(uniqueName(label), "TRY"));
    }

    private OnboardMerchantCommand command(
            String displayName,
            String currency
    ) {
        return new OnboardMerchantCommand(
                displayName + " LLC",
                displayName,
                currency,
                1,
                OPERATIONS_ACTOR,
                "Onboard merchant for integration testing."
        );
    }

    private String uniqueName(String label) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toLowerCase(Locale.ROOT);

        return "Test " + label + " " + suffix;
    }

    private long countLifecycleEvents(UUID merchantId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM merchant_lifecycle_events
                        WHERE merchant_id = ?
                        """,
                Long.class,
                merchantId
        );
    }

    private long countAuditEvents(String actionType, UUID merchantId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM audit_events
                        WHERE action_type = ?
                          AND resource_type = 'MERCHANT'
                          AND resource_id = ?
                        """,
                Long.class,
                actionType,
                merchantId
        );
    }

    private long countOutboxEvents(String eventType, UUID merchantId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM outbox_events
                        WHERE event_type = ?
                          AND aggregate_type = 'MERCHANT'
                          AND aggregate_id = ?
                        """,
                Long.class,
                eventType,
                merchantId
        );
    }
}
