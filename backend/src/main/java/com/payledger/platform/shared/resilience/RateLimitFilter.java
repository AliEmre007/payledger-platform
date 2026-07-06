package com.payledger.platform.shared.resilience;

import com.payledger.platform.shared.observability.BusinessMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ResilienceProperties properties;
    private final BusinessMetrics metrics;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(
            ResilienceProperties properties,
            BusinessMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        ResilienceProperties.RateLimit config = properties.getRateLimit();

        if (!config.isEnabled() || !isLimitedRequest(request, config)) {
            filterChain.doFilter(request, response);
            return;
        }

        long nowMillis = clock.millis();
        long windowMillis = Math.max(1, config.getWindow().toMillis());
        String key = bucketKey(request);
        WindowCounter counter = counters.compute(
                key,
                (ignored, current) -> current == null
                        || nowMillis >= current.windowEndsAtMillis()
                        ? new WindowCounter(
                                nowMillis + windowMillis,
                                new AtomicInteger(0)
                        )
                        : current
        );

        int count = counter.count().incrementAndGet();

        if (count > config.getMaxRequestsPerWindow()) {
            metrics.rateLimited(request.getRequestURI());
            long retryAfterSeconds = Math.max(
                    1,
                    (counter.windowEndsAtMillis() - nowMillis + 999) / 1000
            );
            writeRateLimitExceeded(
                    response,
                    retryAfterSeconds
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLimitedRequest(
            HttpServletRequest request,
            ResilienceProperties.RateLimit config
    ) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        List<String> limitedPaths = config.getMoneyMovingPaths();

        return limitedPaths != null
                && limitedPaths.stream().anyMatch(path::startsWith);
    }

    private String bucketKey(HttpServletRequest request) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        String subject = null;

        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt) {
                subject = jwt.getSubject();
            } else if (authentication.getName() != null) {
                subject = authentication.getName();
            }
        }

        if (subject == null || subject.isBlank()) {
            subject = request.getRemoteAddr();
        }

        return subject + "|" + request.getRequestURI();
    }

    private void writeRateLimitExceeded(
            HttpServletResponse response,
            long retryAfterSeconds
    ) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                Map.of(
                        "code", "RATE_LIMIT_EXCEEDED",
                        "message",
                        "Too many requests. Retry after "
                                + retryAfterSeconds
                                + " seconds.",
                        "traceId", org.slf4j.MDC.get("traceId"),
                        "timestamp", Instant.now().toString()
                )
        );
    }

    private record WindowCounter(
            long windowEndsAtMillis,
            AtomicInteger count
    ) {
    }
}
