package com.company.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String clinicName;

    @Column(nullable = false)
    private String insurerPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(nullable = false)
    private OffsetDateTime appointmentStart;

    private OffsetDateTime confirmedAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    protected Appointment() {
    }

    public UUID getId() {
        return id;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getClinicName() {
        return clinicName;
    }

    public String getInsurerPolicyId() {
        return insurerPolicyId;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getAppointmentStart() {
        return appointmentStart;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void markConfirmed() {
        this.status = AppointmentStatus.CONFIRMED;
        this.confirmedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public void markDeclined() {
        this.status = AppointmentStatus.DECLINED;
        this.confirmedAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markPendingEligibility() {
        this.status = AppointmentStatus.PENDING_ELIGIBILITY_CHECK;
        this.confirmedAt = null;
        this.updatedAt = OffsetDateTime.now();
    }
}
