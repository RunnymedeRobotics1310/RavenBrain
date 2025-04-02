ALTER DATABASE
    CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS RB_TOURNAMENT
(
    id             VARCHAR(127) PRIMARY KEY,
    tournamentname VARCHAR(255) NOT NULL,
    starttime      TIMESTAMP(3) NOT NULL,
    endtime        TIMESTAMP(3) NOT NULL
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS RB_SCHEDULE
(
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    tournamentid VARCHAR(100) NOT NULL,
    matchnum     INT          NOT NULL,
    red1         INT          NOT NULL,
    red2         INT          NOT NULL,
    red3         INT          NOT NULL,
    blue1        INT          NOT NULL,
    blue2        INT          NOT NULL,
    blue3        INT          NOT NULL,
    redscore     INT          NULL,
    bluescore    INT          NULL,
    UNIQUE KEY tourn_match (tournamentId, matchnum)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS RB_EVENT
(
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    eventtimestamp TIMESTAMP(3)  NOT NULL,
    scoutname      VARCHAR(255)  NOT NULL,
    tournamentid   VARCHAR(127)  NOT NULL,
    matchid        INT           NOT NULL,
    alliance       VARCHAR(64)   NOT NULL,
    teamnumber     INT           NOT NULL,
    eventtype      VARCHAR(255)  NOT NULL,
    amount         DOUBLE        NOT NULL,
    note           VARCHAR(1024) NOT NULL,
    UNIQUE KEY time_scout_type (eventtimestamp, scoutname, eventtype)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS RB_COMMENT
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    commenttimestamp TIMESTAMP(3)  NOT NULL,
    scoutname        VARCHAR(255)  NOT NULL,
    scoutrole        VARCHAR(64)   NOT NULL,
    teamnumber       INT           NOT NULL,
    comment          VARCHAR(1024) NOT NULL,
    UNIQUE KEY time_scout (commenttimestamp, scoutname)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;
