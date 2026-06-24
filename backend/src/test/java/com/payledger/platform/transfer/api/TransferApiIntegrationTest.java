package com.payledger.platform.transfer.api;

import tools.jackson.databind.ObjectMapper;
import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.ledger.application.LedgerBalance;
import com.payledger.platform.ledger.application.LedgerBalanceService;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class TransferApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerBalanceService ledgerBalanceService;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void createsTransferThroughHttpApi() throws Exception {
        WalletContext sender = createTryWallet("api-sender");
        WalletContext recipient = createTryWallet("api-recipient");

        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);

        String idempotencyKey = "api-transfer-" + UUID.randomUUID();

        CreateTransferRequest request = new CreateTransferRequest(
                sender.wallet().getId(),
                recipient.wallet().getId(),
                sender.customer().getId(),
                2_500,
                "TRY"
        );

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt())
                )
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.sourceWalletId")
                        .value(sender.wallet().getId().toString()))
                .andExpect(jsonPath("$.destinationWalletId")
                        .value(recipient.wallet().getId().toString()))
                .andExpect(jsonPath("$.amountMinor").value(2_500))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        LedgerBalance senderBalance = ledgerBalanceService.calculate(
                sender.ledgerAccount().getId()
        );

        LedgerBalance recipientBalance = ledgerBalanceService.calculate(
                recipient.ledgerAccount().getId()
        );

        assertThat(senderBalance.balanceMinor()).isEqualTo(7_500);
        assertThat(recipientBalance.balanceMinor()).isEqualTo(2_500);

        assertThat(
                transferRepository.countBySourceWalletIdAndIdempotencyKey(
                        sender.wallet().getId(),
                        idempotencyKey
                )
        ).isEqualTo(1);
    }

    @Test
    void returns422WhenSourceWalletHasInsufficientFunds() throws Exception {
        WalletContext sender = createTryWallet("api-insufficient-sender");
        WalletContext recipient = createTryWallet("api-insufficient-recipient");

        CreateTransferRequest request = new CreateTransferRequest(
                sender.wallet().getId(),
                recipient.wallet().getId(),
                sender.customer().getId(),
                1_000,
                "TRY"
        );

        String idempotencyKey = "api-insufficient-" + UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt())
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(
                transferRepository.countBySourceWalletIdAndIdempotencyKey(
                        sender.wallet().getId(),
                        idempotencyKey
                )
        ).isZero();
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
                        "API_TEST_CASH_" + suffix,
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
                        "API_TRANSFER_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a wallet before API transfer testing.",
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
            Customer customer,
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }
}
