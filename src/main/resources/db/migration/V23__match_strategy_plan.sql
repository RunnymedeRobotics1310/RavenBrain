CREATE TABLE RB_MATCH_STRATEGY_PLAN
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id           VARCHAR(127) NOT NULL,
    match_level             VARCHAR(32)  NOT NULL,
    match_number            INT          NOT NULL,
    short_summary           VARCHAR(32)  NOT NULL DEFAULT '',
    strategy_text           TEXT,
    updated_by_user_id      BIGINT       NOT NULL,
    updated_by_display_name VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP(3) NOT NULL,
    UNIQUE KEY uk_plan_match (tournament_id, match_level, match_number),
    FOREIGN KEY (tournament_id) REFERENCES RB_TOURNAMENT(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE RB_MATCH_STRATEGY_DRAWING
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id                 BIGINT       NOT NULL,
    label                   VARCHAR(64)  NOT NULL,
    strokes                 LONGTEXT     NOT NULL,
    created_by_user_id      BIGINT       NOT NULL,
    created_by_display_name VARCHAR(255) NOT NULL,
    updated_by_user_id      BIGINT       NOT NULL,
    updated_by_display_name VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP(3) NOT NULL,
    updated_at              TIMESTAMP(3) NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES RB_MATCH_STRATEGY_PLAN(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
