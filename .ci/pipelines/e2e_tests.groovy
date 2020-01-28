#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves-ssd-stable" }

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }

def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) {
  return "registry.camunda.cloud/camunda-bpm-platform-ee:${cambpmVersion}"
}

def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) {
  return "docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}"
}

ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

static String e2eTestConfig(esVersion, camBpmVersion) {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  tolerations:
    - key: "${NODE_POOL()}"
      operator: "Exists"
      effect: "NoSchedule"
  imagePullSecrets:
    - name: registry-camunda-cloud-secret
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda-ci/k8s-infrastructure/tree/master/infrastructure/ci-30-162810/deployments/optimize
      name: ci-optimize-cambpm-config
  - name: gcloud2postgres
    emptyDir: {}
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
      command: ["sysctl", "-w", "vm.max_map_count=262144"]
      securityContext:
        privileged: true
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        value: 1
      - name: TZ
        value: Europe/Berlin
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
""" \
 + gcloudContainerSpec() \
 + postgresContainerSpec() + camBpmContainerSpec(camBpmVersion) \
 + elasticSearchContainerSpec(esVersion)
}

static String camBpmContainerSpec(String camBpmVersion) {
  return """
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(camBpmVersion)}
    tty: true
    env:
      - name: JAVA_OPTS
        value: "-Xms2g -Xmx2g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
      - name: DB_DRIVER
        value: "org.postgresql.Driver"
      - name: DB_USERNAME
        value: "camunda"
      - name: DB_PASSWORD
        value: "camunda"
      - name: DB_URL
        value: "jdbc:postgresql://localhost:5432/engine"
      - name: WAIT_FOR
        value: localhost:5432
    resources:
      limits:
        cpu: 4
        memory: 3Gi
      requests:
        cpu: 4
        memory: 3Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
    """
}

static String elasticSearchContainerSpec(esVersion) {
  return """
  - name: elasticsearch
    image: ${ELASTICSEARCH_DOCKER_IMAGE(esVersion)}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms1g -Xmx1g"
    - name: cluster.name
      value: elasticsearch
    - name: discovery.type
      value: single-node
    - name: bootstrap.memory_lock
      value: true
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK"]
    resources:
      requests:
        cpu: 2
        memory: 2Gi
  """
}

static String postgresContainerSpec() {
  return """
  - name: postgresql
    image: postgres:11.2
    env:
      - name: POSTGRES_USER
        value: camunda
      - name: POSTGRES_PASSWORD
        value: camunda
      - name: POSTGRES_DB
        value: engine
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
    volumeMounts:
    - name: gcloud2postgres
      mountPath: /db_dump/
  """
}

static String gcloudContainerSpec() {
  return """
  - name: gcloud
    image: google/cloud-sdk:slim
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
    volumeMounts:
    - name: gcloud2postgres
      mountPath: /db_dump/
  """
}

static String mavenAgent() {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  tolerations:
    - key: "${NODE_POOL()}"
      operator: "Exists"
      effect: "NoSchedule"
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
"""
}

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  String buildResultUrl = "${env.BUILD_URL}"
  if (env.RUN_DISPLAY_URL) {
    buildResultUrl = "${env.RUN_DISPLAY_URL}"
  }

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${buildResultUrl}"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

pipeline {
  agent none
  environment {
    NEXUS = credentials("camunda-nexus")
    BROWSERSTACK_USERNAME = credentials('browserstack-username')
    BROWSERSTACK_ACCESS_KEY = credentials('browserstack-access-key')
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    timestamps()
    timeout(time: 4, unit: 'HOURS')
  }

  stages {
    stage("Prepare") {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml mavenAgent()
        }
      }
      steps {
        cloneGitRepo()
        script {
          def mavenProps = readMavenPom().getProperties()
          env.ES_VERSION = params.ES_VERSION ? params.ES_VERSION : mavenProps.getProperty(ES_TEST_VERSION_POM_PROPERTY)
          env.CAMBPM_VERSION = params.CAMBPM_VERSION ? params.CAMBPM_VERSION : mavenProps.getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
        }
      }
    }
    stage('End to End Tests') {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build_e2etests_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml e2eTestConfig("${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
        }
      }
      stages {
        stage('Build') {
          steps {
            retry(2) {
              cloneGitRepo()
              container('maven') {
                runMaven('install -Pengine-latest -Dskip.docker -DskipTests -T\$LIMITS_CPU')
              }
            }
          }
        }
        stage('Restore Test Data') {
          steps {
            retry(2) {
              timeout(20) {
                container('gcloud') {
                  sh 'gsutil -q -m cp gs://optimize-data/optimize_large_data-e2e.sqlc /db_dump/dump.sqlc'
                }
                container('postgresql') {
                  sh 'pg_restore --clean --if-exists -v -h localhost -U camunda -d engine /db_dump/dump.sqlc'
                }
              }
            }
          }
        }
        stage('Run E2E Tests') {
          steps {
            container('maven') {
              runMaven('test -pl client -Pclient.e2etests-browserstack -Dskip.yarn.build')
            }
          }
          post {
            always {
              archiveArtifacts artifacts: 'client/build/*.log'
            }
          }
        }
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
    always {
      // Retrigger the build if the slave disconnected
      script {
        if (slaveDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}

private void cloneGitRepo() {
  git url: 'git@github.com:camunda/camunda-optimize',
          branch: "${params.BRANCH}",
          credentialsId: 'camunda-jenkins-github-ssh',
          poll: false
}
