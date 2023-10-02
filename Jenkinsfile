#!/usr/bin/env groovy

// The job packages osp4j_2.6.14 with Talend's patch
// https://github.com/Talend/org.ops4j.pax.url/tree/url-2.6.14-tipaas

def slackChannel = 'tic-notifications'
def decodedJobName = env.JOB_NAME.replaceAll("%2F", "/")

pipeline {

    agent {
        kubernetes {
            label 'osp4j-build'
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: maven
      image: jenkinsxio/builder-maven:0.1.273
      command:
      - cat
      tty: true
      resources:
          requests:
            memory: "5120Mi"
            cpu: "2.0"
          limits:
            memory: "5120Mi"
            cpu: "2.0"
      volumeMounts:
      - name: docker
        mountPath: /var/run/docker.sock
  volumes:
  - name: docker
    hostPath:
      path: /var/run/docker.sock
  """
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
        timeout(time: 120, unit: 'MINUTES')
        skipStagesAfterUnstable()
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'SKIP_MAVEN_TEST', defaultValue: true, description: 'Pick to disable maven test')
        booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Pick to publish to artifacts-zl.talend.com')
        string(name: 'CLASSIFIER', defaultValue: 'tipaasTest', description: 'Jar classifier name')
    }

    stages {

        stage('Maven clean') {
          steps {
            container('maven') {
              sh "mvn clean"
            }
          }
        }

        stage('Maven package') {
          steps {
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-settings-nexus-zl', variable: 'MAVEN_SETTINGS')]) {
                sh "mvn -Dmaven.test.skip=${params.SKIP_MAVEN_TEST} -Dtipaas.classifier=${params.CLASSIFIER} package -f pax-url-aether/pom.xml -s $MAVEN_SETTINGS"
                archiveArtifacts artifacts: 'pax-url-aether/target/pax-url-aether-2.6.14-*.jar', fingerprint: true, onlyIfSuccessful: true
              }
            }
          }
        }

        stage('Publlish to Nexus') {
          when { expression { params.PUBLISH } }
          steps {
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-settings-nexus-zl', variable: 'MAVEN_SETTINGS')]) {
                sh "mvn deploy:deploy-file -s $MAVEN_SETTINGS -DgeneratePom=true -DrepositoryId=thirdparty-releases -DgroupId=org.ops4j.pax.url -DartifactId=pax-url-aether -Dversion=2.6.14 -Dclassifier=${params.CLASSIFIER} -Dpackaging=jar -Durl=https://artifacts-zl.talend.com/nexus/content/repositories/thirdparty-releases -Dfile=pax-url-aether/target/pax-url-aether-2.6.14-${params.CLASSIFIER}.jar"
              }
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
