package com.payledger.platform.wallet.application;

import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountStatus;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.shared.error.IdempotencyConflictException;
import com.payledger.platform.shared.error.InsufficientFundsException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.wallet.domain.FundsHold;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.domain.WalletStatus;
import com.payledger.platform.wallet.infrastructure.FundsHoldRepository;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FundsHoldService {

    private final WalletRepository walletRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final FundsHoldRepository fundsHoldRepository;
    private final WalletAvailableBalanceService availableBalanceService;

    public FundsHoldService(
            WalletRepository walletRepository,
            LedgerAccountRepository ledgerAccountRepository,
            FundsHoldRepository fundsHoldRepository,
            WalletAvailableBalanceService availableBalanceService
    ) {
        this.walletRepository = walletRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.fundsHoldRepository = fundsHoldRepository;
        this.availableBalanceService = availableBalanceService;
    }

    @Transactional
    public FundsHold create(CreateFundsHoldCommand command) {
        Wallet wallet = getWallet(command.walletId());
        validateWallet(wallet, command.currency());

        String fingerprint = FundsHoldRequestFingerprint.calculate(command);

        return fundsHoldRepository
                .findByWalletIdAndIdempotencyKey(
                        wallet.getId(),
                        command.idempotencyKey()
                )
                .map(existing -> replayOrReject(existing, fingerprint))
                .orElseGet(() -> createNewHold(command, wallet, fingerprint));
    }

    @Transactional
    public FundsHold release(UUID holdId) {
        FundsHold hold = getHold(holdId);
        lockWalletLedgerAccount(hold.getWalletId());

        try {
            hold.release();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException(
                    "HOLD_INVALID_TRANSITION",
                    exception.getMessage()
            );
        }

        return fundsHoldRepository.saveAndFlush(hold);
    }

    @Transactional
    public FundsHold capture(UUID holdId) {
        FundsHold hold = getHold(holdId);
        lockWalletLedgerAccount(hold.getWalletId());

        try {
            hold.capture();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleViolationException(
                    "HOLD_INVALID_TRANSITION",
                    exception.getMessage()
            );
        }

        return fundsHoldRepository.saveAndFlush(hold);
    }

    @Transactional(readOnly = true)
    public long activeHeldAmount(UUID walletId) {
        return fundsHoldRepository.sumAmountMinorByWalletIdAndStatus(
                walletId,
                com.payledger.platform.wallet.domain.FundsHoldStatus.ACTIVE
        );
    }

    private FundsHold createNewHold(
            CreateFundsHoldCommand command,
            Wallet wallet,
            String fingerprint
    ) {
        LedgerAccount ledgerAccount = lockWalletLedgerAccount(wallet.getId());

        fundsHoldRepository
                .findByWalletIdAndReferenceTypeAndReferenceId(
                        wallet.getId(),
                        command.referenceType(),
                        command.referenceId()
                )
                .ifPresent(existing -> {
                    throw new IdempotencyConflictException(
                            "A funds hold already exists for this business reference."
                    );
                });

        WalletAvailableBalance availableBalance =
                availableBalanceService.calculate(wallet, ledgerAccount);

        if (availableBalance.availableBalanceMinor() < command.amountMinor()) {
            throw new InsufficientFundsException(
                    "Wallet does not have sufficient available funds for this hold."
            );
        }

        return fundsHoldRepository.saveAndFlush(FundsHold.active(
                wallet.getId(),
                command.currency(),
                command.amountMinor(),
                command.reason(),
                command.referenceType(),
                command.referenceId(),
                command.idempotencyKey(),
                fingerprint
        ));
    }

    private FundsHold replayOrReject(
            FundsHold existing,
            String fingerprint
    ) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(
                    "The idempotency key was already used for a different funds hold request."
            );
        }

        return existing;
    }

    private Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletId
                ));
    }

    private FundsHold getHold(UUID holdId) {
        return fundsHoldRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Funds hold not found: " + holdId
                ));
    }

    private void validateWallet(Wallet wallet, String currency) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "WALLET_NOT_ACTIVE",
                    "Wallet must be active to reserve funds."
            );
        }

        if (!wallet.getCurrency().equals(currency)) {
            throw new IllegalArgumentException(
                    "Hold currency must match wallet currency."
            );
        }
    }

    private LedgerAccount lockWalletLedgerAccount(UUID walletId) {
        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger account not found for wallet: " + walletId
                ));

        if (ledgerAccount.getStatus() != LedgerAccountStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "LEDGER_ACCOUNT_NOT_ACTIVE",
                    "Wallet ledger account must be active to reserve funds."
            );
        }

        return ledgerAccount;
    }
}
