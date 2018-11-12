#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves-ssd" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8-slim" }
def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) { return "camunda/camunda-bpm-platform:${cambpmVersion}" }
def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) { return "docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}" }

static String mavenElasticsearchAgent(env, esVersion = '6.0.0', cambpmVersion = '7.10.0-SNAPSHOT') {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  securityContext:
    fsGroup: 1000
  volumes:
    - name: es-storage
      hostPath:
        path: /mnt/disks/ssd0
        type: Directory
    - name: cambpm-storage
      emptyDir: {}
  initContainers:
  - name: init-sysctl
    image: ${MAVEN_DOCKER_IMAGE()}
    command:
    - sysctl
    - -w
    - vm.max_map_count=262144
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
        cpu: 2
        memory: 4Gi
      requests:
        cpu: 2
        memory: 4Gi
    volumeMounts:
      - name: es-storage
        mountPath: /es-storage
      - name: cambpm-storage
        mountPath: /cambpm-storage
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    env:
      - name: DB_DRIVER
        value: org.postgresql.Driver
      - name: DB_USERNAME
        value: camunda
      - name: DB_PASSWORD
        value: camunda123
      - name: DB_URL
        value: jdbc:postgresql://opt-ci-perf.db:5432/optimize-ci-performance
      - name: TZ
        value: Europe/Berlin
      - name: WAIT_FOR
        value: opt-ci-perf.db:5432
      - name: JAVA_OPTS
        value: "-Xms2g -Xmx2g -XX:MaxMetaspaceSize=256m"
    resources:
      limits:
        cpu: 4
        memory: 3Gi
      requests:
        cpu: 4
        memory: 3Gi
    volumeMounts:
      - name: cambpm-storage
        mountPath: /camunda/logs
  - name: elasticsearch
    image: ${ELASTICSEARCH_DOCKER_IMAGE(esVersion)}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms6g -Xmx6g"
    - name: cluster.name
      value: elasticsearch
    - name: discovery.type
      value: single-node
    - name: action.auto_create_index
      value: false
    - name: bootstrap.memory_lock
      value: true
    securityContext:
      capabilities:
        add:
          - IPC_LOCK
    ports:
    - containerPort: 9200
      name: es-http
      protocol: TCP
    - containerPort: 9300
      name: es-transport
      protocol: TCP
    resources:
      limits:
        cpu: 12
        memory: 8Gi
      requests:
        cpu: 12
        memory: 8Gi
    volumeMounts:
      - name: es-storage
        mountPath: /usr/share/elasticsearch/data
        subPath: data
      - name: es-storage
        mountPath: /usr/share/elasticsearch/logs
        subPath: logs
"""
}

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  String buildResultUrl = "${env.BUILD_URL}"
  if(env.RUN_DISPLAY_URL) {
    buildResultUrl = "${env.RUN_DISPLAY_URL}"
  }

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${buildResultUrl}"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
}

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenElasticsearchAgent(env, params.ES_VERSION, params.CAMBPM_VERSION)
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '50'))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-optimize',
            branch: "${params.BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
      }
    }
    stage('Query Performance') {
      steps {
        container('maven') {
          sh 'mvn -T\$LIMITS_CPU -DskipTests -Dskip.fe.build -Dskip.docker -s settings.xml clean install -B'
          sh 'mvn -f qa/query-performance-tests/pom.xml -s settings.xml clean verify -Pquery-performance-tests -B'
        }
      }
      post {
        always {
          container('maven') {
            sh 'curl localhost:9200/_cat/indices?v'
            sh ('''#!/bin/bash -ex
              cp -R --parents /es-storage/logs /cambpm-storage .
              chown -R 10000:1000 ./{es-storage,cambpm-storage}
            ''')
            archiveArtifacts artifacts: 'es-storage/logs/*,cambpm-storage/**/*', onlyIfSuccessful: false
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
