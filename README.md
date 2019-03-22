# Camunda Operate

## Documentation

* [Backend Documentation](./backend)
* [Frontend Documentation](./client)
* [Contribution Guidelines](/CONTRIBUTING.md)
* [Issue Tracker](https://app.camunda.com/jira/secure/RapidBoard.jspa?rapidView=61)
* [Zeebe](https://zeebe.io)

## Running locally

To run the application locally you can use `docker`, `docker-compose` and
`make`. Make sure to have a recent version of these tools installed
locally: you should be able to run these commands on your shell without
`sudo`.

Windows users need to install `make` manually on their shell. You can find
instructions on how to do it
[here](https://gist.github.com/evanwill/0207876c3243bbb6863e65ec5dc3f058#make).

If you need support to configure these tools please contact Andrea or
Christian.

To spawn the full local environment, run this command in the root folder:

```
make env-up
```

The app will be running at `localhost:8080`.

To stop:

```
make env-down
```

To see the status of the environment, you can run:

```
make env-status
```

This command should pull/build the necessary containers to run the
application locally, the first run might take a while. This includes
a local elasticsearch, zeebe, operate backend and frontend.

You can clean your local docker environment using:

```
make env-clean
```

This will delete all dangling images and containers. It might be useful
when you run out of space or if you encounter any problem with the docker
daemon.

## Commit Message Guidelines

* **feat** (new feature for the user, not a new feature for build script)
* **fix** (bug fix for the user, not a fix to a build script)
* **docs** (changes to the documentation)
* **style** (changes to css, styling, etc; no business logic change)
* **refactor** (refactoring production code, eg. renaming a variable)
* **test** (adding missing tests, refactoring tests; no production code change)
* **chore** (updating grunt tasks etc; no production code change)

## Testing environments

1. The **staging** environment is available here: https://stage.operate.camunda.cloud/ . Every commit to master (successfully built) will be published to stage automatically.
2. Moreover, every branch that ends with `-deploy` (e.s. `amazing-feature-deploy`) will be deployed online at $branch_name.operate.camunda.cloud (e.s. `amazing-feature.operate.camunda.cloud`).
3. **public-deploy** is a branch dedicated to user tests. It ends up on public.operate.camunda.cloud (**without** authentication)

To re-create the "public" environment (or any other environment):
* Open https://ci.operate.camunda.cloud/view/all/job/deploy-branch-to-k8s/build
* Change the BRANCH field to "public"
* (special) If you're deploying version 1.0.0-RC1, also put INFRASTRUCTURE_BRANCH: operate-public (this will run older Zeebe version: 0.13.1)
* Click on "Build"

License: This repository contains files subject to a commercial license.