package com.payledger.platform.identity.application;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.identity.domain.CustomerIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CustomerIdentityServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerIdentityService customerIdentityService;

    @Test
    void linksKeycloakSubjectToCustomerAndResolvesIt() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Customer customer = customerService.createCustomer(
                CustomerType.PERSONAL,
                "Identity Test " + suffix,
                "identity-" + suffix + "@example.test"
        );

        String keycloakSubject = "keycloak-subject-" + suffix;

        CustomerIdentity identity =
                customerIdentityService.linkKeycloakIdentity(
                        customer.getId(),
                        keycloakSubject
                );

        AuthenticatedCustomer authenticatedCustomer =
                customerIdentityService.resolveKeycloakSubject(
                        keycloakSubject
                );

        assertThat(identity.getCustomerId())
                .isEqualTo(customer.getId());

        assertThat(authenticatedCustomer.customerId())
                .isEqualTo(customer.getId());

        assertThat(authenticatedCustomer.externalSubject())
                .isEqualTo(keycloakSubject);
    }
}
