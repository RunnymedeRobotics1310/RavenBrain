---
title: "feat: Identify and match telemetry sessions to tournaments and matches"
type: feat
status: active
date: 2026-04-17
---

# feat: Identify and match telemetry sessions to tournaments and matches

## Overview

Augment network-table telemetry sessions so each session can be unambiguously tied to a tournament, match level, and match number, using values the robot publishes under `/FMSInfo/*` (notably `/FMSInfo/MatchNumber`, e.g., `P7`, `Q14`, `SF3`, `F1`). This makes it possible to join telemetry to `RB_SCHEDULE`, `RB_EVENT` (scout events), `RB_COMMENT`, and `RB_MATCH_STRATEGY_PLAN` for a given match, enabling telemetry-driven reporting in RavenBrain.

**Key invariant (confirmed with user):** A telemetry session corresponds to exactly one match. Sessions end when the robot is disabled, so there is never more than one `/FMSInfo/MatchNumber` per session. This lets us store match identity directly on the session — no segment table needed.

Today, `RB_TELEMETRY_SESSION` only knows `session_id`, `team_number`, `robot_ip`, and timestamps. `RB_TELEMETRY_ENTRY` blindly stores the `/FMSInfo/MatchNumber` key without extracting structured match identity, so telemetry can't be joined to anything match-keyed.

## Problem Frame

**User:** Drive team / expert scouts / report consumers within RavenBrain.

**Problem:** Telemetry data is collected into `RB_TELEMETRY_SESSION` / `RB_TELEMETRY_ENTRY` but has no structural link to the FRC match it describes. Without a match key, telemetry is an island — unjoinable to scout events, schedules, or strategy plans.

**What we know:**
- The robot publishes `/FMSInfo/MatchNumber` as a string like `P7`, `Q14`, `SF3`, `F1`. The first character(s) indicate level; the remainder is the match number.
- FMSInfo also publishes other identifying keys (e.g., `EventName`, `IsRedAlliance`, `StationNumber`, `MatchTime`).
- One session = one match (robot disable ends the session).
- `RB_TOURNAMENT` keys on `id` (FMS event code + season).
- `RB_SCHEDULE` keys on `(tournamentId, level, matchnum)` — this is the join target.

**Goal:** Enrich each telemetry session with match-level identity so downstream queries can join on `(tournament_id, match_level, match_number)`.

## Requirements Trace

- R1. Parse `/FMSInfo/MatchNumber` (e.g., `P7`, `Q14`, `SF3`, `F1`) into `(match_level, match_number)` at ingest time.
- R2. Persist structured match identity on the session, with first-class columns that can be indexed and joined.
- R3. Attempt tournament resolution from FMS data (`/FMSInfo/EventName` or equivalent) with fallback to the currently-watched tournament at session `started_at`.
- R4. Expose the enriched identity via the telemetry REST API so clients can list sessions by match.
- R5. Existing telemetry upload flow continues to work unchanged for older agents that don't publish `/FMSInfo/MatchNumber` — null match identity is valid.
- R6. Backfill: provide a one-time routine to populate match identity on existing sessions from already-ingested entries.

## Scope Boundaries

- **In scope:** Parsing FMSInfo keys during ingest, schema additions on the session table, ingest-time enrichment, backfill, API exposure of match identity, query endpoints to find telemetry by match.
- **Out of scope:** Telemetry visualization / charting, match-replay UI, cross-team telemetry sync, automatic alliance inference beyond what FMS publishes, multi-match session handling (explicitly excluded — one session = one match).

### Deferred to Separate Tasks

- Telemetry reporting / dashboards keyed on match — follows once the identity is in place.
- Robot-side changes to what NT keys are published — RavenEye / robot code repos.

## Context & Research

### Relevant Code and Patterns

- `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetrySession.java` — session record.
- `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryEntry.java` — entry record (has `nt_key`, `nt_value`).
- `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryService.java` — `bulkInsertEntries` is the right insertion seam for ingest-time enrichment.
- `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryApi.java` — REST surface; add query endpoints here.
- `src/main/java/ca/team1310/ravenbrain/frcapi/model/TournamentLevel.java` — canonical level enum. NB: only `None/Practice/Qualification/Playoff`, so `SF`/`QF`/`F` all map to `Playoff`. Store a separate `playoff_round` for finer grain.
- `src/main/java/ca/team1310/ravenbrain/schedule/ScheduleRecord.java` — target of joins: `(tournamentId, level, matchnum)`.
- `src/main/java/ca/team1310/ravenbrain/tournament/WatchedTournamentService.java` — source for fallback tournament resolution.
- `src/main/resources/db/migration/V24__telemetry.sql` — current telemetry schema.
- `src/main/java/ca/team1310/ravenbrain/matchstrategy/MatchStrategyApi.java:81` — proven `(tournamentId, matchLevel, matchNumber)` URL shape to mirror.

