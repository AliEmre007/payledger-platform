package com.payledger.platform.customer.api;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        Customer customer = customerService.createCustomer(
                request.customerType(),
                request.legalName(),
                request.email()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CustomerResponse.from(customer));
    }

    @GetMapping("/{customerId}")
    public CustomerResponse getCustomer(@PathVariable UUID customerId) {
        return CustomerResponse.from(customerService.getCustomer(customerId));
    }
}
