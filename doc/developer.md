# Raven Brain - Running the Application on a Developer System

## Running the Application

There are three ways to run RavenBrain locally:

1. **Docker** - Recommended for front-end developers working on RavenEye who just need a running backend
2. **Native** - Required for developers actively working on RavenBrain code

## Option 1: Running via Docker (Front-End Developers)

This option runs both RavenBrain and MySQL in Docker containers. You do not need to install Java or MySQL on your
machine.

### Prerequisites

- Docker Desktop installed and running

### Setup

1. Download RavenEye source code to your local machine
2. Follow the **Developer** instructions. They will guide you in starting RavenBrain on your local computer.

#### Notes

- If you want to completely reset your instance to the default (mostly empty) database, stop docker and delete the
  ravenbrain and mysql containers.

## Option 2: Running Natively (RavenBrain Developers)

To run the app natively you have two choices - in IntelliJ using the Micronaut plugin, or via Gradle.

First, be sure to complete the [RavenBrain setup instructions](setup.md).

### Via IntelliJ

The `RavenBrain Local` run configuration should be available in your project. You can click on the `RavenBrain Local`
menu option from the play menu in the top-right of the IDE window.

If you do not have it there, follow these instructions to add it:

1. open up the dropdown menu and select `Edit Configuration`.
2. Under the `Micronaut` panel, select `µ Application`.
3. Change the name from `Application` to `RavenBrain Local`.
4. From the `Build and Run` section, click on `Modify Options` and check `Add VM Options`.
5. Close this popup.
6. A new `VM Options` field appears. Add this to it: `-Dmicronaut.environments=local`.
7. Click on `Apply` and then `Ok` to close the configuration edit screen.
8. The `RavenBrain Local` option should now appear.

Via gradle, execute the following, which sets the variable and runs the app.

#### Mac:

```bash
MICRONAUT_ENVIRONMENTS=local gradle run
```

#### Windows:

```bash
set MICRONAUT_ENVIRONMENTS=local
.\gradlew run
```

---


Allow public and private networks to access `Zulu Platform x64 Architecture`.

When RavenBrain starts, you should see the following at the top of the log, after the banner:

```log
16:14:57.664 [main] INFO  i.m.c.DefaultApplicationContext$RuntimeConfiguredEnvironment - Established active environments: [local]
```
