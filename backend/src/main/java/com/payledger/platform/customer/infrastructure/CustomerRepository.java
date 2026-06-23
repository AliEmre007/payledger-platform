package com.payledger.platform.customer.infrastructure;

import com.payledger.platform.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmailIgnoreCase(String email);
}
