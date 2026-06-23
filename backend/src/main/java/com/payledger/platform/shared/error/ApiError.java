package com.payledger.platform.shared.error;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String traceId,
        Instant timestamp
) {
}
