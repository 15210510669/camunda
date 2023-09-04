ARG APP_ENV=prod

# Building builder image
FROM alpine:latest as builder
ARG DISTBALL

ENV TMP_ARCHIVE=/tmp/zeebe.tar.gz \
    TMP_DIR=/tmp/zeebe \
    TINI_VERSION=v0.19.0

COPY ${DISTBALL} ${TMP_ARCHIVE}

RUN mkdir -p ${TMP_DIR} && \
    tar xfvz ${TMP_ARCHIVE} --strip 1 -C ${TMP_DIR}

ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini ${TMP_DIR}/bin/tini
COPY docker/utils/startup.sh ${TMP_DIR}/bin/startup.sh
RUN chmod +x -R ${TMP_DIR}/bin/

# Building prod image
FROM eclipse-temurin:17-jre-focal@sha256:bed33979fe79ff4448686ca4074b43b87a8ea26f80cb3d6a9873634c4098ddfb as prod

# Building dev image
FROM eclipse-temurin:17-jdk-focal@sha256:3fb98e0f616c7f7b358cacc0b537067d137c5b4ee3066649dd325895d56931ee as dev
RUN echo "running DEV pre-install commands"
RUN apt-get update
RUN curl -sSL https://github.com/jvm-profiling-tools/async-profiler/releases/download/v1.7.1/async-profiler-1.7.1-linux-x64.tar.gz | tar xzv

# Building application image
FROM ${APP_ENV} as app

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false
ENV PATH "${ZB_HOME}/bin:${PATH}"
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
# We know there's no need to check for musl on this image
ENV ROCKSDB_MUSL_LIBC=false

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME /tmp
VOLUME ${ZB_HOME}/data
VOLUME ${ZB_HOME}/logs

COPY --from=builder --chown=1000:0 /tmp/zeebe ${ZB_HOME}

RUN groupadd -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe && \
    chmod g=u /etc/passwd && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${ZB_HOME}/data && \
    mkdir ${ZB_HOME}/logs && \
    chown -R 1000:0 ${ZB_HOME} && \
    chmod -R 0775 ${ZB_HOME}

COPY --from=builder --chown=1000:0 /tmp/zeebe/bin/startup.sh /usr/local/bin/startup.sh

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
