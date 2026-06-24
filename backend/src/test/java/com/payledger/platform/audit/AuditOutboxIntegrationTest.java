package com.payledger.platform.audit;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.JournalEntryRepository;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.transfer.application.CreateTransferCommand;
import com.payledger.platform.transfer.application.TransferService;
import com.payledger.platform.transfer.domain.Transfer;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditOutboxIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void transferCreationWritesAuditAndOutboxInSameTransaction() {
        WalletContext sender = createTryWallet("audit-transfer-sender");
        WalletContext recipient = createTryWallet("audit-transfer-recipient");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);

        Transfer transfer = transferService.createCompletedTransfer(
                transferCommand(
                        sender,
                        recipient,
                        2_500,
                        "audit-transfer-" + UUID.randomUUID()
                )
        );

        assertThat(transferRepository.findById(transfer.getId())).isPresent();
        assertThat(journalEntryRepository.existsById(
                transfer.getJournalEntryId()
        )).isTrue();
        assertThat(countAuditEvents("TRANSFER", transfer.getId())).isOne();
        assertThat(countOutboxEvents("TRANSFER", transfer.getId())).isOne();

        String auditMetadata = auditMetadata("TRANSFER", transfer.getId());
        String outboxPayload = outboxPayload("TRANSFER", transfer.getId());

        assertThat(auditMetadata)
                .contains("sourceWalletId")
                .contains("destinationWalletId")
                .contains("journalEntryId")
                .doesNotContain("audit-transfer-")
                .doesNotContainIgnoringCase("bearer")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("secret");

        assertThat(outboxPayload)
                .contains("sourceWalletId")
                .contains("destinationWalletId")
                .contains("journalEntryId")
                .doesNotContain("audit-transfer-")
                .doesNotContainIgnoringCase("bearer")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("secret");
    }

    @Test
    void rollbackAfterTransferLeavesNoBusinessLedgerAuditOrOutboxRows() {
        WalletContext sender = createTryWallet("rollback-sender");
        WalletContext recipient = createTryWallet("rollback-recipient");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);

        long transferCountBefore = transferRepository.count();
        long journalCountBefore = journalEntryRepository.count();
        long auditCountBefore = countRows("audit_events");
        long outboxCountBefore = countRows("outbox_events");

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    transferService.createCompletedTransfer(
                            transferCommand(
                                    sender,
                                    recipient,
                                    1_000,
                                    "rollback-transfer-" + UUID.randomUUID()
                            )
                    );

                    throw new IllegalStateException(
                            "force rollback after transfer workflow"
                    );
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(transferRepository.count()).isEqualTo(transferCountBefore);
        assertThat(journalEntryRepository.count()).isEqualTo(journalCountBefore);
        assertThat(countRows("audit_events")).isEqualTo(auditCountBefore);
        assertThat(countRows("outbox_events")).isEqualTo(outboxCountBefore);
    }

    @Test
    void auditAndOutboxRowsAreAppendOnly() {
        WalletContext walletContext = createTryWallet("append-only");

        UUID auditEventId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM audit_events
                        WHERE resource_type = 'WALLET'
                          AND resource_id = ?
                        LIMIT 1
                        """,
                UUID.class,
                walletContext.wallet().getId()
        );

        UUID outboxRowId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM outbox_events
                        WHERE aggregate_type = 'WALLET'
                          AND aggregate_id = ?
                        LIMIT 1
                        """,
                UUID.class,
                walletContext.wallet().getId()
        );

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE audit_events SET metadata = '{}'::jsonb WHERE id = ?",
                        auditEventId
                )
        )
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "DELETE FROM audit_events WHERE id = ?",
                        auditEventId
                )
        )
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE outbox_events SET status = 'PENDING' WHERE id = ?",
                        outboxRowId
                )
        )
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "DELETE FROM outbox_events WHERE id = ?",
                        outboxRowId
                )
        )
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private WalletContext createTryWallet(String label) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toLowerCase(Locale.ROOT);

        Customer customer = customerService.createCustomer(
                CustomerType.PERSONAL,
                "Test " + label + " " + suffix,
                label + "-" + suffix + "@example.test"
        );

        Wallet wallet = walletService.createWallet(customer.getId(), "TRY");

        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();

        return new WalletContext(customer, wallet, ledgerAccount);
    }

    private LedgerAccount createPlatformCashAccount() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);

        return ledgerAccountRepository.saveAndFlush(
                LedgerAccount.createPlatformAccount(
                        "AUDIT_TEST_CASH_" + suffix,
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
                        "AUDIT_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a wallet before audit testing.",
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
                "audit-test-subject-" + UUID.randomUUID(),
                amountMinor,
                "TRY",
                idempotencyKey
        );
    }

    private long countAuditEvents(String resourceType, UUID resourceId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM audit_events
                        WHERE resource_type = ?
                          AND resource_id = ?
                        """,
                Long.class,
                resourceType,
                resourceId
        );
    }

    private long countOutboxEvents(String aggregateType, UUID aggregateId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM outbox_events
                        WHERE aggregate_type = ?
                          AND aggregate_id = ?
                        """,
                Long.class,
                aggregateType,
                aggregateId
        );
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tableName,
                Long.class
        );
    }

    private String auditMetadata(String resourceType, UUID resourceId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT metadata::text
                        FROM audit_events
                        WHERE resource_type = ?
                          AND resource_id = ?
                        """,
                String.class,
                resourceType,
                resourceId
        );
    }

    private String outboxPayload(String aggregateType, UUID aggregateId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT payload::text
                        FROM outbox_events
                        WHERE aggregate_type = ?
                          AND aggregate_id = ?
                        """,
                String.class,
                aggregateType,
                aggregateId
        );
    }

    private record WalletContext(
            Customer customer,
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }
}
