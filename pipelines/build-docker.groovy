pipeline {
  agent any

  parameters {
    string(name: 'PRODUCT_REPO', defaultValue: 'mmiiprpkr/releuk-to-the-moon', description: 'org/repo')
    choice(name: 'TARGET_BRANCH', choices: ['dev','sit','main'], description: 'branch to build')
  }

  stages {
    stage('Log') {
      steps {
        echo "PRODUCT_REPO   = ${params.PRODUCT_REPO}"
        echo "TARGET_BRANCH  = ${params.TARGET_BRANCH}"
      }
    }
  }
}
