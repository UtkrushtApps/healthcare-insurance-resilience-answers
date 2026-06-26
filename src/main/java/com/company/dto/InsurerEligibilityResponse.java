package com.company.dto;

public record InsurerEligibilityResponse(
        String policyId,
        boolean eligible,
        String decisionCode,
        String message
) {
}
