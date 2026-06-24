package com.payledger.platform.operations;

import tools.jackson.databind.ObjectMapper;
import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.kyc.application.KycOperationsService;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.operations.api.OperationReasonRequest;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.transfer.application.CreateTransferCommand;
import com.payledger.platform.transfer.application.TransferService;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.application.WalletLifecycleService;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OperationsLifecycleIntegrationTest extends PostgresIntegrationTest {

    private static final String OPERATIONS_ACTOR =
            "operations-lifecycle-test-actor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private KycOperationsService kycOperationsService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletLifecycleService walletLifecycleService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsInvalidKycTransitions() {
        Customer customer = createCustomer("invalid-kyc");

        assertThatThrownBy(() ->
                kycOperationsService.approve(
                        customer.getId(),
                        OPERATIONS_ACTOR,
                        "Cannot approve unsubmitted KYC."
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("KYC_INVALID_TRANSITION")
                )
                .hasMessageContaining("PENDING");

        kycOperationsService.submitForReview(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Submit first review."
        );
        kycOperationsService.reject(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Reject first review."
        );

        Customer resubmitted = kycOperationsService.submitForReview(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Resubmit after rejection."
        );

        assertThat(resubmitted.getKycStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void unverifiedCustomerCannotInitiateTransfer() {
        WalletContext sender = createWallet("unverified-sender");
        WalletContext recipient = createWallet("verified-recipient");
        approveKyc(recipient.customer());
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);

        CreateTransferCommand command = transferCommand(
                sender,
                recipient,
                1_000,
                "kyc-required-" + UUID.randomUUID()
        );

        assertThatThrownBy(() ->
                transferService.createCompletedTransfer(command)
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("KYC_NOT_VERIFIED")
                )
                .hasMessageContaining("Source customer");

        assertThat(
                transferRepository.countBySourceWalletIdAndIdempotencyKey(
                        sender.wallet().getId(),
                        command.idempotencyKey()
                )
        ).isZero();
    }

    @Test
    void frozenWalletCannotDebitTransfer() {
        WalletContext sender = createApprovedWallet("frozen-sender");
        WalletContext recipient = createApprovedWallet("frozen-recipient");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);
        walletLifecycleService.freeze(
                sender.wallet().getId(),
                OPERATIONS_ACTOR,
                "Freeze before debit attempt."
        );

        assertThatThrownBy(() ->
                transferService.createCompletedTransfer(
                        transferCommand(
                                sender,
                                recipient,
                                1_000,
                                "frozen-wallet-" + UUID.randomUUID()
                        )
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("WALLET_NOT_ACTIVE")
                )
                .hasMessageContaining("Source wallet");
    }

    @Test
    void closingNonZeroBalanceWalletIsRejected() {
        WalletContext walletContext = createApprovedWallet("non-zero-close");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, walletContext.ledgerAccount(), 5_000);

        assertThatThrownBy(() ->
                walletLifecycleService.close(
                        walletContext.wallet().getId(),
                        OPERATIONS_ACTOR,
                        "Attempt close with funds."
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("WALLET_NON_ZERO_BALANCE")
                )
                .hasMessageContaining("non-zero");
    }

    @Test
    void operationsActionsWriteHistoryAndAuditEvents() {
        Customer customer = createCustomer("audit-kyc");

        kycOperationsService.submitForReview(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Submit KYC for audit history."
        );
        kycOperationsService.approve(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Approve KYC for audit history."
        );

        Wallet wallet = walletService.createWallet(customer.getId(), "TRY");

        walletLifecycleService.freeze(
                wallet.getId(),
                OPERATIONS_ACTOR,
                "Freeze wallet for audit history."
        );
        walletLifecycleService.unfreeze(
                wallet.getId(),
                OPERATIONS_ACTOR,
                "Unfreeze wallet for audit history."
        );
        walletLifecycleService.close(
                wallet.getId(),
                OPERATIONS_ACTOR,
                "Close empty wallet for audit history."
        );

        assertThat(countKycReviewEvents(customer.getId())).isEqualTo(2);
        assertThat(countWalletLifecycleEvents(wallet.getId())).isEqualTo(3);
        assertThat(countAuditEvents("KYC_SUBMITTED_FOR_REVIEW", "CUSTOMER",
                customer.getId())).isOne();
        assertThat(countAuditEvents("KYC_APPROVED", "CUSTOMER",
                customer.getId())).isOne();
        assertThat(countAuditEvents("WALLET_FROZEN", "WALLET",
                wallet.getId())).isOne();
        assertThat(countAuditEvents("WALLET_UNFROZEN", "WALLET",
                wallet.getId())).isOne();
        assertThat(countAuditEvents("WALLET_CLOSED", "WALLET",
                wallet.getId())).isOne();
    }

    @Test
    void operationsEndpointsRequireOperationsRole() throws Exception {
        WalletContext walletContext = createApprovedWallet("rbac-wallet");
        OperationReasonRequest request = new OperationReasonRequest(
                "Freeze through operations API."
        );

        mockMvc.perform(
                        post(
                                "/api/v1/operations/wallets/{walletId}/freeze",
                                walletContext.wallet().getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(
                                        "customer-role-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post(
                                "/api/v1/operations/wallets/{walletId}/freeze",
                                walletContext.wallet().getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        "operations-role-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    private WalletContext createApprovedWallet(String label) {
        WalletContext walletContext = createWallet(label);
        approveKyc(walletContext.customer());
        return walletContext;
    }

    private WalletContext createWallet(String label) {
        Customer customer = createCustomer(label);
        Wallet wallet = walletService.createWallet(customer.getId(), "TRY");
        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();

        return new WalletContext(customer, wallet, ledgerAccount);
    }

    private Customer createCustomer(String label) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toLowerCase(Locale.ROOT);

        return customerService.createCustomer(
                CustomerType.PERSONAL,
                "Test " + label + " " + suffix,
                label + "-" + suffix + "@example.test"
        );
    }

    private void approveKyc(Customer customer) {
        kycOperationsService.submitForReview(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Prepare customer for lifecycle testing."
        );
        kycOperationsService.approve(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Approve customer for lifecycle testing."
        );
    }

    private LedgerAccount createPlatformCashAccount() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);

        return ledgerAccountRepository.saveAndFlush(
                LedgerAccount.createPlatformAccount(
                        "LIFECYCLE_TEST_CASH_" + suffix,
                        LedgerAccountType.ASSET,
                        "TRY"
                )
        );
    }

    private void topUp(
            LedgerAccount platformCash,
            LedgerAccount walletAccount,
            long amountMinor
    ) {
        ledgerService.post(
                new PostJournalEntryCommand(
                        "WALLET_TOP_UP",
                        "OPERATIONS_LIFECYCLE_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a wallet before lifecycle testing.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        platformCash.getId(),
                                        PostingDirection.DEBIT,
                                        amountMinor
                                ),
                                new LedgerPostingCommand(
                                        walletAccount.getId(),
                                        PostingDirection.CREDIT,
                                        amountMinor
                                )
                        )
                )
        );
    }

    private CreateTransferCommand transferCommand(
            WalletContext sender,
            WalletContext recipient,
            long amountMinor,
            String idempotencyKey
    ) {
        return new CreateTransferCommand(
                sender.wallet().getId(),
                recipient.wallet().getId(),
                sender.customer().getId(),
                "operations-lifecycle-subject-" + UUID.randomUUID(),
                amountMinor,
                "TRY",
                idempotencyKey
        );
    }

    private long countKycReviewEvents(UUID customerId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM kyc_review_events
                        WHERE customer_id = ?
                        """,
                Long.class,
                customerId
        );
    }

    private long countWalletLifecycleEvents(UUID walletId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM wallet_lifecycle_events
                        WHERE wallet_id = ?
                        """,
                Long.class,
                walletId
        );
    }

    private long countAuditEvents(
            String actionType,
            String resourceType,
            UUID resourceId
    ) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM audit_events
                        WHERE action_type = ?
                          AND resource_type = ?
                          AND resource_id = ?
                        """,
                Long.class,
                actionType,
                resourceType,
                resourceId
        );
    }

    private record WalletContext(
            Customer customer,
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }
}
