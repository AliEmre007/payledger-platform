package com.payledger.platform.ledger.application;

import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerPosting;
import com.payledger.platform.ledger.domain.NormalBalance;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.ledger.infrastructure.LedgerPostingRepository;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerBalanceService {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerPostingRepository ledgerPostingRepository;

    public LedgerBalanceService(
            LedgerAccountRepository ledgerAccountRepository,
            LedgerPostingRepository ledgerPostingRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerPostingRepository = ledgerPostingRepository;
    }

    @Transactional(readOnly = true)
    public LedgerBalance calculate(UUID ledgerAccountId) {
        LedgerAccount account = ledgerAccountRepository.findById(ledgerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger account not found: " + ledgerAccountId
                ));

        List<LedgerPosting> postings = ledgerPostingRepository
                .findAllByLedgerAccountIdOrderByCreatedAtAscLineNumberAsc(
                        ledgerAccountId
                );

        long debitTotal = 0;
        long creditTotal = 0;

        for (LedgerPosting posting : postings) {
            if (posting.getDirection() == PostingDirection.DEBIT) {
                debitTotal = Math.addExact(
                        debitTotal,
                        posting.getAmountMinor()
                );
            } else {
                creditTotal = Math.addExact(
                        creditTotal,
                        posting.getAmountMinor()
                );
            }
        }

        long balance = account.getNormalBalance() == NormalBalance.DEBIT
                ? Math.subtractExact(debitTotal, creditTotal)
                : Math.subtractExact(creditTotal, debitTotal);

        return new LedgerBalance(
                account.getId(),
                account.getCurrency(),
                debitTotal,
                creditTotal,
                balance
        );
    }
}
