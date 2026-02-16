pipeline {
  agent any

  stages {
    stage('Debug Webhook Vars') {
      steps {
        script {
          echo "env.GIT_REF        = '${env.GIT_REF}'"
          echo "env.REPO_FULL_NAME = '${env.REPO_FULL_NAME}'"

          // เผื่อบางทีอยากกด Build Now เองแบบใส่ params
          echo "params.GIT_REF        = '${params.GIT_REF}'"
          echo "params.REPO_FULL_NAME = '${params.REPO_FULL_NAME}'"
        }
      }
    }

    stage('Resolve Branch') {
      steps {
        script {
          def gitRef = (env.GIT_REF ?: params.GIT_REF ?: '').trim()
          def repo   = (env.REPO_FULL_NAME ?: params.REPO_FULL_NAME ?: '').trim()

          if (!gitRef || !repo) {
            currentBuild.result = 'NOT_BUILT'
            error("Missing vars: GIT_REF='${gitRef}', REPO_FULL_NAME='${repo}'")
          }

          env.BRANCH = gitRef
            .replace('refs/heads/', '')
            .replace('refs/tags/', '')

          echo "Resolved BRANCH = '${env.BRANCH}'"
          echo "Resolved REPO   = '${repo}'"

          if (!(env.BRANCH in ['dev','sit','main'])) {
            currentBuild.result = 'NOT_BUILT'
            error("Ignore branch: ${env.BRANCH}")
          }
        }
      }
    }

    stage('Log Only') {
      steps {
        echo "OK: webhook received for ${env.REPO_FULL_NAME} on ${env.BRANCH}"
      }
    }
  }
}
