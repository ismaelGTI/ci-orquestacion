pipeline {
    agent {
        kubernetes {
            defaultContainer 'jdk'
            namespace 'default'
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    jenkins: "slave"
  annotations:
    kubernetes.jenkins.io/pod-retention: "on-failure"  # Mantiene el Pod si el pipeline falla
spec:
  containers:
    - name: jdk
      image: docker.io/eclipse-temurin:21-jdk
      imagePullPolicy: Always
      command:
        - cat
      tty: true
      securityContext:
        runAsUser: 0
        privileged: true
      volumeMounts:
        - name: m2-cache
          mountPath: /root/.m2
    - name: podman
      image: quay.io/containers/podman:v4.5.1
      command:
        - cat
      tty: true
      securityContext:
        runAsUser: 0
        privileged: true
    - name: kubectl
      image: docker.io/bitnami/kubectl:1.27.3
      command:
        - cat
      tty: true
      securityContext:
        runAsUser: 0
        privileged: true
    - name: jnlp
      image: jenkins/inbound-agent:latest
      args: ['$(JENKINS_SECRET)', '$(JENKINS_NAME)']

  volumes:
    - name: m2-cache
      hostPath:
        path: /data/m2-cache
        type: DirectoryOrCreate
'''
        }
    }

    environment {
        APP_NAME = getPomArtifactId()
        APP_VERSION = getPomVersionNoQualifier()
        APP_CONTEXT_ROOT = '/' // it should be '/' or '<some-context>/'
        APP_LISTENING_PORT = '8080'
        APP_JACOCO_PORT = '6300'
        CONTAINER_REGISTRY_URL = 'docker.io'
        IMAGE_ORG = 'ismaelmrn'
        IMAGE_NAME = "$IMAGE_ORG/$APP_NAME"
        IMAGE_SNAPSHOT = "$IMAGE_NAME:$APP_VERSION-snapshot-$BUILD_NUMBER"
        IMAGE_SNAPSHOT_LATEST = "$IMAGE_NAME:latest-snapshot"
        IMAGE_GA = "$IMAGE_NAME:$APP_VERSION"
        IMAGE_GA_LATEST = "$IMAGE_NAME:latest"
        EPHTEST_CONTAINER_NAME = "ephtest-$APP_NAME-snapshot-$BUILD_NUMBER"
        EPHTEST_BASE_URL = "http://$EPHTEST_CONTAINER_NAME:$APP_LISTENING_PORT".concat("/$APP_CONTEXT_ROOT".replace('//', '/'))

        // credentials
        KUBERNETES_CLUSTER_CRED_ID = 'rancher-desktop-kubeconfig1.0'
        CONTAINER_REGISTRY_CRED = credentials("docker-hub-$IMAGE_ORG")
    }

    stages {
        stage('Prepare environment') {
            steps {
                echo '-=- prepare environment -=-'
                echo "APP_NAME: ${APP_NAME}\nAPP_VERSION: ${APP_VERSION}"
                echo "the name for the epheremeral test container to be created is: $EPHTEST_CONTAINER_NAME"
                echo "the base URL for the epheremeral test container is: $EPHTEST_BASE_URL"
                sh 'java -version'
                sh 'chmod +x ./mvnw'
                sh './mvnw --version'
                container('podman') {
                    sh 'podman --version'
                    sh "podman login $CONTAINER_REGISTRY_URL -u $CONTAINER_REGISTRY_CRED_USR -p $CONTAINER_REGISTRY_CRED_PSW --tls-verify=false"
                }
                container('kubectl') {
                    withKubeConfig([credentialsId: "$KUBERNETES_CLUSTER_CRED_ID"]) {
                        sh 'kubectl version'
                    }
                }
            }
        }

        stage('Unit tests') {
            steps {
                echo '-=- execute unit tests -=-'
                sh './mvnw test org.jacoco:jacoco-maven-plugin:report'
                junit 'target/surefire-reports/*.xml'
                jacoco execPattern: 'target/jacoco.exec'
            }
        }
        
        stage('Mutation tests') {
            steps {
                echo '-=- execute mutation tests -=-'
                sh './mvnw org.pitest:pitest-maven:mutationCoverage'
            }
        }
        
        stage('Quality Gates') {
            steps {
                script {
                    qualityGates = readYaml file: 'quality-gates.yaml'
                    echo "Quality gates loaded: ${qualityGates}"
                }
            }
        }

        stage('Package') {
            steps {
                echo '-=- packaging project -=-'
                sh './mvnw package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Build & push container image') {
            steps {
                echo '-=- build & push container image -=-'
                container('podman') {
                    sh "podman build -t $IMAGE_SNAPSHOT ."
                    sh "podman tag $IMAGE_SNAPSHOT $CONTAINER_REGISTRY_URL/$IMAGE_SNAPSHOT"
                    sh "podman push $CONTAINER_REGISTRY_URL/$IMAGE_SNAPSHOT"
                    sh "podman tag $IMAGE_SNAPSHOT $CONTAINER_REGISTRY_URL/$IMAGE_SNAPSHOT_LATEST"
                    sh "podman push $CONTAINER_REGISTRY_URL/$IMAGE_SNAPSHOT_LATEST"
                }
            }
        }
    }
}



def getPomVersion() {
    return readMavenPom().version
}

def getPomVersionNoQualifier() {
    return readMavenPom().version.split('-')[0]
}

def getPomArtifactId() {
    return readMavenPom().artifactId
}
