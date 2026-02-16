pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Build') {
      steps {
        sh 'echo "building..."'
      }
    }

    stage('Deploy') {
      when {
        anyOf { branch 'dev'; branch 'sit' }
      }
      steps {
        sh "echo deploy to ${env.BRANCH_NAME}"
      }
    }
  }
}
