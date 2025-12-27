# Raven Brain Architecture

## Overview

This is a back-end REST server. It is written in *Java* and uses the *Micronaut Framework*.
*Micronaut* provides multiple basic services including http request/response handling,
security services, and a database abstraction layer (as well as other services not
actively used by this project). *Micronaut* is configured to work in ***servlet*** mode,
as it is a much simpler programming model than the default ***reactive*** mode, and
servlet performance with virtual threads is outstanding.

## System Architecture

### Database Server and Abstraction

This site uses *Micronaut Data* for as a database abstraction layer. It provides
basic `CRUD` (create/read/update/delete) functionality but provides direct access
to raw `SQL` (structured query language) so that specific queries can be written
against the database when needed.

The database uses the ***default*** datasource, so configuration properties in
`application.yml` are easy to work with.

The database server chosen is *MySQL Community Edition 8.4 LTS*, which is free,
open-source and stable. It can be installed directly onto a Mac, Linux machine,
or Windows machine. It can also be run in docker, but configuration details are
not included in this documentation at this time.

### Java and Micronaut Version

This project requires *Java 21* or later.

This project was originally written for *Micronaut Platform 4.7* but is intended to
be kept up-to-date with the latest version of *Micronaut Platform 4* for the foreseeable
future.

### REST Server

Raven Brain is a `REST` server. It receives requests via `HTTP` calls using `JSON` as the
payload (when a payload is appropriate) and responds with `JSON` (when a response
payload is appropriate).

The Raven Eye front-end system is the only expected front-end for this system
at this time. Raven Eye is a *React* client-side application (meaning no server
components or other back-end services are to be used besides this (Raven Brain)).

Furthermore, Raven Eye is designed to work when internet connectivity is extremely
poor (as is the case at many FIRST competitions at this time). Practically, this means
that the app needs to work - after a basic sync - without being able to connect to the
server during preliminary competitions. Internet connectivity during matches will
result in a better experience, but core functionality works.

As such, the communication between Raven Eye and Raven Brain is generally in the form
of occasional bulk synchronization as opposed to being a very chatty protocol.
The `REST` services offered by Raven Brain reflect this.

### Integrations

Besides connecting to the *MySQL* datababse, Raven Brain also connects to other
sources of game data. It is not a goal of Team 1310's strat team to be a series
of data scribes. When important game data is available from external sources with
a satisfactory degree of accuracy, this data will be used in conjunction with
scout-gathered data.

#### FRC API

The *FIRST FRC API* server provides data directly from the *FMS* (FIRST's
Field Management System) that tracks points and scoring in matches, rankings, and
more. Raven Brain will synchronize data as intelligently as it can to avoid excess
abuse of the API and to keep traffic to a minimum.

Once data has been synchronized with the *FRC API*, a series of processors run to
process the received data and save it into tables in the *MySQL* database that are
suitable for reporting.

#### Statbotics

Some time in the future, integration with the *StatBotics API* will be added to return even
more in-depth game data.

## Component Architecture

### Connection `ca.team1310.ravenbrain.connect` package

This package establishes a handshake with the front-end application. It reads
configuration properties (the `Config` class) and has only one main
REST service (`EstablishConnection`).

#### `/api/ping`

This service simply responds with a 200 status code when the server is alive.

#### `/api/validate`

This service looks for basic auth credentials, vaidates them, looks up their
authorization roles, and creates a `JWT` (JSON Web Token - a small bundle of information
that contains information about who the user is and what they have access to, all
cryptographically signed so that it cannot be tampered with. JWTs are standard
web security artifacts). The `JWT` is set into the HTTP response, so that the
client (i.e. Raven Eye) can save it for future use.

The `validate` API uses several built-in features from *Micronaut Security*. It
uses basic auth (username/password) validation to authenticate the user. It
then looks up the user's authorization roles and stores this information
in the `Http Servlet Specification`'s `Authentication` object. The `Authentication`
object is then converted into a `JWT` by *Micronaut Security* and sends it
back to the user, where it can be saved and sent to the server in the future, so
that the user does not have to keep sending the username and password back and
forth.

### Event Log `ca.team1310.ravenbrain.eventlog` package

This package contains the API that allows scouts to post events to the back-end.
It is a trivially simple API.

#### `/api/event`

Event data `POST`ed to this endpoint are stored in the event log table `RB_EVENT`.
The post endpoint expects an array of records. The response will provide a
message indicating the success or failure of the recording of each event.

### FRC API `ca.team1310.ravenbrain.frcapi` package

This package includes all of the functionality related to pulling data from the *FRC API*.

The package is organized into three sections

- `fetch` package - this tool is responsible for fetching data from the *FRC API*. It has a
  `FrcClient` class that wraps the https calls, and a `FrcCachingClient` that uses
  the `FrcClient` to retrieve data and stores the raw responses directly into a
  database table called `RB_FRC_RESPONSES`. This enables smart communication with
  the api while still ensuring that accurate data is returned when requested.
- `service` package - this includes the main `FrcClientService` class that exposes
  FRC data in a friendly and usable format. It retrieves it via the `FrcCachingClient`,
  and marshalls it into a common format that is easy to work with - completely isolated
  from the wire (`JSON`) format. It also contains an `EventSyncService` class, which
  manages scheduled synchronization operations with the FRC server, and also exposes
  manual sync operations with the FRC server.
- `model` package - this provides java objects that represent the content returned by
  the *FRC API*. Most are common to all years, but a series of sub-packages under the
  format `year2025` include objects that are year-specific. (recorded event formats
  vary for some API calls).

### Quick Comment `ca.team1310.ravenbrain.quickcomment` package

This package contains the API endpoint for submitting and reading quick comments by
team members. It allows members to add a comment about a team, and allows expert
scouts to read them.

#### `/api/quickcomment'

Two endpoints - one to `POST` new quick comments, and one to `GET` all of them.

### Report `ca.team1310.ravenbrain.report` package

MHL

### Schedule `ca.team1310.ravenbrain.schedule` package

MHL

### Tournament `ca.team1310.ravenbrain.tournament` package

MHL

# TO DO ITEMS

- force RavenBrain to be an SSL endpoint
- define user management packages
- look at requirements around eventlog - changes needed?
- finish the doc and fill in all of the MHL ("more here later" (i.e. coming soon)) sections
- note use of java records for dto and json and java objects for core business logic
