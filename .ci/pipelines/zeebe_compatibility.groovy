#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }

def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) {
  return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${cambpmVersion}"
}

def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) {
  return "docker.elastic.co/elasticsearch/elasticsearch:${esVersion}"
}

def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.1-jdk-11-slim" }

static String mavenIntegrationTestSpec(String cambpmVersion, String esVersion) {
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
    - name: registry-camunda-cloud
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda/infra-core/tree/master/camunda-ci/deployments/optimize
      name: ci-optimize-cambpm-config
  - name: docker-storage
    emptyDir: {}
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
      command: ["sysctl", "-w", "vm.max_map_count=262144"]
      securityContext:
        privileged: true
  containers:
  - name: gcloud
    image: gcr.io/google.com/cloudsdktool/cloud-sdk:slim
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
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        value: 6
      - name: TZ
        value: Europe/Berlin
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      limits:
        cpu: 6
        memory: 20Gi
      requests:
        cpu: 6
        memory: 20Gi
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    imagePullPolicy: Always
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
  - name: elasticsearch
    image: ${ELASTICSEARCH_DOCKER_IMAGE(esVersion)}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms2g -Xmx2g"
    - name: cluster.name
      value: elasticsearch
    - name: discovery.type
      value: single-node
    - name: http.port
      value: 9200
    - name: bootstrap.memory_lock
      value: true
    # We usually run our integration tests concurrently, as some cleanup methods like `deleteAllOptimizeData`
    # internally make usage of scroll contexts this leads to hits on the scroll limit.
    # Thus this increased scroll context limit.
    - name: search.max_open_scroll_context
      value: 1000
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK"]
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
  - name: docker
    image: docker:20.10.5-dind
    args:
      - --storage-driver
      - overlay2
      - --ipv6
      - --fixed-cidr-v6
      - "2001:db8:1::/64"
    env:
      # The new DinD versions expect secure access using cert
      # Setting DOCKER_TLS_CERTDIR to empty string will disable the secure access
      # (see https://hub.docker.com/_/docker?tab=description&page=1)
      - name: DOCKER_TLS_CERTDIR
        value: ""
    securityContext:
      privileged: true
    volumeMounts:
      - mountPath: /var/lib/docker
        name: docker-storage
    tty: true
    resources:
      limits:
        cpu: 12
        memory: 16Gi
      requests:
        cpu: 12
        memory: 16Gi
"""
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

void integrationTestSteps(String version, boolean snapshot) {
  optimizeCloneGitRepo(params.BRANCH)
  container('maven') {
    runMaven("test -Dskip.fe.build");
    withCredentials([usernamePassword(credentialsId: 'camunda-nexus', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
      def zeebeVersionToUse =
        (version == "latest") ? getZeebeVersionFromTag("${version}", snapshot) : version
      def identityVersionToUse =
        (version == "latest") ? getIdentityVersionFromTag("${version}", snapshot, "$USERNAME", "$PASSWORD") : version
      sh("""    
        echo "running zeebe tests using Zeebe version: ${zeebeVersionToUse}, Identity version: ${identityVersionToUse}"
      """)
      runMaven("verify -Dzeebe.version=${zeebeVersionToUse} -Didentity.version=${identityVersionToUse} -Dzeebe.docker.version=${snapshot ? "SNAPSHOT" : zeebeVersionToUse} -Dit.test.includedGroups='Zeebe-test' -Dskip.docker -Pit,engine-latest -pl backend -am")
    }
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
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml plainMavenAgent(NODE_POOL(), MAVEN_DOCKER_IMAGE())
        }
      }
      steps {
        optimizeCloneGitRepo(params.BRANCH)
        setBuildEnvVars()
      }
    }
    stage('Zeebe Integration Tests') {
      failFast false
      parallel {
        stage("8.0.0") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-8-0-0_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-8-0-0_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("8.0.0", false)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe80.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe80.log', onlyIfSuccessful: false
            }
          }
        }
        stage("8.0.1") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-8-0-1_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-8-0-1_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("8.0.1", false)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe81.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe81.log', onlyIfSuccessful: false
            }
          }
        }
        stage("8.0.2") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-8-0-2_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-8-0-2_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("8.0.2", false)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud'){
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe82.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe82.log', onlyIfSuccessful: false
            }
          }
        }
        stage("8.0.3") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-8-0-3_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-8-0-3_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("8.0.3", false)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud'){
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe83.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe83.log', onlyIfSuccessful: false
            }
          }
        }
        stage("8.0.4") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-8-0-4_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-8-0-4_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("8.0.4", false)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud'){
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe84.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe84.log', onlyIfSuccessful: false
            }
          }
        }
        stage("8.0.5") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-8-0-5_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-8-0-5_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("8.0.5", false)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud'){
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe85.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe85.log', onlyIfSuccessful: false
            }
          }
        }
        stage("8.0.6") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-8-0-6_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-8-0-6_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("8.0.6", false)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud'){
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe86.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe86.log', onlyIfSuccessful: false
            }
          }
        }
        stage("Release") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-latest_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-latest_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("latest", false)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud'){
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe_latest.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe_latest.log', onlyIfSuccessful: false
            }
          }
        }
        stage("Snapshot") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-zeebe-snapshot_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestSpec("${env.CAMBPM_VERSION}", "${env.ES_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build-zeebe-snapshot_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps("latest", true)
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud'){
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_zeebe_snapshot.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_zeebe_snapshot.log', onlyIfSuccessful: false
            }
          }
        }
      }
    }
  }

  post {
    changed {
      sendEmailNotification()
    }
    always {
      retriggerBuildIfDisconnected()
    }
  }
}
