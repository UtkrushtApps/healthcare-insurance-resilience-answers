package com.company.service;

import com.company.config.InsurerClientProperties;
import com.company.domain.Appointment;
import com.company.domain.AppointmentStatus;
import com.company.dto.AppointmentConfirmationResponse;
import com.company.dto.InsurerEligibilityResponse;
import com.company.exception.NotFoundException;
import com.company.integration.InsuranceClient;
import com.company.repository.AppointmentRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final InsuranceClient insuranceClient;
    private final InsurerClientProperties insurerProperties;
    private final MeterRegistry meterRegistry;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              InsuranceClient insuranceClient,
                              InsurerClientProperties insurerProperties,
                              MeterRegistry meterRegistry) {
        this.appointmentRepository = appointmentRepository;
        this.insuranceClient = insuranceClient;
        this.insurerProperties = insurerProperties;
        this.meterRegistry = meterRegistry;
    }

    public AppointmentConfirmationResponse confirmAppointment(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            incrementOutcome(appointment.getStatus().name(), "already_processed");
            return toResponse(appointment, "Appointment is already in a terminal or pending state");
        }

        log.info("event=appointment_confirmation_started appointmentId={} policyId={}", appointment.getId(), appointment.getInsurerPolicyId());
        EligibilityDecision decision = checkEligibilitySafely(appointment);
        return persistDecision(appointmentId, decision);
    }

    @Transactional(readOnly = true)
    public AppointmentConfirmationResponse getAppointment(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
        return toResponse(appointment, "Appointment retrieved");
    }

    private EligibilityDecision checkEligibilitySafely(Appointment appointment) {
        try {
            InsurerEligibilityResponse eligibility = insuranceClient.checkEligibility(appointment.getInsurerPolicyId())
                    .block(insurerProperties.overallTimeout());

            if (eligibility == null) {
                log.warn("event=insurer_empty_response appointmentId={} policyId={}", appointment.getId(), appointment.getInsurerPolicyId());
                return EligibilityDecision.unknown("Eligibility could not be verified; appointment is pending eligibility review");
            }
            if (!appointment.getInsurerPolicyId().equals(eligibility.policyId())) {
                log.warn("event=insurer_policy_mismatch appointmentId={} expectedPolicyId={} actualPolicyId={}",
                        appointment.getId(), appointment.getInsurerPolicyId(), eligibility.policyId());
                return EligibilityDecision.unknown("Eligibility response did not match the appointment policy; appointment is pending eligibility review");
            }
            if (eligibility.eligible()) {
                return EligibilityDecision.approved("Appointment confirmed after eligibility approval");
            }
            return EligibilityDecision.denied("Appointment declined because eligibility was not approved");
        } catch (InsuranceClient.NonRetryableEligibilityException ex) {
            log.info("event=insurer_non_retryable_response appointmentId={} policyId={} status={} message={}",
                    appointment.getId(), appointment.getInsurerPolicyId(), ex.statusCode(), ex.getMessage());
            return EligibilityDecision.denied("Appointment declined because the insurer returned a non-retryable eligibility response");
        } catch (CallNotPermittedException ex) {
            log.warn("event=insurer_circuit_open appointmentId={} policyId={}", appointment.getId(), appointment.getInsurerPolicyId());
            return EligibilityDecision.unknown("Eligibility service is temporarily unavailable; appointment is pending eligibility review");
        } catch (Exception ex) {
            log.warn("event=insurer_transient_failure appointmentId={} policyId={} exceptionType={} message={}",
                    appointment.getId(), appointment.getInsurerPolicyId(), ex.getClass().getSimpleName(), ex.getMessage());
            return EligibilityDecision.unknown("Eligibility could not be verified within the latency budget; appointment is pending eligibility review");
        }
    }

    @Transactional
    protected AppointmentConfirmationResponse persistDecision(UUID appointmentId, EligibilityDecision decision) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            incrementOutcome(appointment.getStatus().name(), "state_changed_before_persist");
            return toResponse(appointment, "Appointment is already in a terminal or pending state");
        }

        switch (decision.type()) {
            case APPROVED -> appointment.markConfirmed();
            case DENIED -> appointment.markDeclined();
            case UNKNOWN -> appointment.markPendingEligibility();
        }

        incrementOutcome(appointment.getStatus().name(), decision.type().name().toLowerCase());
        log.info("event=appointment_confirmation_completed appointmentId={} status={} decisionType={}",
                appointment.getId(), appointment.getStatus(), decision.type());
        return toResponse(appointment, decision.message());
    }

    private AppointmentConfirmationResponse toResponse(Appointment appointment, String message) {
        return new AppointmentConfirmationResponse(
                appointment.getId(),
                appointment.getStatus(),
                message,
                appointment.getAppointmentStart(),
                appointment.getConfirmedAt());
    }

    private void incrementOutcome(String status, String reason) {
        Counter.builder("appointment.confirmation.outcomes")
                .description("Appointment confirmation outcomes")
                .tag("status", status)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private record EligibilityDecision(DecisionType type, String message) {
        static EligibilityDecision approved(String message) {
            return new EligibilityDecision(DecisionType.APPROVED, message);
        }

        static EligibilityDecision denied(String message) {
            return new EligibilityDecision(DecisionType.DENIED, message);
        }

        static EligibilityDecision unknown(String message) {
            return new EligibilityDecision(DecisionType.UNKNOWN, message);
        }
    }

    private enum DecisionType {
        APPROVED,
        DENIED,
        UNKNOWN
    }
}
