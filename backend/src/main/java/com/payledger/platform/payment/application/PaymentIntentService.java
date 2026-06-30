package com.payledger.platform.payment.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.KycStatus;
import com.payledger.platform.customer.infrastructure.CustomerRepository;
import com.payledger.platform.ledger.application.LedgerAccountService;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.JournalEntry;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.merchant.application.MerchantService;
import com.payledger.platform.outbox.application.OutboxEventCommand;
import com.payledger.platform.outbox.application.OutboxEventService;
import com.payledger.platform.payment.domain.PaymentIntent;
import com.payledger.platform.payment.domain.PaymentIntentStatus;
import com.payledger.platform.payment.infrastructure.PaymentIntentRepository;
import com.payledger.platform.risk.application.PaymentAuthorizationRiskRequest;
import com.payledger.platform.risk.application.RiskDecision;
import com.payledger.platform.risk.application.RiskDecisionService;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.shared.error.IdempotencyConflictException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.shared.error.WalletAccessDeniedException;
import com.payledger.platform.wallet.application.CreateFundsHoldCommand;
import com.payledger.platform.wallet.application.FundsHoldService;
import com.payledger.platform.wallet.domain.FundsHold;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.domain.WalletStatus;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentIntentService {

    private static final String REFERENCE_TYPE = "PAYMENT_INTENT";

    private final PaymentIntentRepository paymentIntentRepository;
    private final WalletRepository walletRepository;
    private final CustomerRepository customerRepository;
    private final MerchantService merchantService;
    private final FundsHoldService fundsHoldService;
    private final LedgerAccountService ledgerAccountService;
    private final LedgerService ledgerService;
    private final RiskDecisionService riskDecisionService;
    private final AuditEventService auditEventService;
    private final OutboxEventService outboxEventService;

    public PaymentIntentService(
            PaymentIntentRepository paymentIntentRepository,
            WalletRepository walletRepository,
            CustomerRepository customerRepository,
            MerchantService merchantService,
            FundsHoldService fundsHoldService,
            LedgerAccountService ledgerAccountService,
            LedgerService ledgerService,
            RiskDecisionService riskDecisionService,
            AuditEventService auditEventService,
            OutboxEventService outboxEventService
    ) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.walletRepository = walletRepository;
        this.customerRepository = customerRepository;
        this.merchantService = merchantService;
        this.fundsHoldService = fundsHoldService;
        this.ledgerAccountService = ledgerAccountService;
        this.ledgerService = ledgerService;
        this.riskDecisionService = riskDecisionService;
        this.auditEventService = auditEventService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public PaymentIntent capture(
            UUID paymentIntentId,
            String idempotencyKey,
            String actorExternalSubject,
            String reason
    ) {
        PaymentIntent paymentIntent = getPaymentIntentForUpdate(
                paymentIntentId
        );

        if (paymentIntent.getStatus() == PaymentIntentStatus.CAPTURED) {
            paymentIntent.capture(
                    paymentIntent.getCaptureJournalEntryId(),
                    idempotencyKey
            );
            return paymentIntent;
        }

        if (paymentIntent.getStatus() != PaymentIntentStatus.AUTHORIZED) {
            paymentIntent.capture(UUID.randomUUID(), idempotencyKey);
        }

        fundsHoldService.capture(paymentIntent.getFundsHoldId());

        LedgerAccount customerWalletAccount =
                ledgerAccountService.getForWallet(
                        paymentIntent.getSourceWalletId()
                );
        LedgerAccount merchantPayableAccount =
                ledgerAccountService.getForMerchantPayable(
                        paymentIntent.getMerchantId(),
                        paymentIntent.getCurrency()
                );

        JournalEntry journalEntry = ledgerService.post(
                new PostJournalEntryCommand(
                        "PAYMENT_CAPTURE",
                        REFERENCE_TYPE,
                        paymentIntent.getId(),
                        paymentIntent.getCurrency(),
                        "Capture authorized wallet payment.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        customerWalletAccount.getId(),
                                        PostingDirection.DEBIT,
                                        paymentIntent.getAmountMinor()
                                ),
                                new LedgerPostingCommand(
                                        merchantPayableAccount.getId(),
                                        PostingDirection.CREDIT,
                                        paymentIntent.getAmountMinor()
                                )
                        )
                )
        );

        paymentIntent.capture(journalEntry.getId(), idempotencyKey);
        PaymentIntent saved = paymentIntentRepository.saveAndFlush(
                paymentIntent
        );

        emitMutation(
                "PAYMENT_INTENT_CAPTURED",
                saved,
                actorExternalSubject,
                saved.getCustomerId(),
                reason
        );

        return saved;
    }

    @Transactional
    public PaymentIntent refund(
            UUID paymentIntentId,
            String idempotencyKey,
            String actorExternalSubject,
            String reason
    ) {
        PaymentIntent paymentIntent = getPaymentIntentForUpdate(
                paymentIntentId
        );

        if (paymentIntent.getStatus() == PaymentIntentStatus.REFUNDED) {
            paymentIntent.refund(
                    paymentIntent.getRefundJournalEntryId(),
                    idempotencyKey
            );
            return paymentIntent;
        }

        if (paymentIntent.getStatus() != PaymentIntentStatus.CAPTURED) {
            paymentIntent.refund(UUID.randomUUID(), idempotencyKey);
        }

        LedgerAccount customerWalletAccount =
                ledgerAccountService.getForWallet(
                        paymentIntent.getSourceWalletId()
                );
        LedgerAccount merchantPayableAccount =
                ledgerAccountService.getForMerchantPayable(
                        paymentIntent.getMerchantId(),
                        paymentIntent.getCurrency()
                );

        JournalEntry journalEntry = ledgerService.post(
                new PostJournalEntryCommand(
                        "PAYMENT_REFUND",
                        REFERENCE_TYPE,
                        paymentIntent.getId(),
                        paymentIntent.getCurrency(),
                        "Refund captured wallet payment.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        merchantPayableAccount.getId(),
                                        PostingDirection.DEBIT,
                                        paymentIntent.getAmountMinor()
                                ),
                                new LedgerPostingCommand(
                                        customerWalletAccount.getId(),
                                        PostingDirection.CREDIT,
                                        paymentIntent.getAmountMinor()
                                )
                        )
                )
        );

        paymentIntent.refund(journalEntry.getId(), idempotencyKey);
        PaymentIntent saved = paymentIntentRepository.saveAndFlush(
                paymentIntent
        );

        emitMutation(
                "PAYMENT_INTENT_REFUNDED",
                saved,
                actorExternalSubject,
                saved.getCustomerId(),
                reason
        );

        return saved;
    }

    @Transactional
    public PaymentIntent authorize(CreatePaymentIntentCommand command) {
        String fingerprint = PaymentIntentRequestFingerprint.calculate(command);

        return paymentIntentRepository
                .findByCustomerIdAndIdempotencyKey(
                        command.customerId(),
                        command.idempotencyKey()
                )
                .map(existing -> replayOrReject(existing, fingerprint))
                .orElseGet(() -> authorizeNew(command, fingerprint));
    }

    @Transactional
    public PaymentIntent cancel(
            UUID paymentIntentId,
            UUID customerId,
            String externalSubject
    ) {
        PaymentIntent paymentIntent = paymentIntentRepository
                .findById(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment intent not found: " + paymentIntentId
                ));

        if (!paymentIntent.getCustomerId().equals(customerId)) {
            throw new WalletAccessDeniedException(
                    "The authenticated customer cannot cancel this payment intent."
            );
        }

        if (paymentIntent.cancel()) {
            fundsHoldService.release(paymentIntent.getFundsHoldId());
            PaymentIntent saved =
                    paymentIntentRepository.saveAndFlush(paymentIntent);
            emitMutation(
                    "PAYMENT_INTENT_CANCELED",
                    saved,
                    externalSubject,
                    customerId
            );
            return saved;
        }

        return paymentIntent;
    }

    private PaymentIntent authorizeNew(
            CreatePaymentIntentCommand command,
            String fingerprint
    ) {
        Wallet sourceWallet = getWallet(command.sourceWalletId());
        validateWallet(sourceWallet, command);
        validateCustomerKyc(command.customerId());
        merchantService.getPaymentEligibleMerchant(
                command.merchantId(),
                command.currency()
        );
        requireRiskApproval(command);

        UUID paymentIntentId = UUID.randomUUID();
        PaymentIntent paymentIntent = PaymentIntent.created(
                paymentIntentId,
                command.customerId(),
                sourceWallet.getId(),
                command.merchantId(),
                command.amountMinor(),
                command.currency(),
                command.idempotencyKey(),
                fingerprint
        );

        FundsHold fundsHold = fundsHoldService.create(
                new CreateFundsHoldCommand(
                        sourceWallet.getId(),
                        command.amountMinor(),
                        command.currency(),
                        "Payment intent authorization.",
                        REFERENCE_TYPE,
                        paymentIntent.getId(),
                        "payment-intent-" + paymentIntent.getId()
                )
        );

        paymentIntent.authorize(fundsHold.getId());
        PaymentIntent saved = paymentIntentRepository.saveAndFlush(
                paymentIntent
        );

        emitMutation(
                "PAYMENT_INTENT_AUTHORIZED",
                saved,
                command.externalSubject(),
                command.customerId()
        );

        return saved;
    }

    private PaymentIntent getPaymentIntentForUpdate(UUID paymentIntentId) {
        return paymentIntentRepository.findByIdForUpdate(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment intent not found: " + paymentIntentId
                ));
    }

    private PaymentIntent replayOrReject(
            PaymentIntent existing,
            String fingerprint
    ) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(
                    "The idempotency key was already used for a different payment intent request."
            );
        }

        return existing;
    }

    private Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source wallet not found: " + walletId
                ));
    }

    private void validateWallet(
            Wallet sourceWallet,
            CreatePaymentIntentCommand command
    ) {
        if (!sourceWallet.getCustomerId().equals(command.customerId())) {
            throw new WalletAccessDeniedException(
                    "The authenticated customer cannot authorize a payment from this wallet."
            );
        }

        if (sourceWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "WALLET_NOT_ACTIVE",
                    "Source wallet is not active."
            );
        }

        if (!sourceWallet.getCurrency().equals(command.currency())) {
            throw new IllegalArgumentException(
                    "Payment currency must match the source wallet currency."
            );
        }
    }

    private void validateCustomerKyc(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found: " + customerId
                ));

        if (customer.getKycStatus() != KycStatus.APPROVED) {
            throw new BusinessRuleViolationException(
                    "KYC_NOT_VERIFIED",
                    "Customer must have approved KYC before authorizing a payment."
            );
        }
    }

    private void requireRiskApproval(CreatePaymentIntentCommand command) {
        RiskDecision decision = riskDecisionService.assessPaymentAuthorization(
                new PaymentAuthorizationRiskRequest(
                        command.customerId(),
                        command.sourceWalletId(),
                        command.merchantId(),
                        command.amountMinor(),
                        command.currency()
                ),
                command.externalSubject()
        );

        if (!decision.allowed()) {
            throw new BusinessRuleViolationException(
                    "RISK_DENIED",
                    "Payment authorization was rejected by risk controls."
            );
        }
    }

    private void emitMutation(
            String eventType,
            PaymentIntent paymentIntent,
            String externalSubject,
            UUID customerId
    ) {
        emitMutation(eventType, paymentIntent, externalSubject, customerId, null);
    }

    private void emitMutation(
            String eventType,
            PaymentIntent paymentIntent,
            String externalSubject,
            UUID customerId,
            String reason
    ) {
        Map<String, Object> metadata = Map.of(
                "paymentIntentId", paymentIntent.getId().toString(),
                "sourceWalletId", paymentIntent.getSourceWalletId().toString(),
                "merchantId", paymentIntent.getMerchantId().toString(),
                "amountMinor", paymentIntent.getAmountMinor(),
                "currency", paymentIntent.getCurrency(),
                "status", paymentIntent.getStatus().name(),
                "reason", reason == null ? "" : reason.trim()
        );

        auditEventService.record(
                new AuditEventCommand(
                        eventType,
                        externalSubject,
                        customerId,
                        "PAYMENT_INTENT",
                        paymentIntent.getId(),
                        metadata
                )
        );
        outboxEventService.enqueue(
                new OutboxEventCommand(
                        eventType,
                        "PAYMENT_INTENT",
                        paymentIntent.getId(),
                        metadata
                )
        );
    }
}
