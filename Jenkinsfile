#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static OPERATE_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-operate" }

String getBranchSlug() {
  return env.BRANCH_NAME.toLowerCase().replaceAll(/[^a-z0-9-]/, '-')
}

String getGitCommitMsg() {
  return sh(script: 'git log --format=%B -n 1 HEAD', returnStdout: true).trim()
}

String getGitCommitHash() {
  return sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
}

String getImageTag() {
  return env.BRANCH_NAME == 'master' ? getGitCommitHash() : "branch-${getBranchSlug()}"
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yamlFile '.ci/podSpecs/builderAgent.yml'
    }
  }

  // Environment
  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(daysToKeepStr:'14', numToKeepStr:'50'))
    timestamps()
    timeout(time: 90, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        container('maven') {
          sh '.ci/scripts/ensure-naming-for-process.sh'
        }
      }
    }
    stage('Frontend - Build') {
      steps {
        container('node') {
          sh '''
            cd ./client
            yarn install --frozen-lockfile
            yarn run eslint src/
            yarn build
          '''
        }
      }
    }
    stage('Backend - Build') {
      steps {
        container('maven') {
          // MaxRAMFraction = LIMITS_CPU because there are only maven build threads
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            sh '''
            JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))" \
            mvn clean deploy -s $MAVEN_SETTINGS_XML -P -docker,skipFrontendBuild -DskipTests=true -B -T$LIMITS_CPU --fail-at-end \
                -DaltStagingDirectory=$(pwd)/staging -DskipRemoteStaging=true -Dmaven.deploy.skip=true
          '''
          }
        }
      }
    }
    stage('Unit tests') {
      parallel {
        stage('Backend - Tests') {
          steps {
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                // MaxRAMFraction = LIMITS_CPU+1 because there are LIMITS_CPU surefire threads + one maven thread
                sh '''
                  JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU+OLD_ZEEBE_TESTS_THREADS+3))" \
                  mvn verify -s $MAVEN_SETTINGS_XML -P -docker,skipFrontendBuild -B -T$LIMITS_CPU --fail-at-end
                  '''
              }
            }
          }
          post {
            always {
              junit testResults: 'qa/integration-tests/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
              junit testResults: 'importer/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
        }
        stage('Backend - Tests (old Zeebe)') {
          // TODO(menski): re-enable after 1.0
          when { expression { return false } }
          steps {
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                // MaxRAMFraction = LIMITS_CPU+1 because there are LIMITS_CPU surefire threads + one maven thread
                sh '''
                  JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU+OLD_ZEEBE_TESTS_THREADS+3))" \
                  mvn verify -f qa/import-old-zeebe-tests -s $MAVEN_SETTINGS_XML -P -docker,-skipTests -B -T$OLD_ZEEBE_TESTS_THREADS --fail-at-end
                  '''
              }
            }
          }
          post {
            always {
              junit testResults: 'qa/import-old-zeebe-tests/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
        }
        stage('Frontend - Tests') {
          steps {
            container('node') {
              sh '''
                cd ./client
                yarn test:ci
              '''
            }
          }
          post {
            always {
              junit testResults: 'client/jest-test-results.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
        }
        stage('End to end - Tests'){
          steps {
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                sh ('mvn -B -s $MAVEN_SETTINGS_XML -DCAMUNDA_OPERATE_CSRF_PREVENTION_ENABLED=false spring-boot:start -f webapp/pom.xml -Dspring-boot.run.fork=true')
                sh ('sleep 30')
                sh '''
                  JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU+OLD_ZEEBE_TESTS_THREADS+3))" \
                  mvn -B -s $MAVEN_SETTINGS_XML -f client/pom.xml -P client.e2etests-chromeheadless test
                  '''
                sh ('mvn -B -s $MAVEN_SETTINGS_XML spring-boot:stop -f webapp/pom.xml -Dspring-boot.stop.fork=true')
              }
            }
          }
        }
      }
    }
    stage('Prepare Docker login') {
      environment {
        GCR_REGISTRY = credentials('docker-registry-ci3')
        DOCKER_HUB = credentials('camunda-dockerhub')
      }
      steps {
        container('docker') {
          sh """
            echo '${GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin
            docker login --username ${DOCKER_HUB_USR} --password ${DOCKER_HUB_PSW}
          """
        }
      }
    }
    stage('Deploy') {
      parallel {
        stage('Deploy - Nexus Snapshot') {
          when {
              branch 'master'
          }
          steps {
            lock('operate-snapshot-upload') {
              container('maven') {
                configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                  sh '''
                    mvn org.sonatype.plugins:nexus-staging-maven-plugin:deploy-staged -DskipTests=true  -P -docker,skipFrontendBuild -B --fail-at-end \
                      -s $MAVEN_SETTINGS_XML \
                      -DaltStagingDirectory=$(pwd)/staging -DskipRemoteStaging=true -Dmaven.deploy.skip=true
                  '''
                }
              }
            }
          }
        }
        stage('Deploy - Docker Image') {
          when {
            not {
                expression { BRANCH_NAME ==~ /(.*-nodeploy)/ }
            }
          }
          environment {
            IMAGE_TAG = getImageTag()
          }
          steps {
            lock('operate-dockerimage-upload') {
              container('docker') {
                sh """
                  docker build -t ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} .
                  docker push ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG}

                  if [ "${env.BRANCH_NAME}" = 'master' ]; then
                    docker tag ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} ${OPERATE_DOCKER_IMAGE()}:latest
                    docker push ${OPERATE_DOCKER_IMAGE()}:latest
                  fi
                """
              }
            }
          }
        }
        stage('Deploy - Docker Image SNAPSHOT') {
          when {
              branch 'master'
          }
          environment {
            IMAGE_NAME = 'camunda/operate'
            IMAGE_TAG = 'SNAPSHOT'
          }
          steps {
            lock('operate-dockerimage-snapshot-upload') {
              container('docker') {
                sh """
                  docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                  docker push ${IMAGE_NAME}:${IMAGE_TAG}
                """
              }
            }
          }
        }
      }
    }
    stage ('Deploy to K8s') {
      when {
        not {
            expression { BRANCH_NAME ==~ /(.*-nodeploy)/ }
        }
      }
      steps {
        build job: '/deploy-branch-to-k8s',
          parameters: [
              string(name: 'BRANCH', value: getBranchSlug()),
              string(name: 'OPERATE_BRANCH', value: env.BRANCH_NAME),
          ]
      }
    }
  }

  post {
    unsuccessful {
      // Store container logs
      writeFile(
              file: "elastic_logs.txt",
              text: containerLog( name: "elasticsearch", returnLog: true )
      )
      archiveArtifacts artifacts: '*.txt'
      // Do not send notification if the node disconnected
      script {
        if (!agentDisconnected()) {
          def notification = load ".ci/pipelines/build_notification.groovy"
          notification.buildNotification(currentBuild.result)
        }
      }
    }
    changed {
      script {
        // Do not send notification if the node disconnected
        if (env.BRANCH_NAME == 'master' && !agentDisconnected()) {
          slackSend(
              channel: "#operate-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
              message: "Operate ${env.BRANCH_NAME} build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
        }
      }
    }
    always {
      // Retrigger the build if the node disconnected
      script {
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}
