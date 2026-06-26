package com.company.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "insurance_policies")
public class InsurancePolicy {
    @Id
    private String policyId;

    @Column(nullable = false)
    private String memberName;

    @Column(nullable = false)
    private String behavior;

    @Column(nullable = false)
    private boolean eligible;

    @Column(nullable = false)
    private int delayMillis;

    @Column(nullable = false)
    private int httpStatus;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected InsurancePolicy() {
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getBehavior() {
        return behavior;
    }

    public boolean isEligible() {
        return eligible;
    }

    public int getDelayMillis() {
        return delayMillis;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
