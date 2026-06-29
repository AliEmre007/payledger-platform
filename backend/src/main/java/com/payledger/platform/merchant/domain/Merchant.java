package com.payledger.platform.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    protected Merchant() {
    }

    private Merchant(
            UUID id,
            String legalName,
            String displayName
    ) {
        this.id = id;
        this.legalName = legalName;
        this.displayName = displayName;
        this.status = MerchantStatus.PENDING;
    }

    public static Merchant onboard(
            String legalName,
            String displayName
    ) {
        return new Merchant(
                UUID.randomUUID(),
                legalName,
                displayName
        );
    }

    public UUID getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public void activate() {
        if (status != MerchantStatus.PENDING
                && status != MerchantStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Merchant can only be activated from PENDING or SUSPENDED."
            );
        }

        status = MerchantStatus.ACTIVE;
    }

    public void suspend() {
        if (status != MerchantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only ACTIVE merchants can be suspended."
            );
        }

        status = MerchantStatus.SUSPENDED;
    }

    public void close() {
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException(
                    "Merchant is already closed."
            );
        }

        status = MerchantStatus.CLOSED;
    }
}
