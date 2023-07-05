# syntax=docker/dockerfile:1.4
# Override this based on the architecture; this is currently pointing to amd64
ARG BASE_DIGEST="sha256:1a6c81a1b4144fe893e2452a6340d4e3a54ca53356962ed36dea290d9c43400f"

# set to "build" to build zeebe from scratch instead of using a distball
ARG DIST="distball"

### Init image containing tini and the startup script ###
FROM ubuntu:jammy as init
WORKDIR /zeebe
RUN --mount=type=cache,target=/var/apt/cache,rw \
    apt-get -qq update && \
    apt-get install -y --no-install-recommends tini=0.19.0-1 && \
    cp /usr/bin/tini .
COPY --link --chown=1000:0 docker/utils/startup.sh .

### Build zeebe from scratch ###
FROM maven:3-eclipse-temurin-17 as build
WORKDIR /zeebe
ENV MAVEN_OPTS -XX:MaxRAMPercentage=80
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw mvn -B -am -pl dist package -T1C -D skipChecks -D skipTests
RUN mv dist/target/camunda-zeebe .

### Extract zeebe from distball ###
FROM ubuntu:jammy as distball
WORKDIR /zeebe
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
COPY --link ${DISTBALL} zeebe.tar.gz
RUN mkdir camunda-zeebe && tar xfvz zeebe.tar.gz --strip 1 -C camunda-zeebe

### Image containing the zeebe distribution ###
# hadolint ignore=DL3006
FROM ${DIST} as dist

# Building application image
# hadolint ignore=DL3006
FROM eclipse-temurin:17-jre-focal@${BASE_DIGEST} as app

# leave unset to use the default value at the top of the file
ARG BASE_DIGEST
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
LABEL org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:17-jre-focal"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="zeebe@camunda.com"
LABEL org.opencontainers.image.url="https://zeebe.io"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/zeebe-deployment/"
LABEL org.opencontainers.image.source="https://github.com/camunda/zeebe"
LABEL org.opencontainers.image.version="${VERSION}"
# According to https://github.com/opencontainers/image-spec/blob/main/annotations.md#pre-defined-annotation-keys
# and given we set the base.name and base.digest, we reference the manifest of the base image here
LABEL org.opencontainers.image.ref.name="eclipse-temurin:17-jre-focal"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="(Apache-2.0 AND LicenseRef-Zeebe-Community-1.1)"
LABEL org.opencontainers.image.title="Zeebe"
LABEL org.opencontainers.image.description="Workflow engine for microservice orchestration"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,orchestration,workflow"
LABEL io.k8s.description="Workflow engine for microservice orchestration"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="512Mi"
LABEL io.openshift.min-cpu="1"

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false \
    ZEEBE_RESTORE=false
ENV PATH "${ZB_HOME}/bin:${PATH}"
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
# We know there's no need to check for musl on this image
ENV ROCKSDB_MUSL_LIBC=false

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME /tmp
VOLUME ${ZB_HOME}/data
VOLUME ${ZB_HOME}/logs

RUN groupadd -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe && \
    chmod g=u /etc/passwd && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${ZB_HOME}/data && \
    mkdir ${ZB_HOME}/logs && \
    chown -R 1000:0 ${ZB_HOME} && \
    chmod -R 0775 ${ZB_HOME}

COPY --from=init --chown=1000:0 /zeebe/tini ${ZB_HOME}/bin/
COPY --from=init --chown=1000:0 /zeebe/startup.sh /usr/local/bin/startup.sh
COPY --from=dist --chown=1000:0 /zeebe/camunda-zeebe ${ZB_HOME}

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
