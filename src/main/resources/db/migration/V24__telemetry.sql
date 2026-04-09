CREATE TABLE RB_TELEMETRY_SESSION
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id      VARCHAR(64)  NOT NULL UNIQUE,
    team_number     INT          NOT NULL,
    robot_ip        VARCHAR(32)  NOT NULL,
    started_at      TIMESTAMP(3) NOT NULL,
    ended_at        TIMESTAMP(3) NULL,
    entry_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE RB_TELEMETRY_ENTRY
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id      BIGINT       NOT NULL,
    ts              TIMESTAMP(3) NOT NULL,
    entry_type      VARCHAR(32)  NOT NULL,
    nt_key          VARCHAR(255) NULL,
    nt_type         VARCHAR(32)  NULL,
    nt_value        TEXT         NULL,
    fms_raw         INT          NULL,
    INDEX idx_telemetry_session (session_id),
    INDEX idx_telemetry_ts (ts),
    INDEX idx_telemetry_key (nt_key),
    FOREIGN KEY (session_id) REFERENCES RB_TELEMETRY_SESSION(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
