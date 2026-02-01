# Raven Brain Developer Setup Instructions

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

### Install Java

Java 25 is required

#### Mac Users

- Install https://sdkman.io
- Use `sdk list java` to see the available versions
- Install the required version (Azul 25 JDK is fine)

e.g.

```bash
sdk install java 25.0.1-zulu
```

#### Windows Users

- Download the OpenJDK distribution from Azul Systems for JDK 25 for windows from here:
  https://www.azul.com/downloads/?package=jdk#zulu
- Run the installer and choose default options

### Install JetBrains' IntelliJ IDEA Ultimate

The Ultimate version of IntelliJ IDEA is a commercial product but it is available for free to full-time students.

Request the student license by following the instructions on this page: https://www.jetbrains.com/academy/student-pack/
and then download IntelliJ IDEA Ultimate for your computer.

### Install MySQL Database

- Install MySQL Community Server 8.4 LTS from https://dev.mysql.com/downloads/mysql/
- Save your `root` username and password in a safe place. You will need it throughout these installation instructions.
- Throughout the setup instructions, you will need to specify a username and password. Very frequently, `-u <username>`
  will allow you to specify the username, and `-p` will tell the system to prompt you for your password (it's not a good
  idea to specify the password on the command line, so you are prompted for it instead.)

#### Mac Users

- Add `/usr/local/mysql/bin` to your `PATH` environment variable
- Load mac os time zone info into mysql
- Set the global time zone in mysql to UTC

```bash
/usr/local/mysql/bin/mysql_tzinfo_to_sql /usr/share/zoneinfo | mysql -u root -p mysql
mysql -uroot -p -e "SET GLOBAL time_zone = UTC;"
```

#### Windows Users

- COMING SOON...
- ...but basically, as long as you have THE CORRECT VERSION and follow the default instructions, you should be fine.

### Create the main user and the database, and load the initial DB data

```bash
mysqladmin -u root -p create ravenbrain
mysql -uroot -p mysql -e  "CREATE USER 'rb'@'localhost' IDENTIFIED BY 'rb'"
mysql -uroot -p mysql -e  "GRANT ALL ON ravenbrain.* TO 'rb'@'localhost'"
mysql --database=ravenbrain --user=root -p --host=127.0.0.1 --port=3306 < "ravenbrain-dumps/ravenbrain-2025-04-05-dump.sql"
```

- Micronaut/Flyway will automatically generate schema updates as necessary.

### Recreating the database

Use this if the DB becomes corrupted or a new DB dump is available.

- drop the database
- re-create it
- re-import
- re-start RaveBrain (Flyway (the database schema upgrade manager) will update tables again)

```bash
mysqladmin -u root -p drop ravenbrain
mysqladmin -u root -p create ravenbrain
mysql --database=ravenbrain --user=root -p --host=127.0.0.1 --port=3306 < "ravenbrain-dumps/ravenbrain-2025-04-05-dump.sql"
```

### Exporting the database

Use this to dump the database from localhost:

```bash
mysqldump --compress -u root -p ravenbrain > ../ravenbrain-dumps/ravenbrain-local-$(date +"%Y%m%dT%I%M%S").sql
```

### Configuring the Application

Define a file called `application-local.properties` and place it in your `src/main/resources` folder, and add the
following properties:

```properties
datasources.default.username=rb
datasources.default.password=rb
raven-eye.frc-api.user=frc_api_user_id
raven-eye.frc-api.key=frc_api_user_key
raven-eye.role-passwords.superuser=actual_superuser_password_1310
```

You will need to substitute the placeholder keys and secrets with the appropriate values.

| **Parameter**                      | **Description**                                                                                                                                                                                   |
|------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| datasources.default.username       | The username used when creating the database. This was probably `rb`                                                                                                                              |
| datasources.default.password       | The password used when creating the database. Probagbly `rb` for developer systems, but different for the production system.                                                                      |
| raven-eye.frc-api.user             | The userid you used when you created a FRC API key on the FRC API website. You have to do this yourself - the key is not shared                                                                   |
| raven-eye.frc-api.key              | The key given you to you by the FRC API site when you registered. This is not shared - you need to sign up yourself.                                                                              |
| raven-eye.role-passwords.superuser | This is where you DEFINE the superuser password for the system. A superuser has the ability to create admins (and other roles). Never share this password. If blank, no superuser will be active. |

Next, create a second file, this time called `application-test.properties` (i.e. with `-test` instead of `-local`), and
place it in your
`src/test/resources` folder (**NOT** the `src/main/resources` folder), and include the same contents. These
configuration overrides are overlaid on top of the
properties in `application.yml`.

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

### Code Formatting

This project uses the Google Formatter for code formatting.

### Installation

In IntelliJ IDEA:

1. From the gear icon in the top right of IntelliJ IDEA (with the RavenBrain project open), select `Plugins`.
2. Plugins should be selected in the poppup window's sidebar
3. In the search box on the right section of the popup window, enter `google-java-format`. If it appears in the
   `Installed` tab, ensure that it is not disabled
4. If it doesn't show up under the `Installed` tab, click on the `Marketplace` tab
4. Click on install beside the plugin.
5. Ensure that it is enabled.
6. Exit and Restart IntelliJ IDEA.

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
