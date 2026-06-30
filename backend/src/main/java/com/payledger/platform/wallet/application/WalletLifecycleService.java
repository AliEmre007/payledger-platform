package com.payledger.platform.wallet.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.ledger.application.LedgerAccountService;
import com.payledger.platform.ledger.application.LedgerBalanceService;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.outbox.application.OutboxEventCommand;
import com.payledger.platform.outbox.application.OutboxEventService;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.domain.WalletStatus;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class WalletLifecycleService {

    private final WalletRepository walletRepository;
    private final LedgerAccountService ledgerAccountService;
    private final LedgerBalanceService ledgerBalanceService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditEventService auditEventService;
    private final OutboxEventService outboxEventService;

    public WalletLifecycleService(
            WalletRepository walletRepository,
            LedgerAccountService ledgerAccountService,
            LedgerBalanceService ledgerBalanceService,
            JdbcTemplate jdbcTemplate,
            AuditEventService auditEventService,
            OutboxEventService outboxEventService
    ) {
        this.walletRepository = walletRepository;
        this.ledgerAccountService = ledgerAccountService;
        this.ledgerBalanceService = ledgerBalanceService;
        this.jdbcTemplate = jdbcTemplate;
        this.auditEventService = auditEventService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public Wallet freeze(
            UUID walletId,
            String actorExternalSubject,
            String reason
    ) {
        Wallet wallet = getWallet(walletId);
        WalletStatus fromStatus = wallet.getStatus();

        try {
            wallet.freeze();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException(
                    "WALLET_INVALID_TRANSITION",
                    exception.getMessage()
            );
        }

        Wallet saved = walletRepository.saveAndFlush(wallet);
        recordLifecycleChange(
                saved,
                fromStatus,
                saved.getStatus(),
                actorExternalSubject,
                reason,
                "WALLET_FROZEN"
        );

        return saved;
    }

    @Transactional
    public Wallet unfreeze(
            UUID walletId,
            String actorExternalSubject,
            String reason
    ) {
        Wallet wallet = getWallet(walletId);
        WalletStatus fromStatus = wallet.getStatus();

        try {
            wallet.unfreeze();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException(
                    "WALLET_INVALID_TRANSITION",
                    exception.getMessage()
            );
        }

        Wallet saved = walletRepository.saveAndFlush(wallet);
        recordLifecycleChange(
                saved,
                fromStatus,
                saved.getStatus(),
                actorExternalSubject,
                reason,
                "WALLET_UNFROZEN"
        );

        return saved;
    }

    @Transactional
    public Wallet close(
            UUID walletId,
            String actorExternalSubject,
            String reason
    ) {
        Wallet wallet = getWallet(walletId);
        WalletStatus fromStatus = wallet.getStatus();
        LedgerAccount ledgerAccount = ledgerAccountService.getForWallet(
                wallet.getId()
        );
        long ledgerBalance = ledgerBalanceService.calculate(
                ledgerAccount.getId()
        ).balanceMinor();

        if (ledgerBalance != 0) {
            throw new BusinessRuleViolationException(
                    "WALLET_NON_ZERO_BALANCE",
                    "Wallet cannot be closed while its ledger balance is non-zero."
            );
        }

        try {
            wallet.close();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException(
                    "WALLET_INVALID_TRANSITION",
                    exception.getMessage()
            );
        }

        Wallet saved = walletRepository.saveAndFlush(wallet);
        recordLifecycleChange(
                saved,
                fromStatus,
                saved.getStatus(),
                actorExternalSubject,
                reason,
                "WALLET_CLOSED"
        );

        return saved;
    }

    private Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletId
                ));
    }

    private void recordLifecycleChange(
            Wallet wallet,
            WalletStatus fromStatus,
            WalletStatus toStatus,
            String actorExternalSubject,
            String reason,
            String actionType
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO wallet_lifecycle_events (
                            id,
                            wallet_id,
                            from_status,
                            to_status,
                            reason,
                            actor_external_subject
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                wallet.getId(),
                fromStatus.name(),
                toStatus.name(),
                normalizeReason(reason),
                normalizeActor(actorExternalSubject)
        );

        auditEventService.record(
                new AuditEventCommand(
                        actionType,
                        normalizeActor(actorExternalSubject),
                        null,
                        "WALLET",
                        wallet.getId(),
                        Map.of(
                                "fromStatus", fromStatus.name(),
                                "toStatus", toStatus.name()
                        )
                )
        );

        if ("WALLET_FROZEN".equals(actionType)) {
            outboxEventService.enqueue(
                    new OutboxEventCommand(
                            actionType,
                            "WALLET",
                            wallet.getId(),
                            Map.of(
                                    "walletId", wallet.getId().toString(),
                                    "customerId", wallet.getCustomerId().toString(),
                                    "fromStatus", fromStatus.name(),
                                    "toStatus", toStatus.name()
                            )
                    )
            );
        }
    }

    private String normalizeActor(String actorExternalSubject) {
        if (actorExternalSubject == null || actorExternalSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "actorExternalSubject is required."
            );
        }

        return actorExternalSubject.trim();
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required.");
        }

        String normalized = reason.trim();

        if (normalized.length() > 500) {
            throw new IllegalArgumentException(
                    "reason must not exceed 500 characters."
            );
        }

        return normalized;
    }
}
