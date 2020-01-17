#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves-ssd-small" }
def static GCLOUD_DOCKER_IMAGE() { return "google/cloud-sdk:alpine" }
def static POSTGRES_DOCKER_IMAGE(String postgresVersion) { "postgres:${postgresVersion}" }
static String kubectlAgent(env, postgresVersion='9.6-alpine') {
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
  serviceAccountName: ci-optimize-camunda-cloud
  volumes:
  - name: import
    hostPath:
      path: /mnt/disks/ssd0
      type: Directory
  containers:
  - name: gcloud
    image: ${GCLOUD_DOCKER_IMAGE()}
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
    volumeMounts:
    - name: import 
      mountPath: /import
  - name: postgres
    image: ${POSTGRES_DOCKER_IMAGE(postgresVersion)}
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 500m
        memory: 500Mi
      requests:
        cpu: 500m
        memory: 500Mi
    env:
      - name: PGUSER
        value: camunda
      - name: PGPASSWORD 
        value: camunda123
      - name: PGHOST
        value: opt-ci-perf.db
      - name: PGDATABASE
        value: optimize-ci-performance
    volumeMounts:
    - name: import 
      mountPath: /import
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
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml kubectlAgent(env)
    }
  }

  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
  }

  stages {
    stage('Prepare') {
      steps {
        dir('k8s-infrastructure') {
          git url: 'git@github.com:camunda-ci/k8s-infrastructure',
            branch: "${params.INFRASTRUCTURE_BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
        }
        dir('optimize') {
          git url: 'git@github.com:camunda/camunda-optimize',
            branch: "${params.BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
        }

        container('gcloud') {
          sh("""
            gcloud components install kubectl --quiet
            apk add --no-cache jq py-pip && pip install yq
            gsutil stat gs://optimize-data/optimize_large_data-stage.sqlc  | yq ".[].ETag" >> /import/optimize_large_data-stage.etag || true
            gsutil cp   gs://optimize-data/optimize_large_data-stage.etag /import/optimize_large_data-stage.etag.old || true
            diff -Ns /import/optimize_large_data-stage.etag /import/optimize_large_data-stage.etag.new && touch /import/skip || true
          """)
        }
      }
    }
    stage('Import stage dataset') {
      steps {
        container('gcloud') {
          sh("""
            test -f /import/skip && echo "skipped" && exit 0
            gsutil cp gs://optimize-data/optimize_large_data-stage.sqlc /import/
          """)
        }
        container('postgres') {
          sh("""
            test -f /import/skip && echo "skipped" && exit 0
            pg_restore --if-exists --clean --jobs=24 --no-owner -d \$PGDATABASE /import/*.sqlc || echo pg_restore=\$?
          """)
        }
        container('gcloud') {
          sh("""
            test -f /import/skip && echo "skipped" && exit 0
            gsutil cp /import/*.etag gs://optimize-data/
          """)
        }
      }
    }
    stage('Deploy to K8s') {
      steps {
        container('gcloud') {
          dir('k8s-infrastructure') {
            sh("""
              ./cmd/k8s/deploy-template-to-branch \
              ${WORKSPACE}/k8s-infrastructure/infrastructure/ci-30-162810/deployments/optimize-branch \
              ${WORKSPACE}/optimize/.ci/branch-deployment \
              ${params.BRANCH} \
              optimize
            """)
          }
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'k8s-infrastructure/rendered-templates/**/*'
        }
      }
    }
    stage('Ingest Event Data') {
      steps {
        container('gcloud') {
          dir('optimize') {
            sh("""
              kubectl -n optimize-stage rollout status deployment/stage-optimize-camunda-cloud --watch=true
              kubectl -n optimize-stage port-forward deployment/stage-optimize-camunda-cloud 8090:8090 &
              while ! nc -z -w 3 localhost 8090; do
                sleep 5
              done
              curl -X PUT \
                http://localhost:8090/api/ingestion/event/batch \
                -H 'Content-Type: 'application/cloudevents-batch+json' \
                -H 'Authorization: secret' \
                --data "@${WORKSPACE}/optimize/client/demo-data/eventIngestionBatch.json"
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
