#!/usr/bin/env groovy

// The job packages osp4j_2.6.17 with Talend's patch
// https://github.com/Talend/org.ops4j.pax.url/tree/url-2.6.17-tipaas

def slackChannel = '#tmc-engine-builds-notifications'
def decodedJobName = env.JOB_NAME.replaceAll("%2F", "/")

pipeline {

    agent {
        kubernetes {
            label 'osp4j-build'
            defaultContainer "default-container"
            yaml """
apiVersion: v1
kind: Pod
spec:
  imagePullSecrets:
    - name: talend-registry
  containers:
    - name: default-container
      image: artifactory.datapwn.com/tlnd-docker-dev/talend/common/tsbi/jdk8-builder-base:4.0.41-20251113144154
      command:
        - cat
      tty: true
      resources:
        requests:
          memory: "2048Mi"
          cpu: "1.0"
        limits:
          memory: "2048Mi"
          cpu: "1.0"
"""
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
        timeout(time: 30, unit: 'MINUTES')
        skipStagesAfterUnstable()
        disableConcurrentBuilds()
        ansiColor("xterm")
    }

    parameters {
        booleanParam(name: 'SKIP_MAVEN_TEST', defaultValue: false, description: 'Pick to disable maven test')
        booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Pick to publish to artifacts-zl.talend.com')
        string(name: 'CLASSIFIER', defaultValue: 'tipaasTest', description: 'Jar classifier name')
    }

    stages {

        stage('Maven clean') {
            steps {
                sh "mvn --no-transfer-progress clean"
            }
        }

        stage('Maven package') {
            steps {
                configFileProvider([configFile(fileId: 'maven-settings-nexus-zl', variable: 'MAVEN_SETTINGS')]) {
                    sh "mvn --no-transfer-progress -Dmaven.test.skip=${params.SKIP_MAVEN_TEST} -Dtipaas.classifier=${params.CLASSIFIER} package -f pax-url-aether/pom.xml -s $MAVEN_SETTINGS"
                    archiveArtifacts artifacts: 'pax-url-aether/target/pax-url-aether-2.6.17-*.jar', fingerprint: true, onlyIfSuccessful: true
                }
            }
        }

        stage('Publish to Nexus') {
            when {
                expression { return params.PUBLISH }
            }
            steps {
                configFileProvider([configFile(fileId: 'maven-settings-nexus-zl', variable: 'MAVEN_SETTINGS')]) {
                    sh "mvn --no-transfer-progress deploy:deploy-file -s $MAVEN_SETTINGS -DgeneratePom=true -DrepositoryId=thirdparty-releases -DgroupId=org.ops4j.pax.url -DartifactId=pax-url-aether -Dversion=2.6.17 -Dclassifier=${params.CLASSIFIER} -Dpackaging=jar -Durl=https://artifacts-zl.talend.com/nexus/content/repositories/thirdparty-releases -Dfile=pax-url-aether/target/pax-url-aether-2.6.17-${params.CLASSIFIER}.jar"
                }
            }
        }
    }

    post {
        success {
            slackSend (color: 'good', channel: "${slackChannel}", message: "SUCCESSFUL: `${decodedJobName}` #${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)\nDuration: ${currentBuild.durationString}")
        }
        unstable {
            slackSend (color: 'warning', channel: "${slackChannel}", message: "UNSTABLE: `${decodedJobName}` #${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)")
        }
        failure {
            slackSend (color: '#e81f3f', channel: "${slackChannel}", message: "FAILED: `${decodedJobName}` #${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)")
        }
        aborted {
            slackSend (color: 'warning', channel: "${slackChannel}", message: "ABORTED: `${decodedJobName}` #${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)")
        }
    }

}
