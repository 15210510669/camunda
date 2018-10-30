#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves" }
def static DOCKER_IMAGE() { return "google/cloud-sdk:alpine" }

static String agent(env) {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  serviceAccountName: ci-operate-camunda-cloud
  containers:
  - name: gcloud
    image: ${DOCKER_IMAGE()}
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 500m
        memory: 500Mi
      requests:
        cpu: 500m
        memory: 500Mi
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
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml agent(env)
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 15, unit: 'MINUTES')
  }

  environment {
      REGISTRY = credentials('docker-registry-ci3')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda-ci/k8s-infrastructure',
            branch: "${params.INFRASTRUCTURE_BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false

        container('gcloud') {
            sh ("""
                # kubectl
                gcloud components install kubectl --quiet

                # gcloud
                echo '${REGISTRY}' > account.json
                gcloud auth activate-service-account --key-file=account.json
                gcloud info

                # setup ssh for github clone
                mkdir -p ~/.ssh
                ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
            """)
        }
      }
    }
    stage('Cleanup K8s branches') {
      steps {
        container('gcloud') {
          sshagent(['camunda-jenkins-github-ssh']) {
            sh("""
              ./cmd/k8s/cleanup-branch-deployment \
              camunda/camunda-operate \
              operate \
              gcr.io/ci-30-162810/camunda-operate
            """)
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

