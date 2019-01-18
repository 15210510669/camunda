#!/usr/bin/env groovy

// general properties for CI execution
def static MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8-slim" }

def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) { return "registry.camunda.cloud/camunda-bpm-platform-ee:${cambpmVersion}" }

def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) {
  return "docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}"
}

static String mavenElasticsearchIntegrationTestAgent(esVersion, cambpmVersion = '7.10.0') {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: slaves
  imagePullSecrets:
    - name: registry-camunda-cloud-secret
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda-ci/k8s-infrastructure/tree/master/infrastructure/ci-30-162810/deployments/optimize
      name: ci-optimize-cambpm-config
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
      # every JVM process will get a 1/4 of HEAP from total memory
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: TZ
        value: Europe/Berlin
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      limits:
        cpu: 3
        memory: 3Gi
      requests:
        cpu: 3
        memory: 3Gi
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    env:
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -XX:MaxRAMFraction=1
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/bpm-platform.xml
      subPath: bpm-platform.xml
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
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
        cpu: 1
        memory: 2Gi
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
  sh ("mvn ${cmd} -s settings.xml -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
}

void integrationTestSteps() {
  git url: 'git@github.com:camunda/camunda-optimize',
          branch: "${params.BRANCH}",
          credentialsId: 'camunda-jenkins-github-ssh',
          poll: false
  container('maven') {
    runMaven("verify -Dskip.docker -Pproduction,it,engine-latest -pl backend -am -T\$LIMITS_CPU")
  }
}

pipeline {

  agent none

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Elasticsearch Integration Tests') {
      failFast false
      parallel {
        stage("Elasticsearch 6.3.1 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-6.3.1_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("6.3.1")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage("Elasticsearch 6.4.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-6.4.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("6.4.0")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage("Elasticsearch 6.5.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-6.5.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("6.5.0")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
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
  }
}
