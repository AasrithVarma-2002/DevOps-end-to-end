// CI/CD for HR Portal: test -> build image -> scan -> push to ECR -> deploy to EKS with Helm.
// Runs on the Jenkins EC2. AWS access comes from its instance role (no keys); the server's
// environment already has AWS_REGION, EKS_CLUSTER_NAME, ECR_REGISTRY and ECR_REPOSITORY
// (set by terraform/scripts/jenkins-user-data.sh.tftpl).

pipeline {
  agent any

  options {
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
    disableConcurrentBuilds()            // two deploys at once would fight over the release
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  // Check GitHub every ~5 minutes; build only when there's a new commit
  triggers {
    pollSCM('H/5 * * * *')
  }

  environment {
    NAMESPACE  = 'hr-portal'
    RELEASE    = 'hr-portal'
    KUBECONFIG = "${WORKSPACE}/.kube/config"   // per-build kubeconfig, not shared with other jobs
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script {
          // Image tag = short commit ID, so every image maps to exact code
          env.TAG   = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
          env.REPO  = "${env.ECR_REGISTRY}/${env.ECR_REPOSITORY}"
          env.IMAGE = "${env.REPO}:${env.TAG}"
        }
        echo "Building ${env.IMAGE}"
      }
    }

    stage('Unit tests') {
      steps {
        dir('app') {
          sh './mvnw -B -q test'
        }
      }
    }

    stage('Build image') {
      steps {
        script {
          // ECR tags are immutable: if this commit was already pushed (e.g. by hand), reuse it
          env.IMAGE_EXISTS = sh(
            script: "aws ecr describe-images --repository-name ${env.ECR_REPOSITORY} --image-ids imageTag=${env.TAG} >/dev/null 2>&1 && echo yes || echo no",
            returnStdout: true).trim()
        }
        sh '''
          if [ "$IMAGE_EXISTS" = "yes" ]; then
            echo "Image $IMAGE already in ECR, skipping build"
          else
            docker build -t "$IMAGE" app/
          fi
        '''
      }
    }

    stage('Scan image') {
      when { environment name: 'IMAGE_EXISTS', value: 'no' }
      steps {
        // Report HIGH/CRITICAL vulnerabilities. Change --exit-code to 1 to fail the build on them.
        sh 'trivy image --no-progress --severity HIGH,CRITICAL --ignore-unfixed --exit-code 0 "$IMAGE"'
      }
    }

    stage('Push to ECR') {
      when { environment name: 'IMAGE_EXISTS', value: 'no' }
      steps {
        sh '''
          aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY"
          docker push "$IMAGE"
        '''
      }
    }

    stage('Deploy to EKS') {
      steps {
        sh '''
          aws eks update-kubeconfig --name "$EKS_CLUSTER_NAME" --kubeconfig "$KUBECONFIG"
          helm upgrade --install "$RELEASE" helm/hr-portal --namespace "$NAMESPACE" \
            --set image.repository="$REPO" \
            --set image.tag="$TAG" \
            --wait --timeout 10m --atomic
          kubectl -n "$NAMESPACE" get pods -o wide
          kubectl -n "$NAMESPACE" get ingress "$RELEASE"
        '''
      }
    }
  }

  post {
    success { echo "Deployed ${env.IMAGE}" }
    failure { echo 'Failed. If the deploy stage failed, Helm rolled back to the previous release (--atomic).' }
    always  { sh 'docker image prune -f >/dev/null 2>&1 || true' }
  }
}
