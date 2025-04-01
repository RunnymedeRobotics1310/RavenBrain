# Raven Brain

Back-end datastore for [Team 1310 Raven Eye](https://runnymederobotics1310.github.io/2025-data-scouting-app/)

Created March 22, 2025, North Bay, Ontario, Canada by Tony Field, Team 1310, Runnymede Robotics

## Overview

- rest service backed with mysql database
- uses micronaut framework - initially 4.7.6
- intended to run on a battery-powered raspberry pi in the stands at competitions

## Setup

- install mysql
- create database using the following (coming soon)
- install this app
- configure it to start on boot
- deploy it to raspberry pi.

## Database setup

```shell
mysqladmin -u root -p create ravenbrain 
mysql -uroot -p mysql -e  "CREATE USER 'rb'@'localhost' IDENTIFIED BY 'rb'" 
mysql -uroot -p mysql -e  "GRANT ALL ON ravenbrain.* TO 'rb'@'localhost'"
```

## Micronaut 4.7.6 Documentation

- [User Guide](https://docs.micronaut.io/4.7.6/guide/index.html)
- [API Reference](https://docs.micronaut.io/4.7.6/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/4.7.6/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)

---
