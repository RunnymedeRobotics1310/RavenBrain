# Raven Brain

Back-end datastore for [Team 1310 Raven Eye](https://github.com/runnymederobotics1310/raveneye)

Created March 22, 2025, North Bay, Ontario, Canada by Tony Field, Team 1310, Runnymede Robotics
Last Updated December 25, 2025 by Tony Field

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

Docker images are automatically published to GitHub Container Registry on every push to `main`:

```bash
docker pull ghcr.io/runnymederobotics1310/ravenbrain:latest
```

## Table of Contents

- [Developer Environment Setup Instructions](doc/setup.md)
- [Developer Programming Instructions](doc/setup.md)
- [Server setup Instructions](doc/server.md)
- [Server Architecture](doc/architecture.md)

Happy strategizing!
