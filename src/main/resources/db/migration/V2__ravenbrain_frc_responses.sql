CREATE TABLE IF NOT EXISTS RB_FRC_RESPONSES
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    lastcheck      TIMESTAMP(3)  NOT NULL,
    lastmodified   TIMESTAMP(3)  NOT NULL,
    processed      BIT           NOT NULL,
    statuscode     INT           NOT NULL,
    url            VARCHAR(255)  NOT NULL,
    body           LONGTEXT      NULL
    )
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;
