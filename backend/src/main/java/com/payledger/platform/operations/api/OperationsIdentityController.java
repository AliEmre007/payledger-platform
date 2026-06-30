package com.payledger.platform.operations.api;

import com.payledger.platform.identity.application.CustomerIdentityService;
import com.payledger.platform.identity.domain.CustomerIdentity;
import com.payledger.platform.operations.application.CurrentOperationActorService;
import com.payledger.platform.operations.application.OperationActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/customers/{customerId}/identities")
@PreAuthorize("hasAnyRole('OPERATIONS', 'ADMIN')")
public class OperationsIdentityController {

    private final CustomerIdentityService customerIdentityService;
    private final CurrentOperationActorService currentActorService;

    public OperationsIdentityController(
            CustomerIdentityService customerIdentityService,
            CurrentOperationActorService currentActorService
    ) {
        this.customerIdentityService = customerIdentityService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/keycloak")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerIdentityResponse linkKeycloakIdentity(
            @PathVariable UUID customerId,
            @Valid @RequestBody LinkKeycloakIdentityRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();
        CustomerIdentity identity = customerIdentityService.linkKeycloakIdentity(
                customerId,
                request.externalSubject(),
                actor.externalSubject(),
                request.reason()
        );

        return CustomerIdentityResponse.from(identity);
    }
}
