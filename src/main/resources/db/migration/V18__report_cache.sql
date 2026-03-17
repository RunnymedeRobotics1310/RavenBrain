CREATE TABLE IF NOT EXISTS RB_REPORT_CACHE
(
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    cachekey VARCHAR(255) NOT NULL,
    body     LONGTEXT     NOT NULL,
    created  TIMESTAMP(3) NOT NULL,
    UNIQUE KEY uq_report_cache_key (cachekey)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;
