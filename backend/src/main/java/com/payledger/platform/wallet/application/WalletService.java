package com.payledger.platform.wallet.application;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.shared.error.ConflictException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final CustomerService customerService;

    public WalletService(
            WalletRepository walletRepository,
            CustomerService customerService
    ) {
        this.walletRepository = walletRepository;
        this.customerService = customerService;
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

        return walletRepository.save(Wallet.create(customerId, currency));
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Wallet not found: " + walletId)
                );
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
