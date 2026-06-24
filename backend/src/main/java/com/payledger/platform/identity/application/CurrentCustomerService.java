package com.payledger.platform.identity.application;

import com.payledger.platform.shared.error.IdentityNotLinkedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentCustomerService {

    private final CustomerIdentityService customerIdentityService;

    public CurrentCustomerService(
            CustomerIdentityService customerIdentityService
    ) {
        this.customerIdentityService = customerIdentityService;
    }

    @Transactional(readOnly = true)
    public AuthenticatedCustomer getCurrentCustomer() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IdentityNotLinkedException(
                    "The authenticated request does not contain a JWT subject."
            );
        }

        String subject = jwtAuthentication.getToken().getSubject();

        if (subject == null || subject.isBlank()) {
            throw new IdentityNotLinkedException(
                    "The authenticated request does not contain a JWT subject."
            );
        }

        return customerIdentityService.resolveKeycloakSubject(subject);
    }
}
