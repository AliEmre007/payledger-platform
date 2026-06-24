package com.payledger.platform.ledger.infrastructure;

import com.payledger.platform.ledger.domain.LedgerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByWalletId(UUID walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM LedgerAccount account
            WHERE account.walletId = :walletId
            """)
    Optional<LedgerAccount> findByWalletIdForUpdate(
            @Param("walletId") UUID walletId
    );
}
