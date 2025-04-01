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
    tournamentid VARCHAR(127) NOT NULL,
    matchnum     INT          NOT NULL,
    red1         INT          NOT NULL,
    red2         INT          NOT NULL,
    red3         INT          NOT NULL,
    blue1        INT          NOT NULL,
    blue2        INT          NOT NULL,
    blue3        INT          NOT NULL,
    redscore     INT          NULL,
    bluescore    INT          NULL,
    PRIMARY KEY (tournamentId, matchnum)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS RB_EVENT
(
    eventtimestamp TIMESTAMP(3)  NOT NULL,
    scoutname      VARCHAR(255)  NOT NULL,
    tournamentid   VARCHAR(127)  NOT NULL,
    matchid        INT           NOT NULL,
    alliance       VARCHAR(64)   NOT NULL,
    teamnumber     INT           NOT NULL,
    eventtype      VARCHAR(255)  NOT NULL,
    amount         DOUBLE        NOT NULL,
    note           VARCHAR(1024) NOT NULL,
    PRIMARY KEY (eventtimestamp, scoutname, eventtype)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS RB_COMMENT
(
    commenttimestamp TIMESTAMP(3)  NOT NULL,
    scoutname        VARCHAR(255)  NOT NULL,
    scoutrole        VARCHAR(64)   NOT NULL,
    teamnumber       INT           NOT NULL,
    comment          VARCHAR(1024) NOT NULL,
    PRIMARY KEY (commenttimestamp, scoutname)
)
    CHARSET = utf8mb4
    COLLATE utf8mb4_unicode_ci;
