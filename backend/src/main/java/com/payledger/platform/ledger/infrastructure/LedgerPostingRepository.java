package com.payledger.platform.ledger.infrastructure;

import com.payledger.platform.ledger.domain.LedgerPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerPostingRepository
        extends JpaRepository<LedgerPosting, UUID> {

    List<LedgerPosting> findAllByLedgerAccountIdOrderByCreatedAtAscLineNumberAsc(
            UUID ledgerAccountId
    );
}
