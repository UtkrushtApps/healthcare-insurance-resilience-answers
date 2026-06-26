package com.company.controller;

import com.company.domain.AppointmentStatus;
import com.company.dto.AppointmentConfirmationResponse;
import com.company.service.AppointmentService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/{appointmentId}/confirm")
    public ResponseEntity<AppointmentConfirmationResponse> confirm(@PathVariable UUID appointmentId) {
        AppointmentConfirmationResponse response = appointmentService.confirmAppointment(appointmentId);
        if (response.status() == AppointmentStatus.PENDING_ELIGIBILITY_CHECK) {
            return ResponseEntity.accepted().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<AppointmentConfirmationResponse> get(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(appointmentService.getAppointment(appointmentId));
    }
}
