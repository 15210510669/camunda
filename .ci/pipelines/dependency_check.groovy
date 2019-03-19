#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves" }

static String mavenElasticsearchAgent() {
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
  containers:
  - name: maven
    image: maven:3.5.3-jdk-8-slim
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      # every JVM process will get a 1/2 of HEAP from total memory
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -XX:MaxRAMFraction=\$(LIMITS_CPU)
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 3
        memory: 3Gi
      requests:
        cpu: 3
        memory: 3Gi
"""
}

void buildNotification() {
    String BACKEND_DIFF = sh(script: 'cd ./util/dependency-doc-creation/ && cat backend_diff.md', returnStdout: true)
            .replaceAll(">", "Added dependency:")
            .replaceAll("<", "Removed dependency:")
            .trim()

    String FRONTEND_DIFF = sh(script: 'cd ./util/dependency-doc-creation/ && cat frontend_diff.md', returnStdout: true)
            .replaceAll(">", "Added dependency:")
            .replaceAll("<", "Removed dependency:")
            .trim()

    FRONTEND_DIFF = buildDiff(FRONTEND_DIFF, "FRONTEND")
    BACKEND_DIFF = buildDiff(BACKEND_DIFF, "BACKEND")

    if (FRONTEND_DIFF || BACKEND_DIFF) {
        sendEmail(FRONTEND_DIFF, BACKEND_DIFF)
    }
}

private static String buildDiff(String DIFF, String end) {
    DIFF ? "${end} DEPENDENCIES CHANGED:\n" +
            "${DIFF}\n" +
            "----------------------------------------\n\n" :
            ""
}

private void sendEmail(String FRONTEND_DIFF, String BACKEND_DIFF) {
    String subject = "Optimize dependencies changed - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"

    String body =
            "Hello Optimize team, \n\n" +
            "while checking the dependencies of the Camunda Optimize repository, the following changes were detected:\n\n" +
            "${FRONTEND_DIFF}" +
            "${BACKEND_DIFF}" +
            "Please check if there are any unusual licenses for the new dependencies. If you are not sure if you are" +
            " allowed to use libraries with the given dependencies please notify/ask Roman Smirnov or Ulrike Liss.\n\n" +
            "Best\n" +
            "Your Optimize Dependency Check Bot"

    emailext subject: subject, body: body, to: "optimize@camunda.com"
}

pipeline {
    agent {
        kubernetes {
            cloud 'optimize-ci'
            label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
            defaultContainer 'jnlp'
            yaml mavenElasticsearchAgent()
        }
    }

    environment {
        NEXUS = credentials("camunda-nexus")
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '50'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Prepare') {
            steps {
                git url: 'git@github.com:camunda/camunda-optimize',
                        branch: "master",
                        credentialsId: 'camunda-jenkins-github-ssh',
                        poll: false
            }
        }
        stage('Create dependency lists') {
            steps {
                container('maven') {
                    sh ("""
                        cd ./util/dependency-doc-creation/
                        ./createOptimizeDependencyFiles.sh
                        
                        mv backend-dependencies.md ./curr_backend-dependencies.md
                        mv frontend-dependencies.md ./curr_frontend-dependencies.md
                        
                        apt-get update && \
                        apt-get -y install git openssh-client
                        mkdir -p ~/.ssh
                        ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
            
                        git config --global user.email "ci@camunda.com"
                        git config --global user.name "camunda-jenkins"
                        
                        git checkout `git log -1 --before=\\"\$(date -d "yesterday" +%d.%m.%Y)\\" --pretty=format:"%h"`
                        
                        ./createOptimizeDependencyFiles.sh

                        # diff returns status code 1 if there is difference, so the script crashes
                        # || : executes a null operator if diff fails, so the script continues running
                        diff backend-dependencies.md curr_backend-dependencies.md > backend_diff.md || :
                        diff frontend-dependencies.md curr_frontend-dependencies.md > frontend_diff.md || :
                        
                        # cut first line of the diff files (first line describes the changes)
                        sed '1d' frontend_diff.md > tmpfile; mv tmpfile frontend_diff.md
                        sed '1d' backend_diff.md > tmpfile; mv tmpfile backend_diff.md
                    """)
                    buildNotification()
                }
            }
        }
    }
}
