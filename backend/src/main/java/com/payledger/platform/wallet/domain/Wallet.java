package com.payledger.platform.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    protected Wallet() {
    }

    private Wallet(UUID id, UUID customerId, String currency) {
        this.id = id;
        this.customerId = customerId;
        this.currency = currency;
        this.status = WalletStatus.ACTIVE;
    }

    public static Wallet create(UUID customerId, String currency) {
        return new Wallet(UUID.randomUUID(), customerId, currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCurrency() {
        return currency;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public void freeze() {
        if (status != WalletStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only ACTIVE wallets can be frozen."
            );
        }

        status = WalletStatus.FROZEN;
    }

    public void unfreeze() {
        if (status != WalletStatus.FROZEN) {
            throw new IllegalStateException(
                    "Only FROZEN wallets can be unfrozen."
            );
        }

        status = WalletStatus.ACTIVE;
    }

    public void close() {
        if (status == WalletStatus.CLOSED) {
            throw new IllegalStateException(
                    "Wallet is already closed."
            );
        }

        status = WalletStatus.CLOSED;
    }
}
