package com.payledger.platform.risk.application;

import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerStatus;
import com.payledger.platform.customer.infrastructure.CustomerRepository;
import com.payledger.platform.merchant.domain.Merchant;
import com.payledger.platform.merchant.domain.MerchantStatus;
import com.payledger.platform.merchant.infrastructure.MerchantRepository;
import com.payledger.platform.payment.domain.PaymentIntentStatus;
import com.payledger.platform.payment.infrastructure.PaymentIntentRepository;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.shared.observability.BusinessMetrics;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.domain.WalletStatus;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RiskDecisionService {

    static final long MAX_TRANSFER_AMOUNT_MINOR = 500_000;
    static final long MAX_PAYMENT_AMOUNT_MINOR = 500_000;
    static final long DAILY_OUTGOING_AMOUNT_LIMIT_MINOR = 1_000_000;
    static final long DAILY_OUTGOING_COUNT_LIMIT = 5;

    private static final List<PaymentIntentStatus> OUTGOING_PAYMENT_STATUSES =
            List.of(
                    PaymentIntentStatus.AUTHORIZED,
                    PaymentIntentStatus.CAPTURED,
                    PaymentIntentStatus.REFUNDED
            );

    private final TransferRepository transferRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final CustomerRepository customerRepository;
    private final WalletRepository walletRepository;
    private final MerchantRepository merchantRepository;
    private final RiskDecisionAuditService riskDecisionAuditService;
    private final BusinessMetrics metrics;
    private final Clock clock;

    public RiskDecisionService(
            TransferRepository transferRepository,
            PaymentIntentRepository paymentIntentRepository,
            CustomerRepository customerRepository,
            WalletRepository walletRepository,
            MerchantRepository merchantRepository,
            RiskDecisionAuditService riskDecisionAuditService,
            BusinessMetrics metrics
    ) {
        this.transferRepository = transferRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.customerRepository = customerRepository;
        this.walletRepository = walletRepository;
        this.merchantRepository = merchantRepository;
        this.riskDecisionAuditService = riskDecisionAuditService;
        this.metrics = metrics;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public RiskDecision assessTransfer(
            TransferRiskRequest request,
            String actorExternalSubject
    ) {
        String currency = normalizeCurrency(request.currency());

        RiskDecision decision = evaluateCommonCustomerWalletPolicy(
                request.customerId(),
                request.sourceWalletId(),
                request.amountMinor(),
                currency,
                MAX_TRANSFER_AMOUNT_MINOR
        );

        if (!decision.allowed()) {
            auditDenial(
                    "TRANSFER",
                    request.customerId(),
                    request.sourceWalletId(),
                    request.destinationWalletId(),
                    null,
                    request.amountMinor(),
                    currency,
                    actorExternalSubject,
                    decision.reasonCode()
            );
        }

        return decision;
    }

    @Transactional
    public RiskDecision assessPaymentAuthorization(
            PaymentAuthorizationRiskRequest request,
            String actorExternalSubject
    ) {
        String currency = normalizeCurrency(request.currency());

        RiskDecision decision = evaluateCommonCustomerWalletPolicy(
                request.customerId(),
                request.walletId(),
                request.amountMinor(),
                currency,
                MAX_PAYMENT_AMOUNT_MINOR
        );

        if (decision.allowed()) {
            decision = evaluateMerchantPolicy(request.merchantId());
        }

        if (!decision.allowed()) {
            auditDenial(
                    "PAYMENT_AUTHORIZATION",
                    request.customerId(),
                    request.walletId(),
                    null,
                    request.merchantId(),
                    request.amountMinor(),
                    currency,
                    actorExternalSubject,
                    decision.reasonCode()
            );
        }

        return decision;
    }

    private RiskDecision evaluateCommonCustomerWalletPolicy(
            UUID customerId,
            UUID walletId,
            long amountMinor,
            String currency,
            long maxAmountMinor
    ) {
        if (amountMinor > maxAmountMinor) {
            return RiskDecision.deny("AMOUNT_LIMIT_EXCEEDED");
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found for risk assessment: " + customerId
                ));

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            return RiskDecision.deny("CUSTOMER_STATUS_BLOCKED");
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found for risk assessment: " + walletId
                ));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            return RiskDecision.deny("WALLET_STATUS_BLOCKED");
        }

        Instant startInclusive = currentUtcDayStart();
        Instant endExclusive = currentUtcDayEnd();
        long existingAmount = Math.addExact(
                transferRepository.sumOutgoingAmountForCustomer(
                        customerId,
                        currency,
                        startInclusive,
                        endExclusive
                ),
                paymentIntentRepository.sumOutgoingAmountForCustomer(
                        customerId,
                        currency,
                        OUTGOING_PAYMENT_STATUSES,
                        startInclusive,
                        endExclusive
                )
        );

        if (Math.addExact(existingAmount, amountMinor)
                > DAILY_OUTGOING_AMOUNT_LIMIT_MINOR) {
            return RiskDecision.deny("DAILY_AMOUNT_LIMIT_EXCEEDED");
        }

        long existingCount = Math.addExact(
                transferRepository.countOutgoingForCustomer(
                        customerId,
                        currency,
                        startInclusive,
                        endExclusive
                ),
                paymentIntentRepository.countOutgoingForCustomer(
                        customerId,
                        currency,
                        OUTGOING_PAYMENT_STATUSES,
                        startInclusive,
                        endExclusive
                )
        );

        if (existingCount + 1 > DAILY_OUTGOING_COUNT_LIMIT) {
            return RiskDecision.deny("DAILY_COUNT_LIMIT_EXCEEDED");
        }

        return RiskDecision.allow();
    }

    private RiskDecision evaluateMerchantPolicy(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Merchant not found for risk assessment: " + merchantId
                ));

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            return RiskDecision.deny("MERCHANT_STATUS_BLOCKED");
        }

        return RiskDecision.allow();
    }

    private void auditDenial(
            String action,
            UUID customerId,
            UUID sourceWalletId,
            UUID destinationWalletId,
            UUID merchantId,
            long amountMinor,
            String currency,
            String actorExternalSubject,
            String reasonCode
    ) {
        riskDecisionAuditService.recordDenial(
                action,
                customerId,
                sourceWalletId,
                destinationWalletId,
                merchantId,
                amountMinor,
                currency,
                actorExternalSubject,
                reasonCode
        );
        metrics.riskDenied(action, reasonCode);
    }

    private Instant currentUtcDayStart() {
        return LocalDate.now(clock)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    private Instant currentUtcDayEnd() {
        return LocalDate.now(clock)
                .plusDays(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required.");
        }

        String normalized = currency.trim().toUpperCase(Locale.ROOT);

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(
                    "currency must be a three-letter ISO code."
            );
        }

        return normalized;
    }
}
