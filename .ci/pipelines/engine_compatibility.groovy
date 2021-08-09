#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.1-jdk-11-slim" }

def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }

String basePodSpec() {
  return """
apiVersion: v1
kind: Pod
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
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda/infra-core/tree/master/camunda-ci/deployments/optimize
      name: ci-optimize-cambpm-config
  imagePullSecrets:
  - name: registry-camunda-cloud
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
      command: ["sysctl", "-w", "vm.max_map_count=262144"]
      securityContext:
        privileged: true
        capabilities:
          add: ["IPC_LOCK", "SYS_RESOURCE"]
    - name: increase-the-ulimit
      image: busybox
      command: ["sh", "-c", "ulimit -n 65536"]
      securityContext:
        privileged: true
        capabilities:
          add: ["IPC_LOCK", "SYS_RESOURCE"]
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        value: 6
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 6
        memory: 12Gi
      requests:
        cpu: 6
        memory: 12Gi
    """
}

String elasticSearchContainerSpec(def esVersion) {
  return """
  - name: elasticsearch-9200
    image: docker.elastic.co/elasticsearch/elasticsearch:${esVersion}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms2g -Xmx2g"
    - name: cluster.name
      value: elasticsearch
    - name: http.port
      value: 9200
    - name: discovery.type
      value: single-node
    - name: bootstrap.memory_lock
      value: true
    # We usually run our integration tests concurrently, as some cleanup methods like #deleteAllOptimizeData
    # internally make usage of scroll contexts this lead to hits on the scroll limit.
    # Thus this increased scroll context limit.
    - name: search.max_open_scroll_context
      value: 1000
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK", "SYS_RESOURCE"]
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
   """
}

String camBpmContainerSpec(String camBpmVersion) {
  String camBpmDockerImage = getCamBpmDockerImage(camBpmVersion)
  return """
  - name: cambpm
    image: ${camBpmDockerImage}
    imagePullPolicy: Always
    tty: true
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

void integrationTestSteps(String camBpmVersion) {
  optimizeCloneGitRepo(params.BRANCH)
  container('maven') {
    runMaven("verify -Dskip.docker -Pit,engine-${camBpmVersion} -pl backend -am -T\$LIMITS_CPU")
  }
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

String integrationTestPodSpec(String camBpmVersion, def esVersion) {
  return basePodSpec() + camBpmContainerSpec(camBpmVersion) + elasticSearchContainerSpec(esVersion)
}

String getCamBpmDockerImage(String camBpmVersion) {
  return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${camBpmVersion}"
}

pipeline {
  agent none
  environment {
    CAM_REGISTRY = credentials('repository-camunda-cloud')
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
    GCR_REGISTRY = credentials('docker-registry-ci3')
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage("Prepare") {
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
        setCamBpmSnapshotVersion()
        script {
          env.CAMBPM_7_13_VERSION = getCamBpmVersion('engine-7.13')
          env.CAMBPM_7_14_VERSION = getCamBpmVersion('engine-7.14')
          env.CAMBPM_7_15_VERSION = getCamBpmVersion('engine-7.15')
        }
      }
    }
    stage('IT') {
      failFast false
      parallel {
        stage('IT 7.13') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.13_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_13_VERSION, env.ES_VERSION)
            }
          }
          steps {
            integrationTestSteps('7.13')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('IT 7.14') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.14_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_14_VERSION, env.ES_VERSION)
            }
          }
          steps {
            integrationTestSteps('7.14')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('IT 7.15') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.15_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_15_VERSION, env.ES_VERSION)
            }
          }
          steps {
            integrationTestSteps('7.15')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('IT SNAPSHOT') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-engine-snapshot_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_SNAPSHOT_VERSION, env.ES_VERSION)
            }
          }
          steps {
            integrationTestSteps('snapshot')
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
      sendEmailNotification()
    }
    always {
      retriggerBuildIfDisconnected()
    }
  }
}

private void getCamBpmVersion(String profileId) {
  def profile = readMavenPom().getProfiles().find { it.getId().equals(profileId) }
  return profile.getProperties().getProperty("camunda.engine.version")
}
