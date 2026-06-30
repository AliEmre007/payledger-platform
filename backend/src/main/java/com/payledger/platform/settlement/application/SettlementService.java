package com.payledger.platform.settlement.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.ledger.application.LedgerAccountService;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.JournalEntry;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.outbox.application.OutboxEventCommand;
import com.payledger.platform.outbox.application.OutboxEventService;
import com.payledger.platform.payment.domain.PaymentIntent;
import com.payledger.platform.payment.domain.PaymentIntentStatus;
import com.payledger.platform.payment.infrastructure.PaymentIntentRepository;
import com.payledger.platform.settlement.domain.ReconciliationCase;
import com.payledger.platform.settlement.domain.SettlementBatch;
import com.payledger.platform.settlement.domain.SettlementLine;
import com.payledger.platform.settlement.infrastructure.ReconciliationCaseRepository;
import com.payledger.platform.settlement.infrastructure.SettlementBatchRepository;
import com.payledger.platform.settlement.infrastructure.SettlementLineRepository;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SettlementService {

    private static final String REFERENCE_TYPE = "SETTLEMENT_BATCH";

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementLineRepository settlementLineRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final LedgerAccountService ledgerAccountService;
    private final LedgerService ledgerService;
    private final AuditEventService auditEventService;
    private final OutboxEventService outboxEventService;

    public SettlementService(
            SettlementBatchRepository settlementBatchRepository,
            SettlementLineRepository settlementLineRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            PaymentIntentRepository paymentIntentRepository,
            LedgerAccountService ledgerAccountService,
            LedgerService ledgerService,
            AuditEventService auditEventService,
            OutboxEventService outboxEventService
    ) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementLineRepository = settlementLineRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.ledgerAccountService = ledgerAccountService;
        this.ledgerService = ledgerService;
        this.auditEventService = auditEventService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public SettlementBatchDetails createBatch(
            CreateSettlementBatchCommand command
    ) {
        String currency = normalizeCurrency(command.currency());
        String idempotencyKey = normalizeRequired(
                command.idempotencyKey(),
                "idempotencyKey"
        );

        return settlementBatchRepository
                .findByMerchantIdAndIdempotencyKey(
                        command.merchantId(),
                        idempotencyKey
                )
                .map(SettlementBatchDetails::from)
                .orElseGet(() -> createNewBatch(command, currency, idempotencyKey));
    }

    @Transactional
    public ReconciliationCaseDetails reconcile(
            ReconcileSettlementCommand command
    ) {
        SettlementBatch batch = settlementBatchRepository
                .findById(command.settlementBatchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement batch not found: "
                                + command.settlementBatchId()
                ));

        String providerReference = normalizeRequired(
                command.providerReference(),
                "providerReference"
        );
        String actualCurrency = normalizeCurrency(command.actualCurrency());

        if (command.actualAmountMinor() < 0) {
            throw new IllegalArgumentException(
                    "actualAmountMinor must be zero or positive."
            );
        }

        return reconciliationCaseRepository
                .findByProviderReference(providerReference)
                .map(ReconciliationCaseDetails::from)
                .orElseGet(() -> createReconciliationCase(
                        batch,
                        providerReference,
                        command.actualAmountMinor(),
                        actualCurrency,
                        command.actorExternalSubject()
                ));
    }

    private SettlementBatchDetails createNewBatch(
            CreateSettlementBatchCommand command,
            String currency,
            String idempotencyKey
    ) {
        List<PaymentIntent> capturedIntents = paymentIntentRepository
                .findUnsettledCapturedForUpdate(
                        command.merchantId(),
                        currency,
                        PaymentIntentStatus.CAPTURED
                );

        if (capturedIntents.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "NO_SETTLEMENT_LINES",
                    "No captured merchant payments are eligible for settlement."
            );
        }

        long totalAmountMinor = capturedIntents.stream()
                .mapToLong(PaymentIntent::getAmountMinor)
                .reduce(0, Math::addExact);

        LedgerAccount merchantPayableAccount =
                ledgerAccountService.getForMerchantPayable(
                        command.merchantId(),
                        currency
                );
        LedgerAccount settlementClearingAccount =
                ledgerAccountService.getOrCreateSettlementClearing(currency);

        UUID batchId = UUID.randomUUID();
        JournalEntry journalEntry = ledgerService.post(
                new PostJournalEntryCommand(
                        "MERCHANT_SETTLEMENT",
                        REFERENCE_TYPE,
                        batchId,
                        currency,
                        "Settle captured merchant payable balance.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        merchantPayableAccount.getId(),
                                        PostingDirection.DEBIT,
                                        totalAmountMinor
                                ),
                                new LedgerPostingCommand(
                                        settlementClearingAccount.getId(),
                                        PostingDirection.CREDIT,
                                        totalAmountMinor
                                )
                        )
                )
        );

        SettlementBatch batch = settlementBatchRepository.saveAndFlush(
                SettlementBatch.completed(
                        batchId,
                        command.merchantId(),
                        currency,
                        totalAmountMinor,
                        journalEntry.getId(),
                        idempotencyKey,
                        command.actorExternalSubject(),
                        command.reason()
                )
        );

        List<SettlementLine> lines = capturedIntents.stream()
                .map(intent -> SettlementLine.create(
                        batch.getId(),
                        intent.getId(),
                        intent.getCaptureJournalEntryId(),
                        intent.getAmountMinor(),
                        intent.getCurrency()
                ))
                .toList();
        settlementLineRepository.saveAllAndFlush(lines);

        emitMutation(
                "SETTLEMENT_BATCH_COMPLETED",
                batch.getId(),
                command.actorExternalSubject(),
                Map.of(
                        "merchantId", batch.getMerchantId().toString(),
                        "currency", batch.getCurrency(),
                        "totalAmountMinor", batch.getTotalAmountMinor(),
                        "lineCount", lines.size(),
                        "journalEntryId", journalEntry.getId().toString()
                )
        );

        return SettlementBatchDetails.from(batch);
    }

    private ReconciliationCaseDetails createReconciliationCase(
            SettlementBatch batch,
            String providerReference,
            long actualAmountMinor,
            String actualCurrency,
            String actorExternalSubject
    ) {
        ReconciliationCase reconciliationCase =
                reconciliationCaseRepository.saveAndFlush(
                        ReconciliationCase.create(
                                batch,
                                providerReference,
                                actualAmountMinor,
                                actualCurrency,
                                actorExternalSubject
                        )
                );

        emitMutation(
                "SETTLEMENT_RECONCILED",
                reconciliationCase.getId(),
                actorExternalSubject,
                Map.of(
                        "settlementBatchId", batch.getId().toString(),
                        "merchantId", batch.getMerchantId().toString(),
                        "status", reconciliationCase.getStatus().name(),
                        "expectedAmountMinor",
                        reconciliationCase.getExpectedAmountMinor(),
                        "actualAmountMinor",
                        reconciliationCase.getActualAmountMinor(),
                        "expectedCurrency",
                        reconciliationCase.getExpectedCurrency(),
                        "actualCurrency",
                        reconciliationCase.getActualCurrency()
                )
        );

        return ReconciliationCaseDetails.from(reconciliationCase);
    }

    private void emitMutation(
            String eventType,
            UUID resourceId,
            String actorExternalSubject,
            Map<String, Object> metadata
    ) {
        auditEventService.record(
                new AuditEventCommand(
                        eventType,
                        actorExternalSubject,
                        null,
                        "SETTLEMENT",
                        resourceId,
                        metadata
                )
        );
        outboxEventService.enqueue(
                new OutboxEventCommand(
                        eventType,
                        "SETTLEMENT",
                        resourceId,
                        metadata
                )
        );
    }

    private String normalizeCurrency(String currency) {
        String normalized = normalizeRequired(currency, "currency")
                .toUpperCase(Locale.ROOT);

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(
                    "currency must be a three-letter ISO currency code."
            );
        }

        return normalized;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        return value.trim();
    }
}
