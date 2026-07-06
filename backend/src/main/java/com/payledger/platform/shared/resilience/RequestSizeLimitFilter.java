package com.payledger.platform.shared.resilience;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
@Order(10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final ResilienceProperties properties;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(
            ResilienceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long maxBytes = properties.getMaxRequestBodyBytes();
        long contentLength = request.getContentLengthLong();

        if (maxBytes > 0 && contentLength > maxBytes) {
            writeError(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "REQUEST_BODY_TOO_LARGE",
                    "Request body exceeds the configured limit of "
                            + maxBytes
                            + " bytes."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                Map.of(
                        "code", code,
                        "message", message,
                        "traceId", org.slf4j.MDC.get("traceId"),
                        "timestamp", Instant.now().toString()
                )
        );
    }
}
