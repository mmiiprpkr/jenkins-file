stage('Resolve from SCM') {
  steps {
    script {
      // 1) env จาก job name
      def envFromJobName = env.JOB_NAME.tokenize('/').last()
      env.DEPLOY_ENV = envFromJobName

      def defaultBranchMap = [
        'dev': 'develop',
        'sit': 'sit',
        'uat': 'uat',
        'main': 'main'
      ]
      def expectedBranch = defaultBranchMap[env.DEPLOY_ENV] ?: 'develop'

      // 2) repo from SCM
      def repo = (params.PRODUCT_REPO ?: '').trim()
      if (!repo) {
        // try Jenkins env first
        def gitUrl = (env.GIT_URL ?: '').trim()

        // fallback: read from scm config
        if (!gitUrl && scm?.userRemoteConfigs) {
          gitUrl = scm.userRemoteConfigs[0]?.url ?: ''
        }

        if (!gitUrl) {
          error("Missing repo: set PRODUCT_REPO param or ensure job has Git SCM configured")
        }

        // git@github.com:org/repo.git  OR  https://github.com/org/repo.git
        repo = gitUrl
          .replaceFirst(/^git@github\.com:/, '')
          .replaceFirst(/^https?:\/\/github\.com\//, '')
          .replaceAll(/\.git$/, '')
          .trim()
      }
      env.PRODUCT_REPO = repo

      // 3) branch from Jenkins/Git plugin
      // - env.GIT_BRANCH often like "origin/develop"
      // - multibranch uses env.BRANCH_NAME
      def b = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: params.TARGET_BRANCH ?: expectedBranch).trim()
      b = b.replaceFirst(/^origin\//, '')
      env.TARGET_BRANCH = b

      // 4) optional validation
      if (env.TARGET_BRANCH != expectedBranch) {
        echo "⚠️ Branch mismatch: expected=${expectedBranch} got=${env.TARGET_BRANCH} (env=${env.DEPLOY_ENV})"
      }

      echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
      echo "📦 Repository:   ${env.PRODUCT_REPO}"
      echo "🌿 Branch:       ${env.TARGET_BRANCH}"
      echo "🎯 Environment:  ${env.DEPLOY_ENV}"
      echo "🏗️  Job:          ${env.JOB_NAME}"
      echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
  }

  stage('Log') {
    steps {
      echo "PRODUCT_REPO   = ${env.PRODUCT_REPO}"
      echo "TARGET_BRANCH  = ${env.TARGET_BRANCH}"
    }
  }
}