### Institutional Learnings

- Recent `feat/field-map-calibration` merge (PR #75) shows the repo's pattern for adding a new REST + schema feature in one migration bump. Follow that shape.
- `RB_SCHEDULE.level` stores `TournamentLevel` via its string name — match on the same enum spelling.

### External References

- FRC `FMSInfo` NetworkTables keys are published by the roboRIO's DriverStation. The `MatchNumber` label prefix convention is community convention, not enforced by WPILib, so parsing must be defensive.

## Key Technical Decisions

- **Single-row identity on the session.** Because one session = one match, just add columns on `RB_TELEMETRY_SESSION`. No segment table, no junction table.
- **Match label parsing.** Store the verbatim `match_label` (e.g., `SF3`) alongside the parsed `(level, number)`. `match_level` uses the existing `TournamentLevel` enum (`Practice | Qualification | Playoff`); `playoff_round` captures the finer round (`QF`/`SF`/`F`). Unknown prefixes store the raw label with null structured fields.
- **Tournament resolution order.** (1) `/FMSInfo/EventName` NT value if present — look up by `RB_TOURNAMENT.code` for the active season. (2) Fallback: the watched tournament whose `starttime <= session.started_at <= endtime` (single match wins; otherwise null).
- **First-write-wins for identity.** Once a session has a non-null `match_label`, later ingest batches for the same session do not overwrite it. Normally this won't happen (FMS key is published once early), but if it somehow shifts we log a warning and keep the first value. This keeps the invariant honest: one session, one match.
- **Ingest-time enrichment.** Do the parsing inside `bulkInsertEntries` — scan the incoming batch for `/FMSInfo/MatchNumber` (and supporting keys) and update the session in the same transaction. Keeps writes atomic.
- **Backfill is idempotent.** An admin endpoint re-runs the same enrichment logic over sessions where `match_label IS NULL` using already-stored entries.
- **No backwards-compatibility shim on ingest.** Agents that don't publish FMSInfo simply leave the fields null (acceptable per R5).

## Open Questions

### Resolved During Planning

- *Multi-match sessions?* Not possible — robot disable ends the session. One session = one match. (User-confirmed.)
- *Where do we parse?* Inside `TelemetryService.bulkInsertEntries`, which already handles the transaction boundary.
- *Playoff finer-grained levels?* Separate `playoff_round` column ('QF'/'SF'/'F'); `match_level` stays aligned with `TournamentLevel`.

### Deferred to Implementation

- Exact canonical form for the `/FMSInfo/EventName` payload — inspect captured samples in `RB_TELEMETRY_ENTRY` during implementation.
- Whether `completeSession` should log if the identity is still null (could indicate an agent or parser issue).

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```
Ingest flow
===========
POST /api/telemetry/session/{sessionId}/data
        |
        v
TelemetryService.bulkInsertEntries(sessionId, entries)
        |
        +--> (1) batch-insert RB_TELEMETRY_ENTRY rows (existing behavior)
        |
        +--> (2) scan entries for FMSInfo keys
        |         - /FMSInfo/MatchNumber  -> parse into (level, number, label)
        |         - /FMSInfo/EventName    -> tournament code candidate
        |
        +--> (3) if session.match_label IS NULL and a label was observed:
        |         - resolve tournament (EventName lookup -> watched-tournament fallback)
        |         - UPDATE RB_TELEMETRY_SESSION with identity fields
        |
        +--> commit transaction

Data model
==========
RB_TELEMETRY_SESSION
  + tournament_id        VARCHAR(32) NULL   (loose ref to RB_TOURNAMENT.id)
  + match_label          VARCHAR(16) NULL   ("P7","Q14","SF3","F1")
  + match_level          VARCHAR(16) NULL   (Practice|Qualification|Playoff)
  + match_number         INT         NULL
  + playoff_round        VARCHAR(8)  NULL   ("QF"|"SF"|"F")
  + fms_event_name       VARCHAR(64) NULL   (raw NT value, for audit)
  INDEX (tournament_id, match_level, match_number)

MatchLabelParser (utility)
  "P7"   -> (Practice,       7,   null)
  "Q14"  -> (Qualification,  14,  null)
  "SF3"  -> (Playoff,        3,   "SF")
  "QF2"  -> (Playoff,        2,   "QF")
  "F1"   -> (Playoff,        1,   "F")
  other  -> (null,           null,null)   // stored as label only
```

## Implementation Units

- [ ] **Unit 1: Match label parser**

**Goal:** Pure utility that converts `/FMSInfo/MatchNumber` values into `(TournamentLevel, Integer matchNumber, String playoffRound)` with defensive handling of empty/malformed values.

**Requirements:** R1

**Dependencies:** None

**Files:**
- Create: `src/main/java/ca/team1310/ravenbrain/telemetry/MatchLabelParser.java`
- Test: `src/test/java/ca/team1310/ravenbrain/telemetry/MatchLabelParserTest.java`

**Approach:**
- Static `parse(String label)` returning a small record (e.g., `ParsedMatchLabel(TournamentLevel level, Integer number, String playoffRound, String rawLabel)`).
- Prefix-to-level map: `P`→Practice, `Q`→Qualification, `QF/SF/F`→Playoff (also capture the playoff round).
- Reject (null structured fields, keep raw label) for empty strings, `0`, missing digits, unknown prefixes.
- Trim whitespace; upper-case prefix only.

**Patterns to follow:**
- Tiny utility record style like `src/main/java/ca/team1310/ravenbrain/eventlog/TournamentEventTypePair.java`.

**Test scenarios:**
- Happy path: `"P7"` parses to Practice/7/null, raw `"P7"`.
- Happy path: `"Q14"` parses to Qualification/14/null.
- Happy path: `"SF3"` parses to Playoff/3/"SF"; `"QF2"` to Playoff/2/"QF"; `"F1"` to Playoff/1/"F".
- Edge case: `" q 14 "` with stray whitespace parses to Qualification/14.
- Edge case: `""`, `null`, `"0"` return all-null structured fields with the raw stored verbatim.
- Edge case: `"X99"` (unknown prefix) returns all-null structured fields with raw `"X99"`.
- Edge case: `"Q"` (prefix but no number) returns null number.
- Edge case: very large match number `"Q999"` parses correctly.

**Verification:**
- All test scenarios above pass.

---

- [ ] **Unit 2: Schema migration (session match identity columns)**

**Goal:** Add match identity columns to `RB_TELEMETRY_SESSION`.

**Requirements:** R2

**Dependencies:** None

**Files:**
- Create: `src/main/resources/db/migration/V28__telemetry_match_identity.sql`

**Approach:**
- `ALTER TABLE RB_TELEMETRY_SESSION` to add `tournament_id`, `match_label`, `match_level`, `match_number`, `playoff_round`, `fms_event_name`, all NULLable.
- Add index: `(tournament_id, match_level, match_number)`.
- No FK to `RB_TOURNAMENT.id` (loose coupling; tournament sync may churn).

**Patterns to follow:**
- `src/main/resources/db/migration/V24__telemetry.sql` — engine/charset/collation style.
- `src/main/resources/db/migration/V22__match_video.sql` — nullable match-linking columns.

**Test scenarios:**
- Test expectation: none — pure DDL migration. Verified by Flyway on test container startup and subsequent repository tests.

**Verification:**
- `./gradlew test` boots with the new migration applied.

---

- [ ] **Unit 3: Entity + repository updates**

**Goal:** Extend `TelemetrySession` with the new fields and add repository finders for match-keyed lookup.

**Requirements:** R2, R4

**Dependencies:** Unit 2

**Files:**
- Modify: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetrySession.java`
- Modify: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetrySessionRepository.java`
- Modify: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryService.java` (update `new TelemetrySession(...)` call sites)
- Test: `src/test/java/ca/team1310/ravenbrain/telemetry/TelemetrySessionRepositoryTest.java`

**Approach:**
- Add `@Nullable` `@MappedProperty` fields to `TelemetrySession`: `tournamentId`, `matchLabel`, `matchLevel`, `matchNumber`, `playoffRound`, `fmsEventName`.
- Repository finders: `findAllByTournamentIdAndMatchLevelAndMatchNumber`, `findAllByTournamentIdOrderByStartedAtAsc`.
- Update the existing `createSession` call-site to pass nulls for the new fields.

**Patterns to follow:**
- Entity style: existing `TelemetrySession` record.
- Repository finder style: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetrySessionRepository.java`.

**Test scenarios:**
- Happy path: save + load `TelemetrySession` with all new fields populated preserves them.
- Happy path: save + load with all new fields null still works.
- Happy path: `findAllByTournamentIdAndMatchLevelAndMatchNumber` returns every session for that match.
- Edge case: multiple sessions for the same match (two robots from team 1310 on the same match) are both returned.

**Verification:**
- Repository tests pass against the Testcontainers MySQL.

---

- [ ] **Unit 4: Ingest-time enrichment**

**Goal:** Extend `TelemetryService.bulkInsertEntries` to scan for FMSInfo keys and update the session's match identity atomically — only when the session's identity is still null.

**Requirements:** R1, R2, R3, R5

**Dependencies:** Units 1, 2, 3

**Files:**
- Modify: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryService.java`
- Create: `src/main/java/ca/team1310/ravenbrain/telemetry/MatchIdentityEnricher.java` (helper extracted from the service for isolated testing)
- Modify: `src/main/java/ca/team1310/ravenbrain/tournament/WatchedTournamentService.java` — add `Optional<TournamentRecord> findByCodeAndSeason(String code, int season)` (or equivalent) if not already present.
- Test: `src/test/java/ca/team1310/ravenbrain/telemetry/MatchIdentityEnricherTest.java`
- Test: `src/test/java/ca/team1310/ravenbrain/telemetry/TelemetryServiceEnrichmentTest.java`

**Approach:**
- After inserting entries in the same JDBC transaction, check session's current `match_label`.
- If already non-null → skip enrichment (first-write-wins). Log at DEBUG if a different FMSInfo value appears.
- If null → scan entries for the earliest non-empty `/FMSInfo/MatchNumber` and the latest `/FMSInfo/EventName`.
- Resolve tournament: try `fmsEventName` via `WatchedTournamentService.findByCodeAndSeason` (season derived from session `started_at`); fall back to the watched tournament whose time window contains `session.started_at`.
- Issue a single `UPDATE RB_TELEMETRY_SESSION SET ... WHERE id = ?` within the existing transaction.
- Enrichment errors must not lose entry data: wrap the enrichment block in try/catch, commit the entries, log the enrichment error.

**Patterns to follow:**
- Existing raw-JDBC style in `TelemetryService.bulkInsertEntries`.

**Test scenarios:**
- Happy path: batch contains a single `/FMSInfo/MatchNumber = "Q14"` and session was previously bare → session updated to Qualification/14, `match_label = "Q14"`.
- Happy path: batch contains `/FMSInfo/EventName = "ONWAT"` and a watched tournament exists with code ONWAT for the session's season → `tournament_id` populated.
- Happy path: batch with no FMSInfo keys → session unchanged.
- Edge case: session already has `match_label` set → enrichment is skipped; later batch with a *different* FMS value is ignored (logged but not written).
- Edge case: `/FMSInfo/MatchNumber = ""` is ignored.
- Edge case: `/FMSInfo/MatchNumber = "X99"` (parser returns null structured fields) → `match_label` stored as `"X99"`, level/number null.
- Error path: tournament lookup finds nothing → `tournament_id` null; match identity still populates.
- Error path: enrichment throws → entries are still committed; error is logged.
- Integration: the FMS key appears in a large batch mixed with hundreds of non-FMS entries — enrichment still runs correctly.

**Verification:**
- All enrichment tests pass.
- Existing `TelemetryApi` tests still pass.

---

- [ ] **Unit 5: REST surface for match-keyed telemetry**

**Goal:** Let clients list sessions by `(tournamentId, matchLevel, matchNumber)`, and return enriched session payloads.

**Requirements:** R4

**Dependencies:** Units 3, 4

**Files:**
- Modify: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryApi.java`
- Modify: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryService.java` (add read-side method `findSessionsForMatch`)
- Test: `src/test/java/ca/team1310/ravenbrain/telemetry/TelemetryApiMatchLookupTest.java`

**Approach:**
- Add `GET /api/telemetry/match/{tournamentId}/{matchLevel}/{matchNumber}` returning `List<TelemetrySession>`.
- The existing `GET /api/telemetry/session/{sessionId}` now returns the enriched fields for free via the entity change in Unit 3.
- Mirror role requirements from existing read endpoints: `ROLE_TELEMETRY_USER`, `ROLE_SUPERUSER`.

**Patterns to follow:**
- URL shape from `src/main/java/ca/team1310/ravenbrain/matchstrategy/MatchStrategyApi.java:81` (`/{tournamentId}/{matchLevel}/{matchNumber}`).

**Test scenarios:**
- Happy path: single enriched session for Q14 is returned when querying `(tournamentId, Qualification, 14)`.
- Happy path: multiple sessions (two robots, same match) are both returned.
- Edge case: no matching telemetry → 200 with empty array.
- Error path: unauthorized role returns 403.
- Integration: enriched session from Unit 4 is retrievable via `GET /session/{id}` with all new fields populated.

**Verification:**
- `./gradlew test --tests "*TelemetryApiMatchLookupTest*"` passes.

---

- [ ] **Unit 6: Backfill existing sessions**

**Goal:** One-time routine that populates match identity for sessions where `match_label IS NULL` using entries already in `RB_TELEMETRY_ENTRY`.

**Requirements:** R6

**Dependencies:** Unit 4 (reuses the enricher)

**Files:**
- Create: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryBackfillService.java`
- Modify: `src/main/java/ca/team1310/ravenbrain/telemetry/TelemetryApi.java` (add superuser-only `POST /api/telemetry/backfill-match-identity`)
- Test: `src/test/java/ca/team1310/ravenbrain/telemetry/TelemetryBackfillServiceTest.java`

**Approach:**
- Page over candidate sessions (ordered by `id`) where `match_label IS NULL`.
- For each session, read its FMSInfo-relevant entries from `RB_TELEMETRY_ENTRY` and run the same enricher used at ingest.
- Idempotent: sessions that already have identity are skipped unless `force=true` query param is set.
- Guard: `ROLE_SUPERUSER` only.
- Return a small summary payload `{ examined, updated, skipped }`.

**Patterns to follow:**
- Raw-JDBC streaming pattern in `src/main/java/ca/team1310/ravenbrain/sync/ConfigSyncService.java`.

**Test scenarios:**
- Happy path: one pre-existing session with a `/FMSInfo/MatchNumber = "Q10"` entry is enriched after backfill runs.
- Edge case: session with no FMSInfo entries is left untouched.
- Edge case: rerunning backfill without `force=true` on an already-enriched session is a no-op.
- Error path: non-superuser gets 403.
- Integration: session with thousands of entries completes without OOM — streamed reads, not a single `SELECT *`.

**Verification:**
- Backfill endpoint returns a count of sessions updated.

## System-Wide Impact

- **Interaction graph:** `TelemetryService.bulkInsertEntries` now performs a conditional `UPDATE` on the session row in the same transaction. Downstream: match-strategy / schedule reporting can start joining telemetry.
- **Error propagation:** Enrichment failure must not lose entry data — wrap enrichment in try/catch, commit entries, log the error.
- **State lifecycle risks:** None new. Because one session = one match and first-write-wins, there's no open-interval bookkeeping.
- **API surface parity:** New `/api/telemetry/match/...` mirrors the match-strategy endpoint shape so clients reuse helpers.
- **Integration coverage:** Unit 4 integration tests must use real MySQL (Testcontainers) to prove the raw-JDBC session UPDATE interleaves correctly with entry inserts.
- **Unchanged invariants:** Existing telemetry session creation / entry posting / completion endpoints keep their current signatures and semantics. Agents that never send FMSInfo keep working identically.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Robot publishes `/FMSInfo/MatchNumber` formats we didn't anticipate | Defensive parser (Unit 1) keeps raw `match_label`; structured fields null when unrecognized; backfill can re-run after parser improvements |
| Invariant "one session = one match" is violated in practice (bug, test mode, etc.) | First-write-wins keeps the primary identity stable; conflicting values logged at WARN so we can detect and investigate |
| Tournament resolution wrong when teams aren't watching the event | Accept `tournament_id` null; allow manual repair via future endpoint; don't block ingest |
| Enrichment adds write amplification per batch | Only one UPDATE per batch, and only when identity is still null — negligible |

## Documentation / Operational Notes

- Update `doc/architecture.md` with a short telemetry-identification section once Unit 4 lands.
- Release via conventional commits — this lands as a `feat:` bump.
- No rollout flag needed; enrichment is purely additive. If something goes wrong, a follow-up migration can null the columns out; no data destruction risk.

## Sources & References

- Related code:
  - `src/main/java/ca/team1310/ravenbrain/telemetry/*`
  - `src/main/java/ca/team1310/ravenbrain/schedule/ScheduleRecord.java`
  - `src/main/java/ca/team1310/ravenbrain/matchstrategy/MatchStrategyApi.java`
  - `src/main/resources/db/migration/V24__telemetry.sql`
- Related PRs/issues: recent `feat/field-map-calibration` merge (PR #75) as schema+REST pattern reference.
