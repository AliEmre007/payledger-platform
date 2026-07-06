package com.payledger.platform.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
public class SecurityConfiguration {

    private final boolean prometheusPublic;

    public SecurityConfiguration(
            @Value("${payledger.management.prometheus-public:false}")
            boolean prometheusPublic
    ) {
        this.prometheusPublic = prometheusPublic;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                            "/actuator/health",
                            "/actuator/health/**",
                            "/api/v1/provider/webhooks"
                    ).permitAll();

                    if (prometheusPublic) {
                        authorize.requestMatchers(
                                "/actuator/prometheus"
                        ).permitAll();
                    }

                    authorize.anyRequest().authenticated();
                }
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        keycloakJwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();
    }

    private Converter<Jwt, AbstractAuthenticationToken>
    keycloakJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter =
                new JwtGrantedAuthoritiesConverter();

        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();

            Collection<GrantedAuthority> scopeAuthorities =
                    scopeConverter.convert(jwt);

            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }

            Map<String, Object> realmAccess =
                    jwt.getClaimAsMap("realm_access");

            if (realmAccess != null) {
                Object roles = realmAccess.get("roles");

                if (roles instanceof Collection<?> roleCollection) {
                    roleCollection.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(String::trim)
                            .filter(role -> !role.isBlank())
                            .map(role -> new SimpleGrantedAuthority(
                                    "ROLE_" + role
                            ))
                            .forEach(authorities::add);
                }
            }

            String principalName = jwt.getClaimAsString(
                    "preferred_username"
            );

            if (principalName == null || principalName.isBlank()) {
                principalName = jwt.getSubject();
            }

            return new JwtAuthenticationToken(
                    jwt,
                    authorities,
                    principalName
            );
        };
    }
}
