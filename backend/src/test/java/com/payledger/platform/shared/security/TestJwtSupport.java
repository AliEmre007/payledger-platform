package com.payledger.platform.shared.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class TestJwtSupport {

    private TestJwtSupport() {
    }

    public static RequestPostProcessor customerJwt() {
        return customerJwt("keycloak-alice-test-subject");
    }

    public static RequestPostProcessor customerJwt(String subject) {
        return jwt()
                .jwt(token -> token
                        .subject(subject)
                        .claim("preferred_username", "alice")
                        .claim(
                                "realm_access",
                                Map.of(
                                        "roles",
                                        List.of("CUSTOMER")
                                )
                        )
                )
                .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                );
    }
}
