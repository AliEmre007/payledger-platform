package com.payledger.platform.customer.api;

import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerStatus;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.customer.domain.KycStatus;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        CustomerType customerType,
        String legalName,
        String email,
        CustomerStatus status,
        KycStatus kycStatus
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerType(),
                customer.getLegalName(),
                customer.getEmail(),
                customer.getStatus(),
                customer.getKycStatus()
        );
    }
}
