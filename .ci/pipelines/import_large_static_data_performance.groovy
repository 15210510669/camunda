#!/usr/bin/env groovy

String agent() {
  boolean isStage = env.JENKINS_URL.contains('stage')
  String vaultPrefix = isStage ? 'stage.' : ''
  String prefix = isStage ? 'stage-' : ''
  """
---
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: agents-n1-standard-32-netssd-preempt
  serviceAccountName: ${prefix}ci-operate-camunda-cloud
  tolerations:
    - key: "agents-n1-standard-32-netssd-preempt"
      operator: "Exists"
      effect: "NoSchedule"
  initContainers:
    - name: vault-template
      image: gcr.io/camunda-public/camunda-internal_vault-template
      imagePullPolicy: Always
      env:
      - name: VAULT_ADDR
        value: https://${vaultPrefix}vault.int.camunda.com/
      - name: CLUSTER
        value: camunda-ci-v2
      - name: SA_NAMESPACE
        valueFrom:
          fieldRef:
            apiVersion: v1
            fieldPath: metadata.namespace
      - name: SA_NAME
        valueFrom:
          fieldRef:
            apiVersion: v1
            fieldPath: spec.serviceAccountName
      volumeMounts:
      - mountPath: /etc/consul-templates
        name: vault-config
      - mountPath: /etc/vault-output
        name: vault-output
    - name: init-sysctl
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.13
      command:
      - "sh"
      args:
      - "-c"
      - "sysctl -w vm.max_map_count=262144 && \
         cp -r /usr/share/elasticsearch/config/* /usr/share/elasticsearch/config_new/"
      securityContext:
        privileged: true
      volumeMounts:
      - mountPath: /usr/share/elasticsearch/config_new/
        name: configdir
    - name: init-plugins
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.13
      command:
      - "sh"
      args:
      - "-c"
      - "elasticsearch-plugin install --batch repository-gcs && \
        elasticsearch-keystore create && \
        elasticsearch-keystore add-file gcs.client.operate_ci_service_account.credentials_file /usr/share/elasticsearch/svc/operate-ci-service-account.json"
      securityContext:
        privileged: true
        capabilities:
          add:
            - IPC_LOCK
      volumeMounts:
      - mountPath: /usr/share/elasticsearch/config/
        name: configdir
      - mountPath: /usr/share/elasticsearch/plugins/
        name: plugindir
      - mountPath: /usr/share/elasticsearch/svc/
        name: vault-output
        readOnly: true
  containers:
    - name: maven
      image: maven:3.6.1-jdk-11
      command: ["cat"]
      tty: true
      env:
        - name: LIMITS_CPU
          valueFrom:
            resourceFieldRef:
              resource: limits.cpu
      resources:
        limits:
          cpu: 4
          memory: 8Gi
        requests:
          cpu: 4
          memory: 8Gi
    - name: elasticsearch
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.13
      env:
        - name: ES_JAVA_OPTS
          value: '-Xms4g -Xmx4g'
        - name: cluster.name
          value: docker-cluster
        - name: discovery.type
          value: single-node
        - name: action.auto_create_index
          value: "true"
        - name: bootstrap.memory_lock
          value: "true"
      livenessProbe:
        httpGet:
          path: /_cluster/health?local=true
          port: es-http
        initialDelaySeconds: 10
      readinessProbe:
        httpGet:
          path: /_cluster/health?local=true
          port: es-http
        initialDelaySeconds: 10
      securityContext:
        privileged: true
        capabilities:
          add:
            - IPC_LOCK
      volumeMounts:
      - mountPath: /usr/share/elasticsearch/config/
        name: configdir
      - mountPath: /usr/share/elasticsearch/plugins/
        name: plugindir
      ports:
        - containerPort: 9200
          name: es-http
          protocol: TCP
        - containerPort: 9300
          name: es-transport
          protocol: TCP
      resources:
        limits:
          cpu: 4
          memory: 8Gi
        requests:
          cpu: 4
          memory: 8Gi
    - name: zeebe
      image: camunda/zeebe:0.26.0-alpha1
      #imagePullPolicy: Always   #this must be uncommented when snapshot is used
      env:
        - name: ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME
          value: io.zeebe.exporter.ElasticsearchExporter
        - name: ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT
          value: 10
      resources:
        limits:
          cpu: 4
          memory: 8Gi
        requests:
          cpu: 4
          memory: 8Gi
  volumes:
  - name: configdir
    emptyDir: {}
  - name: plugindir
    emptyDir: {}
  - name: vault-output
    emptyDir:
      medium: Memory
  - name: vault-config
    configMap:
      name: ${prefix}ci-operate-vault-templates
""" as String
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml agent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 2, unit: 'HOURS')
  }

  stages {
    stage('Prepare') {
      parallel {
        stage('Build Operate') {
          steps {
            git url: 'git@github.com:camunda/camunda-operate',
                branch: "master",
                credentialsId: 'camunda-jenkins-github-ssh',
                poll: false
            container('maven') {
              // Compile Operate and skip tests
              configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                sh ('mvn -B -s $MAVEN_SETTINGS_XML -DskipTests -P skipFrontendBuild clean install')
              }
            }
          }
        }
        stage('Import ES Snapshot') {
          steps {
            container('maven') {
                // Create repository
                sh ("""
                    echo \$(curl -sq -H "Content-Type: application/json" -d '{ "type": "gcs", "settings": { "bucket": "operate-data", "client": "operate_ci_service_account" }}' -XPUT "http://localhost:9200/_snapshot/my_gcs_repository")
                """)
                // Restore Snapshot
                sh ("""
                    echo \$(curl -sq -XPOST 'http://localhost:9200/_snapshot/my_gcs_repository/snapshot_1/_restore?wait_for_completion=true')
                """)
            }
          }
        }
      }
    }
    stage('Run performance tests') {
      steps {
        container('maven') {
          // Generate Data
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            sh ('mvn -B -s $MAVEN_SETTINGS_XML -f qa/import-performance-tests -P -docker,-skipTests verify')
          }
        }
      }
    }
    stage('Upload snapshot') {
      steps {
        container('maven') {
          //Delete Zeebe indices
          sh ("""
                echo \$(curl -sq -H "Content-Type: application/json" -XDELETE "http://localhost:9200/zeebe-record*")
            """)
          // Create repository
          sh ("""
                echo \$(curl -qs -H "Content-Type: application/json" -d '{ "type": "gcs", "settings": { "bucket": "operate-data", "client": "operate_ci_service_account" }}' -XPUT "http://localhost:9200/_snapshot/my_gcs_repository")
            """)
          // Delete previous Snapshot
          sh ("""
                echo \$(curl -qs -XDELETE "http://localhost:9200/_snapshot/my_gcs_repository/snapshot_2")
            """)
          // Trigger Snapshot
          sh ("""
                echo \$(curl -qs -XPUT "http://localhost:9200/_snapshot/my_gcs_repository/snapshot_2?wait_for_completion=true")
            """)
        }
      }
    }
  }

  post {
    failure {
      script {
        def notification = load "${pwd()}/.ci/pipelines/build_notification.groovy"
        notification.buildNotification(currentBuild.result)

        slackSend(
          channel: "#operate-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
          message: "Job build ${currentBuild.absoluteUrl} failed!")
      }
    }
  }
}
