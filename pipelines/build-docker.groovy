pipeline {
  agent any

  parameters {
    string(name: 'PRODUCT_REPO', defaultValue: '', description: 'org/repo (optional if webhook provides REPO_FULL_NAME)')
    string(name: 'TARGET_BRANCH', defaultValue: 'dev', description: 'fallback branch if no webhook')
  }

  stages {
    stage('Resolve from webhook') {
      steps {
        script {
          // repo: prefer webhook
          def repo = (env.REPO_FULL_NAME ?: params.PRODUCT_REPO ?: '').trim()
          if (!repo) {
            error("Missing repo: env.REPO_FULL_NAME or params.PRODUCT_REPO")
          }
          env.PRODUCT_REPO = repo

          // branch: prefer webhook ref
          def ref = (env.GIT_REF ?: '').trim()
          def branchFromWebhook = ref
            ? ref.replace('refs/heads/', '').replace('refs/tags/', '')
            : ''

          env.TARGET_BRANCH = branchFromWebhook ?: params.TARGET_BRANCH

          echo "webhook ref         = '${ref}'"
          echo "resolved repo       = '${env.PRODUCT_REPO}'"
          echo "resolved branch     = '${env.TARGET_BRANCH}'"
        }
      }
    }

    stage('Log') {
      steps {
        echo "PRODUCT_REPO   = ${env.PRODUCT_REPO}"
        echo "TARGET_BRANCH  = ${env.TARGET_BRANCH}"
      }
    }
  }
}
