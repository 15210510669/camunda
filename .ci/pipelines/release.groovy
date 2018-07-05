#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static NODE_POOL() { return "slaves" }
// We can't use maven-alpine because 'frontend-maven-plugin' is incompatible
// Issue: https://github.com/eirslett/frontend-maven-plugin/issues/633
def static MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8" }
def static OPERATE_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-operate" }

String getGitCommitMsg() {
  return sh(script: 'git log --format=%B -n 1 HEAD', returnStdout: true).trim()
}

String getGitCommitHash() {
  return sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
}

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${env.BUILD_URL}consoleFull"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
}

static String mavenNodeJSAgent(env) {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
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
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -XX:MaxRAMFraction=\$(LIMITS_CPU)
    resources:
      limits:
        cpu: 2
        memory: 2Gi
      requests:
        cpu: 2
        memory: 2Gi
"""
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenNodeJSAgent(env)
    }
  }

  parameters {
    string(name: 'RELEASE_VERSION', defaultValue: '1.0.0', description: 'Version to release. Applied to pom.xml and Git tag.')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '1.1.0-SNAPSHOT', description: 'Next development version.')
  }

  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr:'50', artifactNumToKeepStr: '3'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-operate',
            branch: 'master',
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false

        container('maven') {
          sh ('''
            # git is required for maven release
            apt-get update && apt-get install -y git openssh-client

            # setup ssh for github
            mkdir -p ~/.ssh
            ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts

            git config --global user.email "ci@operate.camunda.cloud"
            git config --global user.name "camunda-jenkins"
          ''')
        }
      }
    }
    stage('Maven Release') {
      steps {
        container('maven') {
          sshagent(['camunda-jenkins-github-ssh']) {
            sh ("""
              mvn release:prepare release:perform -P -docker -DskipTests=true -B -T\$LIMITS_CPU --fail-at-end \
                -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn --settings=settings.xml \
                -Dtag=${params.RELEASE_VERSION} -DreleaseVersion=${params.RELEASE_VERSION} -DdevelopmentVersion=${params.DEVELOPMENT_VERSION} \
                '-Darguments=--settings=settings.xml -P -docker -DskipTests=true -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn'
            """)
          }
        }
      }
    }
//    stage('Docker Image') {
//      environment {
//        VERSION = "${params.RELEASE_VERSION}"
//        REGISTRY = credentials('docker-registry-ci3')
//      }
//      steps {
//        container('docker') {
//          sh """
//            echo '${REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin
//
//            docker build -t ${PROJECT_DOCKER_IMAGE()}:${VERSION} \
//              --build-arg=VERSION=${VERSION} \
//              --build-arg=SNAPSHOT=false \
//              --build-arg=USERNAME=${NEXUS_USR} \
//              --build-arg=PASSWORD=${NEXUS_PSW} \
//              .
//
//            docker push ${PROJECT_DOCKER_IMAGE()}:${VERSION}
//          """
//        }
//      }
//    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
  }
}
