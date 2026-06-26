#!/usr/bin/env bash
set -euo pipefail
cd /root/task
docker compose up -d --build
printf '\nLocal task environment is starting.\n'
printf 'Application URL: http://127.0.0.1:8080\n'
printf 'Health endpoint: http://127.0.0.1:8080/actuator/health\n'
printf 'Sample workflow: POST http://127.0.0.1:8080/api/appointments/{appointmentId}/confirm\n'
printf 'Seeded slow appointment: 11111111-1111-1111-1111-111111111111\n\n'
