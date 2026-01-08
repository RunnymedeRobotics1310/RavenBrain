DELETE FROM RB_USER;
ALTER TABLE RB_EVENT ADD COLUMN userid bigint AFTER eventtimestamp;

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (1, "superuser", "SuperUser", "secret", "ROLE_SUPERUSER", 1, 0);

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (2, "Avocado", "Avocado", "secret", "ROLE_ADMIN", 0, 0);
UPDATE RB_EVENT set userid = 2 where scoutname = 'Avocado';

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (3, "L", "L", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 3 where scoutname = "L";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (4, "O", "O", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 4 where scoutname = "O";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (5, "T", "T", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 5 where scoutname = "T";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (6, "U", "U", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 6 where scoutname = "U";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (7, "JohnDataScout", "John DataScout", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 7 where scoutname = "John DataScout";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (8, "Quentin", "Quentin", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 8 where scoutname = "Quentin";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (9, "Tony", "Tony", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 9 where scoutname = "Tony";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (10, "Y", "Y", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 10 where scoutname = "Y";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (11, "RachealEng", "Racheal Eng", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 11 where scoutname = "Racheal Eng";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (12, "Nick", "Nick", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 12 where scoutname = "Nick";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (13, "Leia", "Leia", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 13 where scoutname = "Leia";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (14, "Practice", "Practice", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 14 where scoutname = "Practice";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (15, "Maya", "Maya", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 15 where scoutname = "Maya";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (16, "Alan", "Alan", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 16 where scoutname = "Alan";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (17, "Jackson", "Jackson", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 17 where scoutname = "Jackson";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (18, "AaronLe", "Aaron Le", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 18 where scoutname = "Aaron Le";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (19, "Meghan", "Meghan", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT set userid = 19 where scoutname = "Meghan";

ALTER TABLE RB_EVENT drop constraint time_scout_type;
ALTER TABLE RB_EVENT drop column scoutname;

ALTER TABLE RB_EVENT MODIFY userid BIGINT NOT NULL;
ALTER TABLE RB_EVENT add constraint time_user_type unique (eventtimestamp, userid, eventtype);

