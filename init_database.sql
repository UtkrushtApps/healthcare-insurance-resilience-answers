CREATE EXTENSION IF NOT EXISTS pgcrypto;

DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS insurance_policies;

CREATE TABLE insurance_policies (
  policy_id VARCHAR(64) PRIMARY KEY,
  member_name VARCHAR(160) NOT NULL,
  behavior VARCHAR(32) NOT NULL,
  eligible BOOLEAN NOT NULL,
  delay_millis INTEGER NOT NULL,
  http_status INTEGER NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE appointments (
  id UUID PRIMARY KEY,
  patient_name VARCHAR(160) NOT NULL,
  clinic_name VARCHAR(160) NOT NULL,
  insurer_policy_id VARCHAR(64) NOT NULL,
  status VARCHAR(48) NOT NULL,
  appointment_start TIMESTAMPTZ NOT NULL,
  confirmed_at TIMESTAMPTZ NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO insurance_policies (policy_id, member_name, behavior, eligible, delay_millis, http_status) VALUES
  ('POL-SLOW-001', 'Asha Mehta', 'SLOW_ELIGIBLE', true, 20000, 200),
  ('POL-OK-001', 'Rahul Iyer', 'FAST_ELIGIBLE', true, 100, 200),
  ('POL-DENIED-001', 'Meera Shah', 'FAST_INELIGIBLE', false, 100, 200),
  ('POL-DOWN-001', 'Vikram Rao', 'UNAVAILABLE', false, 100, 503),
  ('POL-BAD-001', 'Nisha Kapoor', 'BAD_REQUEST', false, 100, 400),
  ('POL-FLAKY-001', 'Arjun Nair', 'FLAKY', true, 1200, 502);

INSERT INTO appointments (id, patient_name, clinic_name, insurer_policy_id, status, appointment_start) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Asha Mehta', 'Cardiology East', 'POL-SLOW-001', 'SCHEDULED', now() + interval '2 days'),
  ('22222222-2222-2222-2222-222222222222', 'Rahul Iyer', 'Orthopedics West', 'POL-OK-001', 'SCHEDULED', now() + interval '3 days'),
  ('33333333-3333-3333-3333-333333333333', 'Meera Shah', 'Dermatology North', 'POL-DENIED-001', 'SCHEDULED', now() + interval '4 days'),
  ('44444444-4444-4444-4444-444444444444', 'Vikram Rao', 'Cardiology East', 'POL-DOWN-001', 'SCHEDULED', now() + interval '5 days'),
  ('55555555-5555-5555-5555-555555555555', 'Nisha Kapoor', 'Pediatrics South', 'POL-BAD-001', 'SCHEDULED', now() + interval '6 days'),
  ('66666666-6666-6666-6666-666666666666', 'Arjun Nair', 'Neurology Central', 'POL-FLAKY-001', 'SCHEDULED', now() + interval '7 days');
