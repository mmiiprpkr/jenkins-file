pipeline {
  agent any

  parameters {
    string(name: 'PRODUCT_REPO', defaultValue: '', description: 'org/repo (optional if webhook provides it)')
    string(name: 'TARGET_BRANCH', defaultValue: '', description: 'fallback branch if no webhook')
  }

  stages {
    stage('Resolve from webhook') {
      steps {
        script {
          // 1. กำหนด Environment จาก Job Name
          def jobName = env.JOB_NAME // เช่น "my-folder/dev" หรือ "my-folder/sit"
          def envFromJobName = jobName.tokenize('/').last() // ได้ "dev", "sit", "uat"

          env.DEPLOY_ENV = envFromJobName
          echo "🎯 Environment (from job name): ${env.DEPLOY_ENV}"

          // 2. Map environment กับ branch (default)
          def defaultBranchMap = [
            'dev': 'develop',
            'sit': 'sit',
            'uat': 'uat',
            'main': 'main'
          ]
          def expectedBranch = defaultBranchMap[env.DEPLOY_ENV] ?: 'develop'

          // 3. ดึง repo และ branch จาก GitHub Webhook
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

          // 4. Final resolution
          env.TARGET_BRANCH = branchFromWebhook ?: params.TARGET_BRANCH ?: expectedBranch

          // 5. Validation
          if (!env.PRODUCT_REPO) {
            error("❌ Cannot determine repository. Please trigger via webhook or set PRODUCT_REPO parameter")
          }

          // 6. Branch validation (optional - เช็คว่า push มาถูก branch ไหม)
          if (branchFromWebhook && branchFromWebhook != expectedBranch) {
            echo "⚠️  WARNING: Branch mismatch!"
            echo "   Expected: ${expectedBranch} (for ${env.DEPLOY_ENV})"
            echo "   Received: ${branchFromWebhook}"
            echo "   Proceeding with: ${branchFromWebhook}"
          }

          // 7. Summary
          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
          echo "📦 Repository:   ${env.PRODUCT_REPO}"
          echo "🌿 Branch:       ${env.TARGET_BRANCH}"
          echo "🎯 Environment:  ${env.DEPLOY_ENV}"
          echo "🏗️  Job:          ${env.JOB_NAME}"
          echo "🔢 Build:        #${env.BUILD_NUMBER}"
          echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
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
