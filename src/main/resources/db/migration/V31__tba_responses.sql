-- Response cache for The Blue Alliance API calls. Mirrors RB_FRC_RESPONSES (V2) exactly,
-- with one additional `etag` column since TBA supports both If-None-Match and
-- If-Modified-Since conditional requests (per TBA's "Efficient Querying" guidance).
-- NOT NULL constraints on lastcheck/lastmodified/processed/statuscode/url match V2 so that
-- Micronaut Data records using primitive boolean/int fields cannot blow up on NULL reads.
CREATE TABLE IF NOT EXISTS RB_TBA_RESPONSES
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    lastcheck    TIMESTAMP(3) NOT NULL,
    lastmodified TIMESTAMP(3) NOT NULL,
    etag         VARCHAR(127) NULL,
    processed    BIT          NOT NULL,
    statuscode   INT          NOT NULL,
    url          VARCHAR(255) NOT NULL,
    body         LONGTEXT     NULL
);
