ARG BASE_IMAGE="eclipse-temurin:17-jre-focal"
ARG BASE_DIGEST_AMD64="sha256:22f133769ce2b956d150ab749cd4630b3e7fbac2b37049911aa0973a1283047c"
ARG BASE_DIGEST_ARM64="sha256:54f64f1cf8e9b984a92d06d3ad5c10fbbb9e9869144f1f45decdf530d64a4163"

# Prepare Operate Distribution
FROM alpine:3.18.2 as prepare

WORKDIR /tmp/operate

# download operate
COPY distro/target/camunda-operate-*.tar.gz operate.tar.gz
RUN tar xzvf operate.tar.gz --strip 1
RUN rm operate.tar.gz
COPY docker-notice.txt notice.txt
RUN sed -i '/^exec /i cat /usr/local/operate/notice.txt' bin/operate

### AMD64 base image ###
# BASE_DIGEST_AMD64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST_AMD64} as base-amd64
ARG BASE_DIGEST_AMD64
ARG BASE_DIGEST="${BASE_DIGEST_AMD64}"

### ARM64 base image ##
# BASE_DIGEST_ARM64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST_ARM64} as base-arm64
ARG BASE_DIGEST_ARM64
ARG BASE_DIGEST="${BASE_DIGEST_ARM64}"

### Application Image ###
# TARGETARCH is provided by buildkit
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006
FROM base-${TARGETARCH} as app
# leave unset to use the default value at the top of the file
ARG BASE_IMAGE
ARG BASE_DIGEST
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.name="docker.io/library/${BASE_IMAGE}"
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="operate@camunda.com"
LABEL org.opencontainers.image.url="https://camunda.com/platform/operate/"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/operate-deployment/install-and-start/"
LABEL org.opencontainers.image.source="https://github.com/camunda/camunda-operate"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="Proprietary"
LABEL org.opencontainers.image.title="Camunda Operate"
LABEL org.opencontainers.image.description="Tool for process observability and troubleshooting processes running in Camunda Platform 8"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,operate,camunda"
LABEL io.openshift.wants="zeebe,elasticsearch"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="512Mi"
LABEL io.openshift.min-cpu="1"
LABEL io.k8s.description="Tool for process observability and troubleshooting processes running in Camunda Platform 8"

EXPOSE 8080

ARG TARGETARCH
ADD https://github.com/krallin/tini/releases/download/v0.19.0/tini-${TARGETARCH} /bin/tini

WORKDIR /usr/local/operate

COPY --from=prepare /tmp/operate /usr/local/operate

RUN chmod +x /bin/tini

ENTRYPOINT ["/bin/tini", "--", "/usr/local/operate/bin/operate"]
