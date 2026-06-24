package com.payledger.platform.operations.application;

import com.payledger.platform.shared.error.IdentityNotLinkedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CurrentOperationActorService {

    public OperationActor getCurrentActor() {
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

        return new OperationActor(subject);
    }
}
