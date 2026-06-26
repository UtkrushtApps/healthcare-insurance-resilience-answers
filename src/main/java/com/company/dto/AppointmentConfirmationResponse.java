package com.company.dto;

import com.company.domain.AppointmentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentConfirmationResponse(
        UUID appointmentId,
        AppointmentStatus status,
        String message,
        OffsetDateTime appointmentStart,
        OffsetDateTime confirmedAt
) {
}
