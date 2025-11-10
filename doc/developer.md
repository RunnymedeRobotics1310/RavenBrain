# Raven Brain - Developer Instructions

## Setup

- install Java
- install MySQL Database
- create database using the following (coming soon)
- install this app
- configure it to start on boot
- deploy it to raspberry pi.

## Java

Java 21 is required

### Mac Users

- Install https://sdkman.io
- Use `sdk list java` to see the available versions
- Install the required version (Azul 21 JDK is fine)

e.g.

```bash

sdk install java 21.0.8-zulu 
```

### Windows Users

- Download the OpenJDK distribution from Azul Systems for JDK 21 for windows from here:
  https://www.azul.com/downloads/?package=jdk#zulu
- Run the installer and choose default options

## DB Setup

- Install MySQL Community Server 8.4 LTS from https://dev.mysql.com/downloads/mysql/

### Mac Users

- Add `/usr/local/mysql/bin` to your `PATH` environment variable
- Load mac os time zone info into mysql
- Set the global time zone in mysql to UTC

```bash
/usr/local/mysql/bin/mysql_tzinfo_to_sql /usr/share/zoneinfo | mysql -u root -p mysql
mysql -uroot -p -e "SET GLOBAL time_zone = UTC;"
```

### Windows Users

- COMING SOON

## Create database

```bash
mysqladmin -u root -p create ravenbrain 
mysql -uroot -p mysql -e  "CREATE USER 'rb'@'localhost' IDENTIFIED BY 'rb'" 
mysql -uroot -p mysql -e  "GRANT ALL ON ravenbrain.* TO 'rb'@'localhost'"
mysql --database=ravenbrain --user=root -p --host=127.0.0.1 --port=3306 < "rbdb-dump.sql"
```

- Micronaut/Flyway will automatically generate schema updates as necessary.

### Recreating the database

Use this if the DB becomes corrupted or a new DB dump is available.

- drop the database
- re-create it
- re-import
- re-start RaveBrain (Flyway will update tables again)

```bash
mysqladmin -u root -p drop ravenbrain 
mysqladmin -u root -p create ravenbrain
mysql --database=ravenbrain --user=root -p --host=127.0.0.1 --port=3306 < ravenbrain-dump.sql
```

### Exporting the database

Use this to dump the database from localhost:

```bash
mysqldump --compress -u root -p rbdb > ../ravenbrain-dumps/ravenbrain-local-$(date +"%Y%m%dT%I%M%S").sql
```

## Application Configuration

Define a file called `application-local.properties` and place it in your `src/main/resources` folder, and add the
following properties:

```properties
# SMTP Settings
datasources.default.username=rb
datasources.default.password=rb
raven-eye.frc-api.user=tfield
raven-eye.frc-api.key=abc123
raven-eye.role-passwords.admin=actual_admin_password_123
raven-eye.role-passwords.expertscout=actual_expert_scout_password_876
raven-eye.role-passwords.datascout=actual_data_scout_password__1
raven-eye.role-passwords.member=actual_team_shared_secret_team1310IsTheBest
```

If you want to change any of the logging levels, add the appropriate logger property to the above file. Valid levels are
`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, and `FATAL`. These can be left out if not used, and you can add more detailed
loggers if desired as well.

```properties
# Logger settings
logger.levels.ca.team1310.ravenbrain.frcapi.FrcService=DEBUG
logger.levels.ca.team1310.ravenbrain.frcapi.FrcClient=INFO
logger.levels.io.micronaut.context.condition=INFO
logger.levels.io.micronaut.data.query=INFO
logger.levels.io.micronaut.http=INFO
logger.levels.io.micronaut.security=INFO
logger.levels.io.micronaut.server=INFO
logger.levels.io.micronaut.views=INFO
```

Finally, to run the app locally you have two choices - natively in IntelliJ using the Micronaut plugin, or via Gradle:

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

```bash
MICRONAUT_ENVIRONMENTS=local gradle run
```

When RavenBrain starts, you should see the following at the top of the log, after the banner:

```log
16:14:57.664 [main] INFO  i.m.c.DefaultApplicationContext$RuntimeConfiguredEnvironment - Established active environments: [local]
```

Happy coding!
