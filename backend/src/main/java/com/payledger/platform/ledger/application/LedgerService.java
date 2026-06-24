package com.payledger.platform.ledger.application;

import com.payledger.platform.ledger.domain.JournalEntry;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountStatus;
import com.payledger.platform.ledger.domain.LedgerPosting;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.JournalEntryRepository;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.ledger.infrastructure.LedgerPostingRepository;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class LedgerService {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final LedgerPostingRepository ledgerPostingRepository;

    public LedgerService(
            LedgerAccountRepository ledgerAccountRepository,
            JournalEntryRepository journalEntryRepository,
            LedgerPostingRepository ledgerPostingRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.ledgerPostingRepository = ledgerPostingRepository;
    }

    @Transactional
    public JournalEntry post(PostJournalEntryCommand command) {
        validateBalance(command.postings());
        validateAccounts(command);

        JournalEntry journalEntry = journalEntryRepository.save(
                JournalEntry.normal(
                        command.journalType(),
                        command.referenceType(),
                        command.referenceId(),
                        command.currency(),
                        command.description(),
                        command.effectiveAt()
                )
        );

        List<LedgerPosting> postings = new ArrayList<>();
        short lineNumber = 1;

        for (LedgerPostingCommand posting : command.postings()) {
            postings.add(
                    LedgerPosting.create(
                            journalEntry.getId(),
                            posting.ledgerAccountId(),
                            lineNumber++,
                            posting.direction(),
                            posting.amountMinor(),
                            command.currency()
                    )
            );
        }

        ledgerPostingRepository.saveAll(postings);

        return journalEntry;
    }

    private void validateAccounts(PostJournalEntryCommand command) {
        for (LedgerPostingCommand posting : command.postings()) {
            LedgerAccount account = ledgerAccountRepository
                    .findById(posting.ledgerAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Ledger account not found: " + posting.ledgerAccountId()
                    ));

            if (account.getStatus() != LedgerAccountStatus.ACTIVE) {
                throw new IllegalArgumentException(
                        "Ledger account is not active: " + account.getId()
                );
            }

            if (!account.getCurrency().equals(command.currency())) {
                throw new IllegalArgumentException(
                        "Ledger account currency must match journal entry currency."
                );
            }
        }
    }

    private void validateBalance(List<LedgerPostingCommand> postings) {
        long debitTotal = 0;
        long creditTotal = 0;

        for (LedgerPostingCommand posting : postings) {
            if (posting.direction() == PostingDirection.DEBIT) {
                debitTotal = Math.addExact(debitTotal, posting.amountMinor());
            } else {
                creditTotal = Math.addExact(creditTotal, posting.amountMinor());
            }
        }

        if (debitTotal != creditTotal) {
            throw new IllegalArgumentException(
                    "Journal entry is not balanced: debits "
                    + debitTotal
                    + ", credits "
                    + creditTotal
            );
        }
    }
}
