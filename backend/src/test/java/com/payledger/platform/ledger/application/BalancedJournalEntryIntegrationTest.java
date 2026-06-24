package com.payledger.platform.ledger.application;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.ledger.domain.JournalEntry;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class BalancedJournalEntryIntegrationTest extends PostgresIntegrationTest {

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

    @Test
    void postsBalancedEntriesAndCalculatesBalancesFromPostings() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Customer sender = customerService.createCustomer(
                CustomerType.PERSONAL,
                "Test Sender " + suffix,
                "sender-" + suffix + "@example.test"
        );

        Customer recipient = customerService.createCustomer(
                CustomerType.PERSONAL,
                "Test Recipient " + suffix,
                "recipient-" + suffix + "@example.test"
        );

        Wallet senderWallet = walletService.createWallet(
                sender.getId(),
                "TRY"
        );

        Wallet recipientWallet = walletService.createWallet(
                recipient.getId(),
                "TRY"
        );

        LedgerAccount senderAccount = ledgerAccountRepository
                .findByWalletId(senderWallet.getId())
                .orElseThrow();

        LedgerAccount recipientAccount = ledgerAccountRepository
                .findByWalletId(recipientWallet.getId())
                .orElseThrow();

        LedgerAccount platformCashAccount = ledgerAccountRepository.save(
                LedgerAccount.createPlatformAccount(
                        "PLATFORM_CASH_" + suffix,
                        LedgerAccountType.ASSET,
                        "TRY"
                )
        );

        JournalEntry topUp = ledgerService.post(
                new PostJournalEntryCommand(
                        "WALLET_TOP_UP",
                        "TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund sender wallet for ledger test.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        platformCashAccount.getId(),
                                        PostingDirection.DEBIT,
                                        10_000
                                ),
                                new LedgerPostingCommand(
                                        senderAccount.getId(),
                                        PostingDirection.CREDIT,
                                        10_000
                                )
                        )
                )
        );

        JournalEntry transfer = ledgerService.post(
                new PostJournalEntryCommand(
                        "INTERNAL_TRANSFER",
                        "TEST_TRANSFER",
                        UUID.randomUUID(),
                        "TRY",
                        "Move funds from sender wallet to recipient wallet.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        senderAccount.getId(),
                                        PostingDirection.DEBIT,
                                        10_000
                                ),
                                new LedgerPostingCommand(
                                        recipientAccount.getId(),
                                        PostingDirection.CREDIT,
                                        10_000
                                )
                        )
                )
        );

        LedgerBalance platformCashBalance = ledgerBalanceService.calculate(
                platformCashAccount.getId()
        );

        LedgerBalance senderBalance = ledgerBalanceService.calculate(
                senderAccount.getId()
        );

        LedgerBalance recipientBalance = ledgerBalanceService.calculate(
                recipientAccount.getId()
        );

        assertThat(topUp.getId()).isNotNull();
        assertThat(transfer.getId()).isNotNull();

        assertThat(platformCashBalance.balanceMinor()).isEqualTo(10_000);

        assertThat(senderBalance.debitTotalMinor()).isEqualTo(10_000);
        assertThat(senderBalance.creditTotalMinor()).isEqualTo(10_000);
        assertThat(senderBalance.balanceMinor()).isZero();

        assertThat(recipientBalance.debitTotalMinor()).isZero();
        assertThat(recipientBalance.creditTotalMinor()).isEqualTo(10_000);
        assertThat(recipientBalance.balanceMinor()).isEqualTo(10_000);
    }
}
