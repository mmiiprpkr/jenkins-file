pipeline {
  agent any

  parameters {
    string(name: 'APP_REPO_URL', defaultValue: '', description: 'Optional override app repo URL')
    string(name: 'APP_BRANCH',   defaultValue: '', description: 'Optional override branch (default = BRANCH_NAME)')
  }

  environment {
    // Multibranch จะมี BRANCH_NAME ให้อยู่แล้ว
    EFFECTIVE_BRANCH = "${params.APP_BRANCH?.trim() ? params.APP_BRANCH.trim() : env.BRANCH_NAME}"
  }

  stages {
    stage('Resolve & Log Context') {
      steps {
        script {
          // ชื่อ repo จาก JOB_NAME เช่น myapp-pipeline/dev -> เอาตัวหน้า
          def job = env.JOB_NAME ?: ''
          def repoGuess = job.contains('/') ? job.split('/')[0] : job

          echo "=== CI CONTEXT ==="
          echo "job_name       : ${env.JOB_NAME}"
          echo "build_number   : ${env.BUILD_NUMBER}"
          echo "branch_name    : ${env.BRANCH_NAME}"
          echo "effective_branch: ${env.EFFECTIVE_BRANCH}"
          echo "repo_guess     : ${repoGuess}"

          // ถ้าต้องการดูว่าเป็น PR ไหม
          echo "change_id (PR) : ${env.CHANGE_ID ?: '-'}"
          echo "change_branch  : ${env.CHANGE_BRANCH ?: '-'}"
          echo "change_target  : ${env.CHANGE_TARGET ?: '-'}"
        }
      }
    }

    stage('Checkout App Repo') {
      steps {
        script {
          // ถ้าไม่ได้ override ให้ใช้ SCM ของ multibranch ที่ผูกกับ app repo อยู่แล้ว
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

          sh 'ls -la'
          sh 'test -f Dockerfile && echo "Dockerfile found ✅" || (echo "Dockerfile missing ❌" && exit 1)'
        }
      }
    }
  }
}
