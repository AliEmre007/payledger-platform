package com.payledger.platform.wallet.api;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.transfer.application.CreateTransferCommand;
import com.payledger.platform.transfer.application.TransferService;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class WalletStatementApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void returnsLedgerEventsThatExplainWalletBalance() throws Exception {
        WalletContext sender = createTryWallet("statement-sender");
        WalletContext recipient = createTryWallet("statement-recipient");

        LedgerAccount platformCash = createPlatformCashAccount();

        topUp(
                platformCash,
                sender.ledgerAccount(),
                10_000,
                Instant.parse("2026-01-01T10:00:00Z")
        );

        transferService.createCompletedTransfer(
                new CreateTransferCommand(
                        sender.wallet().getId(),
                        recipient.wallet().getId(),
                        sender.customer().getId(),
                        2_500,
                        "TRY",
                        "statement-transfer-" + UUID.randomUUID()
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/wallets/{walletId}/statement",
                                sender.wallet().getId()
                        )
                                .param("page", "0")
                                .param("size", "10")
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.walletId")
                        .value(sender.wallet().getId().toString()))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.ledgerBalanceMinor").value(7_500))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalEntries").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].journalType")
                        .value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.entries[0].direction")
                        .value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amountMinor")
                        .value(2_500))
                .andExpect(jsonPath("$.entries[0].signedAmountMinor")
                        .value(-2_500))
                .andExpect(jsonPath("$.entries[1].journalType")
                        .value("WALLET_TOP_UP"))
                .andExpect(jsonPath("$.entries[1].direction")
                        .value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amountMinor")
                        .value(10_000))
                .andExpect(jsonPath("$.entries[1].signedAmountMinor")
                        .value(10_000));
    }

    @Test
    void returns404WhenWalletDoesNotExist() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/wallets/{walletId}/statement",
                                UUID.randomUUID()
                        )
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("RESOURCE_NOT_FOUND"));
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
                        "STATEMENT_TEST_CASH_" + suffix,
                        LedgerAccountType.ASSET,
                        "TRY"
                )
        );
    }

    private void topUp(
            LedgerAccount platformCash,
            LedgerAccount walletAccount,
            long amountMinor,
            Instant occurredAt
    ) {
        ledgerService.post(
                new PostJournalEntryCommand(
                        "WALLET_TOP_UP",
                        "STATEMENT_API_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a wallet before statement API testing.",
                        occurredAt,
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

    private record WalletContext(
            Customer customer,
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }
}
