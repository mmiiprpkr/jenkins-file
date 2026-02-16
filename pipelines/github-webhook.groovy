pipeline {
  agent any

  parameters {
    string(name: 'PRODUCT_REPO', defaultValue: '', description: 'org/repo (optional if webhook provides it)')
    string(name: 'TARGET_BRANCH', defaultValue: '', description: 'fallback branch if no webhook')
  }

  stages {
    stage('Resolve Environment & Webhook') {
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
            'prod': 'main'
          ]
          def expectedBranch = defaultBranchMap[env.DEPLOY_ENV] ?: 'develop'

          // 3. ดึง repo และ branch จาก GitHub Webhook
          def repoFromWebhook = ''
          def branchFromWebhook = ''

          // Parse จาก GIT_URL ที่ GitHub webhook ส่งมา
          if (env.GIT_URL) {
            echo "📡 GitHub Webhook detected: ${env.GIT_URL}"

            // Extract repo name (org/repo)
            def matcher = env.GIT_URL =~ /github\.com[\/:](.+?)(\.git)?$/
            if (matcher) {
              repoFromWebhook = matcher[0][1].replaceAll('\\.git$', '')
            }
          }

          // Parse branch
          if (env.GIT_BRANCH) {
            branchFromWebhook = env.GIT_BRANCH
              .replaceAll('origin/', '')
              .replaceAll('refs/heads/', '')
              .replaceAll('refs/tags/', '')
          }

          // 4. Final resolution
          env.PRODUCT_REPO = repoFromWebhook ?: params.PRODUCT_REPO
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

    stage('Checkout Product Code') {
      steps {
        script {
          echo "🔄 Checking out ${env.PRODUCT_REPO}@${env.TARGET_BRANCH}"

          // ลบ existing workspace ก่อน (optional)
          deleteDir()

          checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${env.TARGET_BRANCH}"]],
            userRemoteConfigs: [[
              url: "https://github.com/${env.PRODUCT_REPO}.git",
              credentialsId: 'github-credentials' // ถ้ามี private repo
            ]],
            extensions: [
              [$class: 'CleanBeforeCheckout'],
              [$class: 'CloneOption', depth: 1, shallow: true]
            ]
          ])

          // Verify Dockerfile exists
          if (!fileExists('Dockerfile')) {
            error("❌ Dockerfile not found in ${env.PRODUCT_REPO}")
          }

          echo "✅ Product code checked out successfully"
        }
      }
    }

    stage('Load Environment Config') {
      steps {
        script {
          echo "📋 Loading ${env.DEPLOY_ENV} configuration"

          // Checkout config repo ไปที่ subdirectory
          dir('jenkins-config') {
            checkout([
              $class: 'GitSCM',
              branches: [[name: '*/main']],
              userRemoteConfigs: [[
                url: 'https://github.com/your-org/jenkins-config-repo.git',
                credentialsId: 'github-credentials'
              ]]
            ])
          }

          // Load environment variables
          def configFile = "jenkins-config/config/${env.DEPLOY_ENV}.env"
          if (fileExists(configFile)) {
            def props = readProperties file: configFile
            props.each { key, value ->
              env[key] = value
              echo "   ${key} = ${value}"
            }
            echo "✅ Loaded ${props.size()} variables from ${configFile}"
          } else {
            echo "⚠️  Config file not found: ${configFile}, using defaults"
          }
        }
      }
    }

    stage('Build Docker Image') {
      steps {
        script {
          def repoName = env.PRODUCT_REPO.split('/')[1]
          def imageTag = "${env.DEPLOY_ENV}-${env.BUILD_NUMBER}"
          def commitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()

          echo "🐳 Building Docker image..."
          echo "   Repository: ${repoName}"
          echo "   Tag:        ${imageTag}"
          echo "   Commit:     ${commitHash}"

          sh """
            docker build \
              --build-arg ENV=${env.DEPLOY_ENV} \
              --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} \
              --build-arg COMMIT_HASH=${commitHash} \
              --label "env=${env.DEPLOY_ENV}" \
              --label "repo=${env.PRODUCT_REPO}" \
              --label "branch=${env.TARGET_BRANCH}" \
              -t ${env.DOCKER_REGISTRY}/${repoName}:${imageTag} \
              -t ${env.DOCKER_REGISTRY}/${repoName}:${env.DEPLOY_ENV}-latest \
              .
          """

          env.IMAGE_NAME = "${env.DOCKER_REGISTRY}/${repoName}"
          env.IMAGE_TAG = imageTag

          echo "✅ Image built: ${env.IMAGE_NAME}:${env.IMAGE_TAG}"
        }
      }
    }

    stage('Push Docker Image') {
      steps {
        script {
          echo "📤 Pushing Docker image..."

          sh """
            docker push ${env.IMAGE_NAME}:${env.IMAGE_TAG}
            docker push ${env.IMAGE_NAME}:${env.DEPLOY_ENV}-latest
          """

          echo "✅ Image pushed successfully"
        }
      }
    }

    stage('Deploy') {
      steps {
        script {
          echo "🚀 Deploying to ${env.DEPLOY_ENV} environment"

          // Deploy based on environment
          switch(env.DEPLOY_ENV) {
            case 'dev':
              sh """
                kubectl set image deployment/my-app \
                  my-app=${env.IMAGE_NAME}:${env.IMAGE_TAG} \
                  -n dev
              """
              break

            case 'sit':
              sh """
                kubectl set image deployment/my-app \
                  my-app=${env.IMAGE_NAME}:${env.IMAGE_TAG} \
                  -n sit
              """
              break

            case 'uat':
              // ขอ approval ก่อน deploy UAT
              timeout(time: 10, unit: 'MINUTES') {
                input message: "Deploy to UAT?", ok: 'Deploy'
              }
              sh """
                kubectl set image deployment/my-app \
                  my-app=${env.IMAGE_NAME}:${env.IMAGE_TAG} \
                  -n uat
              """
              break

            case 'prod':
              error("Production deployment should use separate job/process")
              break

            default:
              error("Unknown environment: ${env.DEPLOY_ENV}")
          }

          echo "✅ Deployment completed"
        }
      }
    }

    stage('Verify Deployment') {
      steps {
        script {
          echo "🔍 Verifying deployment..."

          sh """
            kubectl rollout status deployment/my-app -n ${env.DEPLOY_ENV} --timeout=5m
          """

          echo "✅ Deployment verified successfully"
        }
      }
    }
  }

  post {
    success {
      script {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "✅ Pipeline SUCCESS"
        echo "📦 ${env.PRODUCT_REPO}"
        echo "🌿 ${env.TARGET_BRANCH}"
        echo "🎯 ${env.DEPLOY_ENV}"
        echo "🐳 ${env.IMAGE_NAME}:${env.IMAGE_TAG}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
      }
    }

    failure {
      script {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "❌ Pipeline FAILED"
        echo "📦 ${env.PRODUCT_REPO}"
        echo "🌿 ${env.TARGET_BRANCH}"
        echo "🎯 ${env.DEPLOY_ENV}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
      }
    }

    always {
      // Cleanup
      sh 'docker system prune -f --filter "until=24h" || true'
    }
  }
}
