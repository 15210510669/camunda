#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

// general properties for CI execution
static String NODE_POOL() { return "slaves-stable" }
static String MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8-slim" }
static String DIND_DOCKER_IMAGE() { return "docker:18.06-dind" }

static Boolean isValidReleaseVersion(releaseVersion) {
  def version = releaseVersion.tokenize('.')
  def majorVersion = version[0]
  def minorVersion = version[1]
  def patchVersion = version[2]

  if (patchVersion == '0') {
    // only major / minor GA (.0) release versions will trigger a release of the example repo.
    return true
  } else {
    return false
  }
}

static String mavenDindAgent(env) {
  return """---
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
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
        cpu: 4
        memory: 3Gi
      requests:
        cpu: 2
        memory: 3Gi
  - name: docker
    image: ${DIND_DOCKER_IMAGE()}
    args: ["--storage-driver=overlay2"]
    securityContext:
      privileged: true
    tty: true
    resources:
      limits:
        cpu: 2
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
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

void runRelease(params) {
  def isValidRelease = isValidReleaseVersion(params.RELEASE_VERSION)
  if (!isValidRelease) {
    println "WARN: Aborting the release since [${params.RELEASE_VERSION}] is not a valid major/minor release version."
    return
  }

  sh ("""
    ### adjust the readme and add the new version to the version overview
    # find line where the version overview starts
    first_line=\$(grep -n "Optimize Version" README.md | head -n 1 | cut -d: -f1)

    if [ -z "\$first_line" ]; then
      echo "Could not find start of the version overview. Aborting instead!"
      exit 1
    fi

    # find line where the version overview ends
    last_line=\$(sed -n "\$first_line,/^\$/=" README.md | tail -n 1)

    if [ -z "last_line" ]; then
      echo "Could not find end of the version overview. Aborting instead!"
      exit 1
    fi

    # find lastest line of the version overview
    line_to_insert=\$(sed -n "\$first_line,/^| Latest/=" README.md | tail -n 1)

    if [ -z "line_to_insert" ] || [ "\$line_to_insert" -le "\$((\$first_line+1))" ] || \\
        [ "\$line_to_insert" -ge "\$((last_line+1))" ]; then
      echo "Could not find line to insert the Optimize version to overview. Aborting instead!"
      exit 1
    fi

    # add the new entry for the released version to the version overview
    version=${params.RELEASE_VERSION}
    sed "\$line_to_insert a\\
| \$version            \\
| [\$version tag](https://github.com/camunda/camunda-optimize-examples/tree/\$version) \\
| \\`git checkout \$version\\`  |" \\
    README.md > RESULT.md

    rm README.md
    mv RESULT.md README.md

    # update the optimize version in pom to the release version
    sed -i "s/optimize.version>.*</optimize.version>${params.RELEASE_VERSION}</g" pom.xml

    git add -u
    git commit -m \"chore(release): add version ${params.RELEASE_VERSION} to release version overview\"

    # create tag for the new Optimize version
    git tag -a ${params.RELEASE_VERSION} -m "Tag for version Optimize ${params.RELEASE_VERSION}"
    git push origin ${params.RELEASE_VERSION}

    # update the optimize version in pom to development version
    sed -i "s/optimize.version>.*</optimize.version>${params.DEVELOPMENT_VERSION}</g" pom.xml

    # push the changes
    git add -u
    git commit -m \"chore(release): update pom to snapshot for development version\"
    git push origin ${params.BRANCH}
  """)
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenDindAgent(env)
    }
  }

  parameters {
    string(name: 'RELEASE_VERSION', defaultValue: '3.0.0', description: 'Version to release. Applied to pom.xml and Git tag.')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '3.1.0-SNAPSHOT', description: 'Next development version.')
    string(name: 'BRANCH', defaultValue: 'master', description: 'The branch used for the release checkout.')
  }

  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    skipDefaultCheckout(true)
    buildDiscarder(logRotator(numToKeepStr:'50', artifactNumToKeepStr: '3'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-optimize-examples',
            branch: "${params.BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false

        container('maven') {
          sh ('''
            # install git and ssh
            apt-get update && \
            apt-get -y install git openssh-client

            # setup ssh for github
            mkdir -p ~/.ssh
            ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts

            # git config
            git config --global user.email "ci@camunda.com"
            git config --global user.name "camunda-jenkins"
          ''')
        }
      }
    }
    stage('Release') {
      steps {
        container('maven') {
          sshagent(['camunda-jenkins-github-ssh']) {
            runRelease(params)
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
