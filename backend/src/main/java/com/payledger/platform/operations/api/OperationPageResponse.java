package com.payledger.platform.operations.api;

import java.util.List;

public record OperationPageResponse<T>(
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        List<T> items
) {
}
