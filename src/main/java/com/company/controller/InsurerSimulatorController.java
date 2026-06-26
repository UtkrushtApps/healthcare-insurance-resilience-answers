package com.company.controller;

import com.company.domain.InsurancePolicy;
import com.company.dto.InsurerEligibilityResponse;
import com.company.exception.NotFoundException;
import com.company.repository.InsurancePolicyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulator/insurer")
public class InsurerSimulatorController {
    private final InsurancePolicyRepository insurancePolicyRepository;

    public InsurerSimulatorController(InsurancePolicyRepository insurancePolicyRepository) {
        this.insurancePolicyRepository = insurancePolicyRepository;
    }

    @GetMapping("/{policyId}/eligibility")
    public ResponseEntity<InsurerEligibilityResponse> eligibility(@PathVariable String policyId) throws InterruptedException {
        InsurancePolicy policy = insurancePolicyRepository.findById(policyId)
                .orElseThrow(() -> new NotFoundException("Policy not found"));
        Thread.sleep(policy.getDelayMillis());
        if (policy.getHttpStatus() >= 400) {
            return ResponseEntity.status(HttpStatus.valueOf(policy.getHttpStatus()))
                    .body(new InsurerEligibilityResponse(policy.getPolicyId(), false, policy.getBehavior(), "Insurer simulator returned an error"));
        }
        return ResponseEntity.ok(new InsurerEligibilityResponse(
                policy.getPolicyId(),
                policy.isEligible(),
                policy.getBehavior(),
                policy.isEligible() ? "Eligible" : "Not eligible"));
    }
}
