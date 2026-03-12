CREATE TABLE RB_TEAM_TOURNAMENT (
    tournament_id VARCHAR(127) NOT NULL,
    team_number   INT          NOT NULL,
    PRIMARY KEY (tournament_id, team_number),
    FOREIGN KEY (tournament_id) REFERENCES RB_TOURNAMENT (id)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;
