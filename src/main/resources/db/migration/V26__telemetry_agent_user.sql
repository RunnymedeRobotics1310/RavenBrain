-- Service account for telemetry uploads. Password is reset on every startup from config.
INSERT INTO RB_USER (login, display_name, password_hash, roles, enabled, forgot_password)
VALUES ('telemetry-agent', 'Telemetry Agent', 'placeholder', 'ROLE_TELEMETRY_AGENT', 1, 0);
