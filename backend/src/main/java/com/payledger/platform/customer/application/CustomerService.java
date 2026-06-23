package com.payledger.platform.customer.application;

import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.customer.infrastructure.CustomerRepository;
import com.payledger.platform.shared.error.ConflictException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer createCustomer(
            CustomerType customerType,
            String legalName,
            String email
    ) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (customerRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("A customer already exists for this email address.");
        }

        Customer customer = Customer.create(
                customerType,
                legalName.trim(),
                normalizedEmail
        );

        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found: " + customerId)
                );
    }
}
