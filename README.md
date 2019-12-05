# Camunda Optimize

Camunda Optimize is an extension to Camunda BPM for enterprise customers,
that provides continuous monitoring and insights about your deployed
business processes.

* [Issue Tracker](https://app.camunda.com/jira/secure/RapidBoard.jspa?rapidView=49)
* [Documentation](https://docs.camunda.org/optimize/)

## Building Optimize

Apache Maven 3 and Java JDK 8 are prerequisites for building Camunda
Optimize. Once you have setup Java and Maven, run

```
mvn clean install
```

And if you don't want to run the tests
```
mvn clean install -DskipTests
```

**On Windows** make sure you run this in a command line as Administrator
- as some npm modules need to be able to create symlinks.

After the build is completed, you will find the distributions under ```
distro/target ```

## Front-end

Learn how to build, debug, test and more in the front-end in the [Front-End README](client/README.md)

## Back-end

Learn how to build, debug, test and more in the back-end in the [Back-End README](backend/README.md)

## Development Setup

### Docker

You can start the platform with H2 by issuing:
```
docker-compose up -d
```

If you need a postgres backend, you can use a different docker-compose
file. In this case the command is: ``` docker-compose -f
docker-compose.postgresql.yml up -d ```

This will start Elasticsearch and a PostgreSQL-backed Camunda BPM instance.
The services are exposed to localhost on the following ports:
- Elasticsearch: 9200, 9300
- PostgreSQL: 5432
- CamBPM: 8080

Services can be shutdown again using
```
docker-compose down
```

## F.A.Q

# Docker is telling me that I do not have the rights to pull the images

You probably did not login in Camunda's private Docker registry. Since we
are using enterprise images that are available only to paying customer it
is necessary to authenticate with:

```
docker login registry.camunda.cloud
```

You can use your LDAP credentials to continue the authentication.

# Supporting multiple versions of Cambpm

Optimize supports multiple versions of CamBpm. It is possible to use these
versions by specifying different maven profiles. In particular you can
have a look at the root pom.xml files. It contains a profile for every
version that Optimize supports (e.s. engine-latest, engine-7.11).

## Running integration tests locally

You can run locally integration tests using maven. The command to run the
tests is the following:

```
mvn -Pit,engine-latest -pl backend -am clean install
```

You can replace the profile `engine-latest` with the version of Cambpm you
want to test against.

## Modifying CamBpm version being tested

Whenever there is a new release of the platform, we need to update the
`pom.xml` file, in order to test against the latest codebase.

The version that each profile is pulling is stored in the
`camunda.engine.version` property of the corresponding profile. Just
update this version with the updated version.
