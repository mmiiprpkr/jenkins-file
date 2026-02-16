pipeline {
  agent any

  parameters {
    string(name: 'PRODUCT_REPO', defaultValue: '', description: 'org/repo (optional; fallback if SCM url not usable)')
    string(name: 'TARGET_BRANCH', defaultValue: '', description: 'fallback branch if cannot detect')
  }

  stages {
    stage('Resolve from SCM') {
      steps {
        script {
          // 1) env จาก job name
          def envFromJobName = env.JOB_NAME.tokenize('/').last()
          env.DEPLOY_ENV = envFromJobName
          echo "🎯 Environment (from job name): ${env.DEPLOY_ENV}"

          def defaultBranchMap = [
            'dev': 'develop',
            'sit': 'sit',
            'uat': 'uat',
            'main': 'main'
          ]
          def expectedBranch = defaultBranchMap[env.DEPLOY_ENV] ?: 'develop'

          // 2) repo from SCM (GitHub plugin case)
          def repo = (params.PRODUCT_REPO ?: '').trim()
          if (!repo) {
            def gitUrl = (env.GIT_URL ?: '').trim()

            // fallback: read from scm config (works if pipeline has SCM)
            if (!gitUrl && scm?.userRemoteConfigs) {
              gitUrl = (scm.userRemoteConfigs[0]?.url ?: '').trim()
            }

            // NOTE: your log shows 2 remotes:
            // origin  = jenkins-file.git
            // origin1 = nextjs-i18n.git
            // If you want "product repo", prefer origin1 when present
            if (scm?.userRemoteConfigs?.size() >= 2) {
              def url0 = (scm.userRemoteConfigs[0]?.url ?: '').trim()
              def url1 = (scm.userRemoteConfigs[1]?.url ?: '').trim()
              // heuristic: if one looks like "jenkins-file", use the other as product repo
              if (url0.contains('jenkins-file') && url1) gitUrl = url1
              else if (url1.contains('jenkins-file') && url0) gitUrl = url0
            }

            if (!gitUrl) {
              error("Missing repo: set PRODUCT_REPO param or ensure job has Git SCM configured")
            }

            repo = gitUrl
              .replaceFirst(/^git@github\.com:/, '')
              .replaceFirst(/^https?:\/\/github\.com\//, '')
              .replaceAll(/\.git$/, '')
              .trim()
          }
          env.PRODUCT_REPO = repo

          // 3) branch
          def b = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: params.TARGET_BRANCH ?: expectedBranch).trim()
          b = b.replaceFirst(/^origin\//, '')
          b = b.replaceFirst(/^origin1\//, '')
          env.TARGET_BRANCH = b

          // 4) validation
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
    }

    stage('Log') {
      steps {
        echo "PRODUCT_REPO   = ${env.PRODUCT_REPO}"
        echo "TARGET_BRANCH  = ${env.TARGET_BRANCH}"
        echo "GIT_URL        = ${env.GIT_URL}"
        echo "GIT_BRANCH     = ${env.GIT_BRANCH}"
        echo "BRANCH_NAME    = ${env.BRANCH_NAME}"
      }
    }
  }
}
