package com.payledger.platform.wallet.infrastructure;

import com.payledger.platform.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByCustomerIdAndCurrency(UUID customerId, String currency);
}
