package com.payledger.platform.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "merchant_settlement_configs")
public class MerchantSettlementConfig {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "settlement_delay_days", nullable = false)
    private int settlementDelayDays;

    @Column(nullable = false)
    private boolean enabled;

    protected MerchantSettlementConfig() {
    }

    private MerchantSettlementConfig(
            UUID id,
            UUID merchantId,
            String currency,
            int settlementDelayDays
    ) {
        this.id = id;
        this.merchantId = merchantId;
        this.currency = currency;
        this.settlementDelayDays = settlementDelayDays;
        this.enabled = true;
    }

    public static MerchantSettlementConfig create(
            UUID merchantId,
            String currency,
            int settlementDelayDays
    ) {
        return new MerchantSettlementConfig(
                UUID.randomUUID(),
                merchantId,
                currency,
                settlementDelayDays
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getCurrency() {
        return currency;
    }

    public int getSettlementDelayDays() {
        return settlementDelayDays;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
