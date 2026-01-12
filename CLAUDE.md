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
- `frcapi` - FRC API integration with caching client and sync services
  - `fetch` - HTTP client with response caching (`RB_FRC_RESPONSES` table)
  - `service` - Data sync orchestration (`FrcClientService`, `EventSyncService`)
  - `model` - API response models (year-specific models in `model.year2025`)
- `quickcomment` - Team comments from scouts
- `report` - Reporting services for team/tournament analysis
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

Local development requires `src/main/resources/application-local.properties`:
```properties
datasources.default.username=rb
datasources.default.password=rb
raven-eye.frc-api.user=<your-frc-api-user>
raven-eye.frc-api.key=<your-frc-api-key>
raven-eye.role-passwords.superuser=<superuser-password>
```

Tests require similar config in `src/test/resources/application-test.properties`.

## Docker Deployment

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
