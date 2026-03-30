CREATE TABLE RB_MATCH_VIDEO (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id VARCHAR(127) NOT NULL,
    match_level VARCHAR(50) NOT NULL,
    match_number INT NOT NULL,
    label VARCHAR(100) NOT NULL,
    video_url TEXT NOT NULL,
    FOREIGN KEY (tournament_id) REFERENCES RB_TOURNAMENT(id),
    UNIQUE KEY uk_match_video (tournament_id, match_level, match_number, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
