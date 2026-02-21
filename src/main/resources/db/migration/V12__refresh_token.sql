CREATE TABLE RB_REFRESH_TOKEN
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(255) NOT NULL,
    refresh_token VARCHAR(4000) NOT NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT FALSE,
    date_created  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;
