pipeline {
  agent any

  parameters {
    string(name: 'REPO_FULL_NAME', defaultValue: '', description: 'org/repo from webhook')
    string(name: 'GIT_REF', defaultValue: '', description: 'refs/heads/dev etc')
  }

  stages {
    stage('Resolve Branch') {
      steps {
        script {
          env.BRANCH = params.GIT_REF
            .replace('refs/heads/', '')
            .replace('refs/tags/', '')

          if (!(env.BRANCH in ['dev','sit', 'main'])) {
            currentBuild.result = 'NOT_BUILT'
            error("Ignore branch: ${env.BRANCH}")
          }
        }
      }
    }

    stage('Checkout Product Repo') {
      steps {
        checkout([$class: 'GitSCM',
          branches: [[name: "*/${env.BRANCH}"]],
          userRemoteConfigs: [[
            url: "https://github.com/${params.REPO_FULL_NAME}.git",
            credentialsId: "github-token"
          ]]
        ])
      }
    }

    stage('Docker Build') {
      steps {
        sh "docker build -t ${params.REPO_FULL_NAME}:${env.BRANCH} ."
      }
    }
  }
}
