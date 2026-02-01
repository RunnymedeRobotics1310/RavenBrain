# Raven Brain

Back-end datastore for [Team 1310 Raven Eye](https://github.com/runnymederobotics1310/raveneye)

Created March 22, 2025, North Bay, Ontario, Canada by Tony Field, Team 1310, Runnymede Robotics\
Last Updated January 31, 2026 by Tony Field

## Overview

- RavenBrain is a REST service backed with MySQL database
- It uses the Micronaut Framework - initially 4.7.6
- Original designs were intended to run on a battery-powered Raspberry Pi in the stands at competitions
- Later, the app was updated to be hosted on a home server of a team mentor.
- The app synchronizes data automatically from the FRC API on a regular schedule. This includes season, schedule, and
  match data.
- The app expects infrequent synchronization of the front-end and back-end systems, as internet access for scouts at
  tournaments is unreliable.

## Docker Image

Docker images are automatically published to GitHub Container Registry on every release:

```bash
docker pull ghcr.io/runnymederobotics1310/ravenbrain:latest
```

## Releases

This project uses [Conventional Commits](https://www.conventionalcommits.org/) for automatic versioning and changelog
generation. Every push to `main` with conventional commit messages triggers an automatic release.
See [CHANGELOG.md](CHANGELOG.md) for release history.

Deployment instructions are available
in [RavenEye Docs](https://github.com/RunnymedeRobotics1310/RavenEye/blob/main/DEPLOYMENT.md)

## Table of Contents

- [Developer Setup Instructions](doc/setup.md)
- [Application Architecture](doc/architecture.md)

Happy strategizing!
