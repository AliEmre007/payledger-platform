package com.payledger.platform.provider.application;

import com.payledger.platform.provider.domain.ProviderTransaction;
import com.payledger.platform.provider.domain.ProviderTransactionStatus;
import com.payledger.platform.provider.infrastructure.ProviderTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class ProviderSimulatorService {

    public static final String PROVIDER_NAME = "FAKE_PROVIDER";

    private final ProviderTransactionRepository repository;

    public ProviderSimulatorService(
            ProviderTransactionRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public ProviderSimulationResult createTransaction(
            UUID paymentIntentId,
            ProviderTransactionStatus requestedOutcome
    ) {
        ProviderTransaction transaction = repository
                .findByPaymentIntentId(paymentIntentId)
                .orElseGet(() -> repository.saveAndFlush(
                        ProviderTransaction.pending(
                                PROVIDER_NAME,
                                deterministicProviderTransactionId(
                                        paymentIntentId
                                ),
                                paymentIntentId,
                                requestedOutcome
                        )
                ));

        return new ProviderSimulationResult(
                transaction.getProviderName(),
                transaction.getProviderTransactionId(),
                transaction.getPaymentIntentId(),
                transaction.getRequestedOutcome()
        );
    }

    private String deterministicProviderTransactionId(UUID paymentIntentId) {
        UUID deterministicId = UUID.nameUUIDFromBytes(
                ("fake-provider|" + paymentIntentId)
                        .getBytes(StandardCharsets.UTF_8)
        );

        return "fpt_" + deterministicId.toString().replace("-", "");
    }
}
