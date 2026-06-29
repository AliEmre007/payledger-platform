package com.payledger.platform.merchant.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.ledger.application.LedgerAccountService;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.merchant.domain.Merchant;
import com.payledger.platform.merchant.domain.MerchantSettlementConfig;
import com.payledger.platform.merchant.domain.MerchantStatus;
import com.payledger.platform.merchant.infrastructure.MerchantRepository;
import com.payledger.platform.merchant.infrastructure.MerchantSettlementConfigRepository;
import com.payledger.platform.outbox.application.OutboxEventCommand;
import com.payledger.platform.outbox.application.OutboxEventService;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.shared.error.ConflictException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantSettlementConfigRepository configRepository;
    private final LedgerAccountService ledgerAccountService;
    private final AuditEventService auditEventService;
    private final OutboxEventService outboxEventService;
    private final JdbcTemplate jdbcTemplate;

    public MerchantService(
            MerchantRepository merchantRepository,
            MerchantSettlementConfigRepository configRepository,
            LedgerAccountService ledgerAccountService,
            AuditEventService auditEventService,
            OutboxEventService outboxEventService,
            JdbcTemplate jdbcTemplate
    ) {
        this.merchantRepository = merchantRepository;
        this.configRepository = configRepository;
        this.ledgerAccountService = ledgerAccountService;
        this.auditEventService = auditEventService;
        this.outboxEventService = outboxEventService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public MerchantDetails onboard(OnboardMerchantCommand command) {
        String legalName = normalizeRequiredText(
                command.legalName(),
                "legalName"
        );
        String displayName = normalizeRequiredText(
                command.displayName(),
                "displayName"
        );
        String currency = normalizeCurrency(command.settlementCurrency());
        String reason = normalizeRequiredText(command.reason(), "reason");

        if (command.settlementDelayDays() < 0
                || command.settlementDelayDays() > 30) {
            throw new IllegalArgumentException(
                    "settlementDelayDays must be between 0 and 30."
            );
        }

        if (merchantRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ConflictException(
                    "A merchant already exists for this display name."
            );
        }

        Merchant merchant = merchantRepository.saveAndFlush(
                Merchant.onboard(legalName, displayName)
        );
        MerchantSettlementConfig config = configRepository.saveAndFlush(
                MerchantSettlementConfig.create(
                        merchant.getId(),
                        currency,
                        command.settlementDelayDays()
                )
        );

        recordLifecycleEvent(
                merchant.getId(),
                MerchantStatus.PENDING,
                MerchantStatus.PENDING,
                command.actorExternalSubject(),
                reason
        );

        emitMutation(
                "MERCHANT_ONBOARDED",
                merchant,
                command.actorExternalSubject(),
                Map.of(
                        "merchantId", merchant.getId().toString(),
                        "status", merchant.getStatus().name(),
                        "currency", config.getCurrency(),
                        "settlementDelayDays",
                        config.getSettlementDelayDays()
                )
        );

        return details(merchant);
    }

    @Transactional
    public MerchantDetails activate(
            UUID merchantId,
            String actorExternalSubject,
            String reason
    ) {
        Merchant merchant = getMerchant(merchantId);
        MerchantStatus fromStatus = merchant.getStatus();

        try {
            merchant.activate();
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }

        Merchant saved = merchantRepository.saveAndFlush(merchant);
        List<MerchantSettlementConfig> configs =
                configRepository.findByMerchantId(merchantId);

        if (configs.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "MERCHANT_SETTLEMENT_CONFIG_REQUIRED",
                    "Merchant must have at least one settlement currency."
            );
        }

        configs.stream()
                .filter(MerchantSettlementConfig::isEnabled)
                .map(MerchantSettlementConfig::getCurrency)
                .forEach(currency -> ledgerAccountService
                        .createForMerchantPayable(merchantId, currency));

        recordLifecycleEvent(
                merchantId,
                fromStatus,
                saved.getStatus(),
                actorExternalSubject,
                reason
        );

        String eventType = fromStatus == MerchantStatus.SUSPENDED
                ? "MERCHANT_REACTIVATED"
                : "MERCHANT_ACTIVATED";

        emitMutation(
                eventType,
                saved,
                actorExternalSubject,
                Map.of(
                        "merchantId", saved.getId().toString(),
                        "fromStatus", fromStatus.name(),
                        "toStatus", saved.getStatus().name()
                )
        );

        return details(saved);
    }

    @Transactional
    public MerchantDetails suspend(
            UUID merchantId,
            String actorExternalSubject,
            String reason
    ) {
        Merchant merchant = getMerchant(merchantId);
        MerchantStatus fromStatus = merchant.getStatus();

        try {
            merchant.suspend();
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }

        Merchant saved = merchantRepository.saveAndFlush(merchant);
        recordLifecycleEvent(
                merchantId,
                fromStatus,
                saved.getStatus(),
                actorExternalSubject,
                reason
        );
        emitMutation(
                "MERCHANT_SUSPENDED",
                saved,
                actorExternalSubject,
                Map.of(
                        "merchantId", saved.getId().toString(),
                        "fromStatus", fromStatus.name(),
                        "toStatus", saved.getStatus().name()
                )
        );

        return details(saved);
    }

    @Transactional
    public MerchantDetails close(
            UUID merchantId,
            String actorExternalSubject,
            String reason
    ) {
        Merchant merchant = getMerchant(merchantId);
        MerchantStatus fromStatus = merchant.getStatus();

        try {
            merchant.close();
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }

        Merchant saved = merchantRepository.saveAndFlush(merchant);
        recordLifecycleEvent(
                merchantId,
                fromStatus,
                saved.getStatus(),
                actorExternalSubject,
                reason
        );
        emitMutation(
                "MERCHANT_CLOSED",
                saved,
                actorExternalSubject,
                Map.of(
                        "merchantId", saved.getId().toString(),
                        "fromStatus", fromStatus.name(),
                        "toStatus", saved.getStatus().name()
                )
        );

        return details(saved);
    }

    @Transactional(readOnly = true)
    public MerchantDetails getActiveMerchant(UUID merchantId) {
        Merchant merchant = merchantRepository
                .findByIdAndStatus(merchantId, MerchantStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active merchant not found: " + merchantId
                ));

        return details(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantDetails getPaymentEligibleMerchant(
            UUID merchantId,
            String currency
    ) {
        Merchant merchant = getMerchant(merchantId);
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "MERCHANT_NOT_ACTIVE",
                    "Merchant must be active before it can receive payments."
            );
        }

        MerchantDetails merchantDetails = details(merchant);
        String normalizedCurrency = normalizeCurrency(currency);

        boolean enabledCurrency = merchantDetails.settlementCurrencies()
                .stream()
                .anyMatch(config -> config.enabled()
                        && config.currency().equals(normalizedCurrency));

        if (!enabledCurrency) {
            throw new BusinessRuleViolationException(
                    "MERCHANT_CURRENCY_NOT_ENABLED",
                    "Merchant cannot receive payments in this currency."
            );
        }

        return merchantDetails;
    }

    @Transactional(readOnly = true)
    public LedgerAccount getPayableAccount(
            UUID merchantId,
            String currency
    ) {
        String normalizedCurrency = normalizeCurrency(currency);

        return ledgerAccountService.getForMerchantPayable(
                merchantId,
                normalizedCurrency
        );
    }

    private Merchant getMerchant(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Merchant not found: " + merchantId
                ));
    }

    private MerchantDetails details(Merchant merchant) {
        List<SettlementCurrency> settlementCurrencies =
                configRepository.findByMerchantId(merchant.getId())
                        .stream()
                        .map(SettlementCurrency::from)
                        .toList();

        return MerchantDetails.from(merchant, settlementCurrencies);
    }

    private void recordLifecycleEvent(
            UUID merchantId,
            MerchantStatus fromStatus,
            MerchantStatus toStatus,
            String actorExternalSubject,
            String reason
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO merchant_lifecycle_events (
                            id,
                            merchant_id,
                            from_status,
                            to_status,
                            reason,
                            actor_external_subject
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                merchantId,
                fromStatus.name(),
                toStatus.name(),
                normalizeRequiredText(reason, "reason"),
                normalizeRequiredText(
                        actorExternalSubject,
                        "actorExternalSubject"
                )
        );
    }

    private void emitMutation(
            String eventType,
            Merchant merchant,
            String actorExternalSubject,
            Map<String, Object> metadata
    ) {
        auditEventService.record(
                new AuditEventCommand(
                        eventType,
                        actorExternalSubject,
                        null,
                        "MERCHANT",
                        merchant.getId(),
                        metadata
                )
        );
        outboxEventService.enqueue(
                new OutboxEventCommand(
                        eventType,
                        "MERCHANT",
                        merchant.getId(),
                        metadata
                )
        );
    }

    private BusinessRuleViolationException invalidTransition(String message) {
        return new BusinessRuleViolationException(
                "MERCHANT_INVALID_TRANSITION",
                message
        );
    }

    private String normalizeCurrency(String requestedCurrency) {
        String currency = normalizeRequiredText(
                requestedCurrency,
                "currency"
        ).toUpperCase(Locale.ROOT);

        try {
            Currency.getInstance(currency);
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "currency must be a valid ISO-4217 code, such as TRY or USD."
            );
        }
    }

    private String normalizeRequiredText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        return value.trim();
    }
}
