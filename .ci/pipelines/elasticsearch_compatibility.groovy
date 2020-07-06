#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-preempt" }

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.3-jdk-8-slim" }

def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) {
  return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${cambpmVersion}"
}

def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) {
  return "docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}"
}

CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"


static String mavenElasticsearchIntegrationTestAgent(esVersion, camBpmVersion) {
  return itStageBasePod() + camBpmContainerSpec(camBpmVersion) + elasticSearchContainerSpec(esVersion)
}

static String mavenElasticsearchAWSIntegrationTestAgent(camBpmVersion) {
  return itStageBasePod() + camBpmContainerSpec(camBpmVersion);
}

static String itStageBasePod() {
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
      # Defined in: https://github.com/camunda/infra-core/tree/master/camunda-ci-v2/deployments/optimize
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
      - name: LIMITS_CPU
        value: 2
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 6
        memory: 6Gi
      requests:
        cpu: 6
        memory: 6Gi
"""
}

static String camBpmContainerSpec(String camBpmVersion) {
  return """
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(camBpmVersion)}
    env:
      - name: JAVA_OPTS
        value: "-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 2Gi
      requests:
        cpu: 4
        memory: 2Gi
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
      limits:
        cpu: 2
        memory: 2Gi
      requests:
        cpu: 2
        memory: 2Gi
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

void gitCheckoutOptimize() {
  git url: 'git@github.com:camunda/camunda-optimize',
          branch: "${params.BRANCH}",
          credentialsId: 'camunda-jenkins-github-ssh',
          poll: false
}

void integrationTestSteps() {
  gitCheckoutOptimize()
  container('maven') {
    runMaven("verify -Dskip.docker -Pit,engine-latest -pl backend,upgrade -am -T\$LIMITS_CPU")
  }
}

void integrationTestStepsAWS() {
  gitCheckoutOptimize()

  container('maven') {
    sh("""    
      curl -s "http://$OPTIMIZE_ELASTICSEARCH_HOST/_cat/indices?v"
      
      #cleanup before starting the integration tests to assure starting from scratch
      curl -XDELETE "http://$OPTIMIZE_ELASTICSEARCH_HOST/_all"
      """)
    runMaven("verify -Dskip.docker -Pit,engine-latest -pl backend,upgrade -am -T\$LIMITS_CPU -DhttpTestTimeout=60000")
  }
}

pipeline {
  agent none
  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 90, unit: 'MINUTES')
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
        gitCheckoutOptimize()
        script {
          env.CAMBPM_VERSION = params.CAMBPM_VERSION ?: readMavenPom().getProperties().getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
        }
      }
    }
    stage('Elasticsearch Integration Tests') {
      failFast false
      parallel {
        stage("Elasticsearch 7.0.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-7.0.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("7.0.0", "${env.CAMBPM_VERSION}")
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
        stage("Elasticsearch 7.1.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-7.1.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("7.1.0", "${env.CAMBPM_VERSION}")
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
        stage("Elasticsearch 7.2.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-7.2.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("7.2.0", "${env.CAMBPM_VERSION}")
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
        stage("Elasticsearch 7.3.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-7.3.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("7.3.0", "${env.CAMBPM_VERSION}")
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
        stage("Elasticsearch 7.4.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-7.4.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("7.4.0", "${env.CAMBPM_VERSION}")
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
        stage("Elasticsearch 7.5.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-7.5.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("7.5.0", "${env.CAMBPM_VERSION}")
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
        stage("Elasticsearch 7.6.0 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-7.6.0_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("7.6.0", "${env.CAMBPM_VERSION}")
            }
          }
          steps {
            retry(2) {
              integrationTestSteps()
            }
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage("Elasticsearch AWS Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-AWS_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchAWSIntegrationTestAgent("${env.CAMBPM_VERSION}")
            }
          }

          environment {
            OPTIMIZE_ELASTICSEARCH_HOST = "ci-elasticsearch.optimize"
            OPTIMIZE_ELASTICSEARCH_HTTP_PORT = 80
          }

          steps {
            integrationTestStepsAWS()
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
    always {
      // Retrigger the build if the slave disconnected
      script {
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}
