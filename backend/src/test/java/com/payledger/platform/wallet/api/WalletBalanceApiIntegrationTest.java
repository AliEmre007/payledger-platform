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
class WalletBalanceApiIntegrationTest {

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

    @Test
    void returnsBalanceDerivedFromLedgerPostings() throws Exception {
        WalletContext walletContext = createTryWallet("balance-api");
        LedgerAccount platformCash = createPlatformCashAccount();

        topUp(platformCash, walletContext.ledgerAccount(), 12_345);

        mockMvc.perform(
                        get("/api/v1/wallets/{walletId}/balance",
                                walletContext.wallet().getId())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.walletId")
                        .value(walletContext.wallet().getId().toString()))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ledgerBalanceMinor").value(12_345));
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

        return new WalletContext(wallet, ledgerAccount);
    }

    private LedgerAccount createPlatformCashAccount() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);

        return ledgerAccountRepository.saveAndFlush(
                LedgerAccount.createPlatformAccount(
                        "BALANCE_TEST_CASH_" + suffix,
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
                        "BALANCE_API_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a wallet before balance API testing.",
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

    private record WalletContext(
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }
}
