pipeline {
  agent any

  parameters {
    string(name: 'APP_REPO_URL', defaultValue: '', description: 'Optional override app repo URL')
    string(name: 'APP_BRANCH',   defaultValue: '', description: 'Optional override branch (default = BRANCH_NAME)')
  }

  environment {
    EFFECTIVE_BRANCH = "${params.APP_BRANCH?.trim() ? params.APP_BRANCH.trim() : env.BRANCH_NAME}"
  }

  stages {
    stage('Resolve & Log Context') {
      steps {
        script {
          def job = env.JOB_NAME ?: ''
          def repoGuess = job.contains('/') ? job.split('/')[0] : job

          echo "=== CI CONTEXT (Jenkins) ==="
          echo "job_name        : ${env.JOB_NAME}"
          echo "build_number    : ${env.BUILD_NUMBER}"
          echo "branch_name     : ${env.BRANCH_NAME}"
          echo "effective_branch: ${env.EFFECTIVE_BRANCH}"
          echo "repo_guess      : ${repoGuess}"
          echo "change_id (PR)  : ${env.CHANGE_ID ?: '-'}"
          echo "change_branch   : ${env.CHANGE_BRANCH ?: '-'}"
          echo "change_target   : ${env.CHANGE_TARGET ?: '-'}"
        }
      }
    }

    stage('Checkout App Repo') {
      steps {
        script {
          if (!params.APP_REPO_URL?.trim()) {
            echo "Using multibranch SCM checkout"
            checkout scm
          } else {
            echo "Using override repo checkout: ${params.APP_REPO_URL}"
            checkout([
              $class: 'GitSCM',
              branches: [[name: "*/${env.EFFECTIVE_BRANCH}"]],
              userRemoteConfigs: [[url: params.APP_REPO_URL.trim()]]
            ])
          }

          // (1) Log from Jenkins/SCM env (ถ้ามี)
          echo "=== SCM CONTEXT (Env) ==="
          echo "GIT_URL    : ${env.GIT_URL ?: '-'}"
          echo "GIT_BRANCH : ${env.GIT_BRANCH ?: '-'}"
          echo "GIT_COMMIT : ${env.GIT_COMMIT ?: '-'}"

          // (2) Log from actual git in workspace (แนะนำที่สุด)
          sh '''
            set -eu
            echo "=== SCM CONTEXT (git) ==="
            echo -n "remote_url : "; git config --get remote.origin.url || echo "-"
            echo -n "branch     : "; git rev-parse --abbrev-ref HEAD || echo "-"
            echo -n "commit     : "; git rev-parse HEAD || echo "-"
            echo -n "commit_msg : "; git log -1 --pretty=%s || echo "-"
          '''
        }
      }
    }
  }
}
