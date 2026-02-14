# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RavenBrain is a REST backend service for Team 1310's scouting system (RavenEye). It uses the Micronaut Framework with MySQL, synchronizes data from the FRC API, and supports offline-first operation for unreliable tournament connectivity.

## Build & Run Commands

```bash
# Build the project
./gradlew build

# Run locally (requires local MySQL and application-local.properties)
MICRONAUT_ENVIRONMENTS=local ./gradlew run

# Run tests (uses Micronaut Test Resources for auto-provisioned MySQL)
./gradlew test

# Run a single test class
./gradlew test --tests "ca.team1310.ravenbrain.connect.UserApiTest"

# Run a single test method
./gradlew test --tests "ca.team1310.ravenbrain.connect.UserApiTest.testUserCreation"
```

## Java Version

Requires Java 25 (configured in `.sdkmanrc` and `build.gradle`). Use SDKMAN to install: `sdk install java 25.0.1-zulu`

## Architecture

### Tech Stack
- **Framework**: Micronaut 4.x in servlet mode (not reactive) with Tomcat
- **Database**: MySQL 8.4 LTS with Micronaut Data JDBC and Flyway migrations
- **Security**: JWT-based auth via Micronaut Security
- **Serialization**: Micronaut Serde with Jackson

### Package Structure (`ca.team1310.ravenbrain`)
- `connect` - Authentication, user management, JWT token generation
- `eventlog` - Scout event logging API (`RB_EVENT` table)
- `eventtype` - Event type definitions for scouting events
- `frcapi` - FRC API integration with caching client and sync services
  - `fetch` - HTTP client with response caching (`RB_FRC_RESPONSES` table)
  - `service` - Data sync orchestration (`FrcClientService`, `EventSyncService`)
  - `model` - API response models (year-specific models in `model.year2025`)
- `quickcomment` - Team comments from scouts
- `report` - Reporting services for team/tournament analysis (includes `seq` subpackage for sequence reports)
- `schedule` - Match schedule management
- `sequencetype` - Event sequence definitions for timed event analysis
- `strategyarea` - Strategy areas configuration
- `tournament` - Tournament management

### Key Patterns
- Java records for database entities (DAOs) and JSON marshalling
- POJOs for business objects requiring mutation
- Auto-generated keys use `@Generated` annotation on record fields
- Database migrations in `src/main/resources/db/migration/V*.sql`

## Configuration

Datasource connection properties (URL, username, password) are **not** defined in `application.yml`. This allows Micronaut Test Resources to auto-provision a MySQL container via Testcontainers during tests. Connection properties must be provided externally for local and production environments.

For local development, create `src/main/resources/application-local.properties`:
```properties
datasources.default.url=jdbc:mysql://localhost:3306/ravenbrain?enabledTLSProtocols=TLSv1.2&useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC
datasources.default.username=rb
datasources.default.password=rb
raven-eye.frc-api.user=<your-frc-api-user>
raven-eye.frc-api.key=<your-frc-api-key>
```

For production/Docker, configure via environment variables: `DATASOURCES_DEFAULT_URL`, `DATASOURCES_DEFAULT_USERNAME`, `DATASOURCES_DEFAULT_PASSWORD`, `FRC_USER`, `FRC_KEY`, `JWT_GENERATOR_SIGNATURE_SECRET`, `SUPERUSER_PASSWORD`, `REGISTRATION_SECRET`.

Tests use Micronaut Test Resources, which auto-provisions a MySQL container via Testcontainers — no datasource config is needed in `src/test/resources/application-test.properties`. Docker Desktop must be running to execute tests.

## Docker Deployment

### CI/CD (GitHub Actions)

**Automatic Releases:** When commits with conventional commit messages (`feat:`, `fix:`) are pushed to `main`, the release workflow (`.github/workflows/release.yml`):
1. Analyzes commit messages to determine version bump (major/minor/patch)
2. Creates a git tag (e.g., `v2.1.0`)
3. Creates a GitHub Release with auto-generated changelog
4. Builds and pushes Docker image to GHCR

**Docker Images:** Built and pushed to GitHub Container Registry on every release:
- `ghcr.io/runnymederobotics1310/ravenbrain:latest`
- `ghcr.io/runnymederobotics1310/ravenbrain:<version>` (e.g., `2.1.0`)
- `ghcr.io/runnymederobotics1310/ravenbrain:<commit-sha>`
- `ghcr.io/runnymederobotics1310/ravenbrain:v<version>` (release tag, e.g., `v2.1.0`)

To pull the latest image:
```bash
docker pull ghcr.io/runnymederobotics1310/ravenbrain:latest
```

### Local Docker Build

```bash
# Build Docker image and start containers (MySQL + app)
./gradlew deployDocker

# Stop containers
./gradlew stopDocker

# Production deployment (uses external volume mount)
MYSQL_DATA_PATH=/mnt/external/ravenbrain ./gradlew deployDockerProd
```

Copy `.env.example` to `.env` and configure secrets before deploying. The app container connects to MySQL via Docker networking using hostname `mysql`.

## Code Style

Uses Google Java Format. Install the `google-java-format` IntelliJ plugin.

## Conventional Commits

This project uses [Conventional Commits](https://www.conventionalcommits.org/) for automatic versioning and release notes. Commit messages must follow this format:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types that trigger version bumps:**
- `fix:` - Bug fix (patch version bump, e.g., 2.0.0 → 2.0.1)
- `feat:` - New feature (minor version bump, e.g., 2.0.0 → 2.1.0)
- `feat!:` or `BREAKING CHANGE:` - Breaking change (major version bump, e.g., 2.0.0 → 3.0.0)

**Other types (no version bump):**
- `docs:` - Documentation only
- `chore:` - Maintenance tasks
- `refactor:` - Code refactoring
- `test:` - Adding/updating tests
- `ci:` - CI/CD changes

**Examples:**
```
feat: add team performance comparison report
fix: correct score calculation for playoff matches
feat(frcapi): add support for 2026 game data
fix!: change event timestamp format to ISO 8601
```

## API Design

The server is designed for bulk synchronization rather than chatty protocols (due to unreliable tournament connectivity). Key endpoints include:
- `/api/ping` - Health check
- `/api/validate` - Basic auth to JWT exchange
- `/api/event` - Batch event submission
- `/api/tournament`, `/api/schedule`, `/api/quickcomment`, `/api/report/*` - CRUD and reporting endpoints
