package com.payledger.platform.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_code", nullable = false, length = 120, updatable = false)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20, updatable = false)
    private LedgerAccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 10, updatable = false)
    private NormalBalance normalBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30, updatable = false)
    private LedgerAccountOwnerType ownerType;

    @Column(name = "wallet_id", updatable = false)
    private UUID walletId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerAccountStatus status;

    protected LedgerAccount() {
    }

    private LedgerAccount(UUID id, String accountCode, UUID walletId, String currency) {
        this.id = id;
        this.accountCode = accountCode;
        this.accountType = LedgerAccountType.LIABILITY;
        this.normalBalance = NormalBalance.CREDIT;
        this.ownerType = LedgerAccountOwnerType.CUSTOMER_WALLET;
        this.walletId = walletId;
        this.currency = currency;
        this.status = LedgerAccountStatus.ACTIVE;
    }

    public static LedgerAccount createForWallet(UUID walletId, String currency) {
        String accountCode = "WALLET_"
                + walletId.toString()
                .replace("-", "_")
                .toUpperCase(Locale.ROOT);

        return new LedgerAccount(
                UUID.randomUUID(),
                accountCode,
                walletId,
                currency
        );
    }

    public UUID getId() {
        return id;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public LedgerAccountType getAccountType() {
        return accountType;
    }

    public NormalBalance getNormalBalance() {
        return normalBalance;
    }

    public LedgerAccountOwnerType getOwnerType() {
        return ownerType;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public String getCurrency() {
        return currency;
    }

    public LedgerAccountStatus getStatus() {
        return status;
    }
}
