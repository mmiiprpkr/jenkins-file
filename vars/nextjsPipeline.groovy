def call(Map cfg = [:]) {
  def nodeVersion = cfg.get('nodeVersion', '18')
  def deployBranches = cfg.get('deployBranches', ['dev','sit'])

  pipeline {
    agent any

    environment {
      CI = "true"
    }

    stages {
      stage('Checkout') {
        steps { checkout scm }
      }

      stage('Install') {
        steps {
          sh 'node -v || true'
          sh 'npm ci'
        }
      }

      stage('Build') {
        steps {
          sh 'npm run build'
        }
      }

      stage('Deploy') {
        when {
          expression { deployBranches.contains(env.BRANCH_NAME) }
        }
        steps {
          sh "echo Deploy branch = ${env.BRANCH_NAME}"
          // TODO: ใส่คำสั่ง deploy จริงทีหลัง
        }
      }
    }
  }
}
