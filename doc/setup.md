# Raven Brain - Developer Setup Instructions

## Setup

- Install Java
- Install JetBrains' IntelliJ IDEA Ultimate
- Install MySQL Database
- Creating the Database
- Loading Data into the Database
- Configure the Application
- Code Formatting

## Install Java

Java 21 is required

#### Mac Users

- Install https://sdkman.io
- Use `sdk list java` to see the available versions
- Install the required version (Azul 21 JDK is fine)

e.g.

```bash
sdk install java 21.0.8-zulu 
```

#### Windows Users

- Download the OpenJDK distribution from Azul Systems for JDK 21 for windows from here:
  https://www.azul.com/downloads/?package=jdk#zulu
- Run the installer and choose default options

## Install JetBrains' IntelliJ IDEA Ultimate

The Ultimate version of IntelliJ IDEA is a commercial product but it is available for free to full-time students.

Request the student license by following the instructions on this page: https://www.jetbrains.com/academy/student-pack/
and then download IntelliJ IDEA Ultimate for your computer.

## Install MySQL Database

- Install MySQL Community Server 8.4 LTS from https://dev.mysql.com/downloads/mysql/

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
- be sure to define the `root` user password. The `-p` prompt in the code snippets below will trigger the app to prompt
  you for a password - and it's the `root` user password that you will have to provide. DO NOT lose that password!

### Create the database

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
- re-start RaveBrain (Flyway will update tables again)

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

## Configuring the Application

Define a file called `application-local.properties` and place it in your `src/main/resources` folder, and add the
following properties:

```properties
# SMTP Settings
datasources.default.username=rb
datasources.default.password=rb
raven-eye.frc-api.user=frc_api_user_id
raven-eye.frc-api.key=frc_api_user_key
raven-eye.role-passwords.admin=actual_admin_password_123
raven-eye.role-passwords.expertscout=actual_expert_scout_password_876
raven-eye.role-passwords.datascout=actual_data_scout_password__1
raven-eye.role-passwords.member=actual_team_shared_secret_team1310IsTheBest
```

You will need to substitute the placeholder keys and secrets with the appropriate values. Speak with the mentor to learn
how to get these details.

Next, create a second file, this time called `application.properties` (i.e. without the `-local`), and place it in your
`src/test/resources` folder, and include the same contents. These configuration overrides are overlaid on top of the
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

## Code Formatting

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

