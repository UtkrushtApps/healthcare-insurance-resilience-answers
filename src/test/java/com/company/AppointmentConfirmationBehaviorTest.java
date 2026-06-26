package com.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18080",
        "app.insurer.base-url=http://localhost:18080/simulator/insurer",
        "app.insurer.connect-timeout=300ms",
        "app.insurer.response-timeout=1s",
        "app.insurer.overall-timeout=2500ms",
        "app.insurer.max-attempts=2",
        "app.insurer.retry-backoff=100ms",
        "spring.datasource.url=jdbc:h2:mem:healthcare-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM appointments",
        "DELETE FROM insurance_policies",
        "INSERT INTO insurance_policies (policy_id, member_name, behavior, eligible, delay_millis, http_status, updated_at) VALUES ('POL-SLOW-001', 'Asha Mehta', 'SLOW_ELIGIBLE', true, 5000, 200, CURRENT_TIMESTAMP)",
        "INSERT INTO insurance_policies (policy_id, member_name, behavior, eligible, delay_millis, http_status, updated_at) VALUES ('POL-OK-001', 'Rahul Iyer', 'FAST_ELIGIBLE', true, 50, 200, CURRENT_TIMESTAMP)",
        "INSERT INTO insurance_policies (policy_id, member_name, behavior, eligible, delay_millis, http_status, updated_at) VALUES ('POL-DENIED-001', 'Meera Shah', 'FAST_INELIGIBLE', false, 50, 200, CURRENT_TIMESTAMP)",
        "INSERT INTO insurance_policies (policy_id, member_name, behavior, eligible, delay_millis, http_status, updated_at) VALUES ('POL-DOWN-001', 'Vikram Rao', 'UNAVAILABLE', false, 50, 503, CURRENT_TIMESTAMP)",
        "INSERT INTO insurance_policies (policy_id, member_name, behavior, eligible, delay_millis, http_status, updated_at) VALUES ('POL-BAD-001', 'Nisha Kapoor', 'BAD_REQUEST', false, 50, 400, CURRENT_TIMESTAMP)",
        "INSERT INTO appointments (id, patient_name, clinic_name, insurer_policy_id, status, appointment_start, confirmed_at, updated_at, version) VALUES ('11111111-1111-1111-1111-111111111111', 'Asha Mehta', 'Cardiology East', 'POL-SLOW-001', 'SCHEDULED', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0)",
        "INSERT INTO appointments (id, patient_name, clinic_name, insurer_policy_id, status, appointment_start, confirmed_at, updated_at, version) VALUES ('22222222-2222-2222-2222-222222222222', 'Rahul Iyer', 'Orthopedics West', 'POL-OK-001', 'SCHEDULED', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0)",
        "INSERT INTO appointments (id, patient_name, clinic_name, insurer_policy_id, status, appointment_start, confirmed_at, updated_at, version) VALUES ('33333333-3333-3333-3333-333333333333', 'Meera Shah', 'Dermatology North', 'POL-DENIED-001', 'SCHEDULED', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0)",
        "INSERT INTO appointments (id, patient_name, clinic_name, insurer_policy_id, status, appointment_start, confirmed_at, updated_at, version) VALUES ('44444444-4444-4444-4444-444444444444', 'Vikram Rao', 'Cardiology East', 'POL-DOWN-001', 'SCHEDULED', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0)",
        "INSERT INTO appointments (id, patient_name, clinic_name, insurer_policy_id, status, appointment_start, confirmed_at, updated_at, version) VALUES ('55555555-5555-5555-5555-555555555555', 'Nisha Kapoor', 'Pediatrics South', 'POL-BAD-001', 'SCHEDULED', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0)"
})
class AppointmentConfirmationBehaviorTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void slowEligibilityDoesNotMakeUserWaitForFullRemoteDelay() throws Exception {
        UUID appointmentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        long started = System.nanoTime();
        MvcResult result = mockMvc.perform(post("/api/appointments/{appointmentId}/confirm", appointmentId)).andReturn();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(3500);
        assertThat(result.getResponse().getStatus()).isIn(HttpStatus.OK.value(), HttpStatus.ACCEPTED.value());
        assertThat(result.getResponse().getContentAsString()).contains("PENDING_ELIGIBILITY_CHECK");

        MvcResult current = mockMvc.perform(get("/api/appointments/{appointmentId}", appointmentId)).andReturn();
        assertThat(current.getResponse().getContentAsString()).contains("PENDING_ELIGIBILITY_CHECK");
    }

    @Test
    void unavailableInsurerLeavesAppointmentInSafeObservableState() throws Exception {
        UUID appointmentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        MvcResult confirm = mockMvc.perform(post("/api/appointments/{appointmentId}/confirm", appointmentId)).andReturn();
        MvcResult current = mockMvc.perform(get("/api/appointments/{appointmentId}", appointmentId)).andReturn();

        assertThat(confirm.getResponse().getStatus()).isIn(HttpStatus.OK.value(), HttpStatus.ACCEPTED.value());
        assertThat(current.getResponse().getContentAsString()).contains("PENDING_ELIGIBILITY_CHECK");
    }

    @Test
    void eligiblePolicyConfirmsAppointment() throws Exception {
        UUID appointmentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        MvcResult confirm = mockMvc.perform(post("/api/appointments/{appointmentId}/confirm", appointmentId)).andReturn();

        assertThat(confirm.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(confirm.getResponse().getContentAsString()).contains("CONFIRMED");
        assertThat(confirm.getResponse().getContentAsString()).contains("confirmedAt");
    }

    @Test
    void ineligiblePolicyDeclinesAppointment() throws Exception {
        UUID appointmentId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        MvcResult confirm = mockMvc.perform(post("/api/appointments/{appointmentId}/confirm", appointmentId)).andReturn();

        assertThat(confirm.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(confirm.getResponse().getContentAsString()).contains("DECLINED");
    }

    @Test
    void nonRetryableInsurerResponseDoesNotRetryAndDoesNotConfirm() throws Exception {
        UUID appointmentId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        long started = System.nanoTime();
        MvcResult confirm = mockMvc.perform(post("/api/appointments/{appointmentId}/confirm", appointmentId)).andReturn();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(1500);
        assertThat(confirm.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(confirm.getResponse().getContentAsString()).contains("DECLINED");
        assertThat(confirm.getResponse().getContentAsString()).doesNotContain("CONFIRMED");
    }
}
