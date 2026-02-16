pipeline {
  agent any
  options { skipDefaultCheckout(true) }

  environment {
    APP_REPO = 'https://github.com/mmiiprpkr/nextjs-i18n.git'
  }

  stages {
    stage('Resolve env') {
      steps {
        script {
          env.DEPLOY_ENV = env.JOB_NAME.tokenize('/').last()   // dev/sit/uat/main

          def branchMap = [dev:'dev', sit:'sit', uat:'uat', main:'main']
          env.APP_BRANCH = branchMap[env.DEPLOY_ENV] ?: 'dev'

          echo "DEPLOY_ENV=${env.DEPLOY_ENV} APP_BRANCH=${env.APP_BRANCH}"
        }
      }
    }

    stage('Checkout app') {
      steps {
        checkout([$class: 'GitSCM',
          branches: [[name: "*/${env.APP_BRANCH}"]],
          userRemoteConfigs: [[url: env.APP_REPO]]
        ])
      }
    }

    stage('Resolve env') {
      steps {
        script {
          env.DEPLOY_ENV = env.JOB_NAME.tokenize('/').last()

          def branchMap = [dev:'dev', sit:'sit', uat:'uat', main:'main']
          env.APP_BRANCH = branchMap[env.DEPLOY_ENV] ?: 'dev'

          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
          echo "🎯 ENV RESOLUTION"
          echo "DEPLOY_ENV      = ${env.DEPLOY_ENV}"
          echo "APP_BRANCH      = ${env.APP_BRANCH}"
          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        }
      }
    }
  }
}
