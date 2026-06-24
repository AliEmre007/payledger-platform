package com.payledger.platform.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    @Version
    @Column(nullable = false)
    private long version;

    protected Customer() {
    }

    private Customer(
            UUID id,
            CustomerType customerType,
            String legalName,
            String email
    ) {
        this.id = id;
        this.customerType = customerType;
        this.legalName = legalName;
        this.email = email;
        this.status = CustomerStatus.PENDING_KYC;
        this.kycStatus = KycStatus.NOT_STARTED;
    }

    public static Customer create(
            CustomerType customerType,
            String legalName,
            String email
    ) {
        return new Customer(
                UUID.randomUUID(),
                customerType,
                legalName,
                email
        );
    }

    public UUID getId() {
        return id;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getEmail() {
        return email;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void submitKycForReview() {
        if (kycStatus != KycStatus.NOT_STARTED
                && kycStatus != KycStatus.REJECTED) {
            throw new IllegalStateException(
                    "KYC can only be submitted from NOT_STARTED or REJECTED."
            );
        }

        kycStatus = KycStatus.PENDING;
        status = CustomerStatus.PENDING_KYC;
    }

    public void approveKyc() {
        if (kycStatus != KycStatus.PENDING) {
            throw new IllegalStateException(
                    "KYC can only be approved from PENDING."
            );
        }

        kycStatus = KycStatus.APPROVED;
        status = CustomerStatus.ACTIVE;
    }

    public void rejectKyc() {
        if (kycStatus != KycStatus.PENDING) {
            throw new IllegalStateException(
                    "KYC can only be rejected from PENDING."
            );
        }

        kycStatus = KycStatus.REJECTED;
        status = CustomerStatus.PENDING_KYC;
    }
}
