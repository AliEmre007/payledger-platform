package com.payledger.platform.wallet.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.ledger.application.LedgerAccountService;
import com.payledger.platform.outbox.application.OutboxEventCommand;
import com.payledger.platform.outbox.application.OutboxEventService;
import com.payledger.platform.shared.error.ConflictException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final CustomerService customerService;
    private final LedgerAccountService ledgerAccountService;
    private final AuditEventService auditEventService;
    private final OutboxEventService outboxEventService;

    public WalletService(
            WalletRepository walletRepository,
            CustomerService customerService,
            LedgerAccountService ledgerAccountService,
            AuditEventService auditEventService,
            OutboxEventService outboxEventService
    ) {
        this.walletRepository = walletRepository;
        this.customerService = customerService;
        this.ledgerAccountService = ledgerAccountService;
        this.auditEventService = auditEventService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public Wallet createWallet(UUID customerId, String requestedCurrency) {
        customerService.getCustomer(customerId);

        String currency = normalizeCurrency(requestedCurrency);

        walletRepository.findByCustomerIdAndCurrency(customerId, currency)
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "The customer already has a wallet for currency " + currency
                    );
                });

        /*
         * Writes the wallet before creating its linked ledger account.
         * This does not commit yet. If ledger-account creation fails,
         * the transaction rolls back and the wallet is not kept.
         */
        Wallet wallet = walletRepository.saveAndFlush(
                Wallet.create(customerId, currency)
        );

        ledgerAccountService.createForWallet(wallet);

        Map<String, Object> metadata = Map.of(
                "customerId", customerId.toString(),
                "currency", wallet.getCurrency()
        );

        auditEventService.record(
                new AuditEventCommand(
                        "WALLET_CREATED",
                        null,
                        customerId,
                        "WALLET",
                        wallet.getId(),
                        metadata
                )
        );

        outboxEventService.enqueue(
                new OutboxEventCommand(
                        "WALLET_CREATED",
                        "WALLET",
                        wallet.getId(),
                        metadata
                )
        );

        return wallet;
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Wallet not found: " + walletId)
                );
    }

    @Transactional(readOnly = true)
    public List<Wallet> listWalletsForCustomer(UUID customerId) {
        return walletRepository.findByCustomerIdOrderByCurrencyAsc(customerId);
    }

    private String normalizeCurrency(String requestedCurrency) {
        String currency = requestedCurrency.trim().toUpperCase(Locale.ROOT);

        try {
            Currency.getInstance(currency);
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "currency must be a valid ISO-4217 code, such as TRY or USD."
            );
        }
    }
}
