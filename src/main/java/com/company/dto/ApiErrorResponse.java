package com.company.dto;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        String code,
        String message,
        String correlationId,
        OffsetDateTime timestamp
) {
}
