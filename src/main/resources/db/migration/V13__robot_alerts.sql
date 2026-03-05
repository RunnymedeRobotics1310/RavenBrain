CREATE TABLE RB_ROBOT_ALERT
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    tournament_id VARCHAR(32)   NOT NULL,
    team_number   INT           NOT NULL,
    user_id       BIGINT        NOT NULL,
    created_at    TIMESTAMP(3)  NOT NULL,
    alert         VARCHAR(1024) NOT NULL,
    UNIQUE KEY alert_time_user (created_at, user_id)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;
