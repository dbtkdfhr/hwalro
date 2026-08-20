pipeline {
    agent any

    options {
        timeout(time: 1, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        REGISTRY_URL = "${env.REGISTRY_URL ?: ''}"
        AWS_REGION = "${env.AWS_REGION ?: 'ap-northeast-2'}"
        EC2_INSTANCE_ID = "${env.EC2_INSTANCE_ID ?: ''}"
        S3_BUCKET = "${env.S3_BUCKET ?: ''}"
        CLOUDFRONT_DISTRIBUTION_ID = "${env.CLOUDFRONT_DISTRIBUTION_ID ?: ''}"
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    def isMain = env.BRANCH_NAME == 'main' || env.GIT_BRANCH == 'origin/main' || env.GIT_BRANCH == 'main'
                    if (isMain) {
                        env.CHANGE_FRONTEND = 'true'
                        env.CHANGE_AUTH = 'true'
                        env.CHANGE_REGULATION = 'true'
                        env.CHANGE_SIMULATION = 'true'
                    } else {
                        def diffOutput = sh(script: 'git diff --name-only origin/main...HEAD || git diff --name-only HEAD~1', returnStdout: true).trim()
                        env.CHANGE_FRONTEND = diffOutput.contains('apps/frontend') || diffOutput.contains('package.json') || diffOutput.contains('pnpm-lock.yaml') ? 'true' : 'false'
                        env.CHANGE_AUTH = diffOutput.contains('apps/auth-service') ? 'true' : 'false'
                        env.CHANGE_REGULATION = diffOutput.contains('apps/regulation-service') ? 'true' : 'false'
                        env.CHANGE_SIMULATION = diffOutput.contains('apps/simulation-service') ? 'true' : 'false'
                    }
                    echo "Changed Services - Frontend: ${env.CHANGE_FRONTEND}, Auth: ${env.CHANGE_AUTH}, Regulation: ${env.CHANGE_REGULATION}, Simulation: ${env.CHANGE_SIMULATION}"
                }
            }
        }

        stage('Frontend CI') {
            when {
                expression { return env.CHANGE_FRONTEND == 'true' }
            }
            steps {
                sh 'pnpm install --frozen-lockfile'
                sh 'pnpm --filter @hwalro/frontend lint'
                sh 'pnpm --filter @hwalro/frontend build'
            }
        }

        stage('Auth Service CI') {
            when {
                expression { return env.CHANGE_AUTH == 'true' }
            }
            steps {
                dir('apps/auth-service') {
                    sh './gradlew --no-daemon spotlessCheck test'
                }
            }
        }

        stage('Regulation Service CI') {
            when {
                expression { return env.CHANGE_REGULATION == 'true' }
            }
            steps {
                dir('apps/regulation-service') {
                    sh './gradlew --no-daemon spotlessCheck test'
                }
            }
        }

        stage('Simulation Service CI') {
            when {
                expression { return env.CHANGE_SIMULATION == 'true' }
            }
            steps {
                dir('apps/simulation-service') {
                    sh './gradlew --no-daemon spotlessCheck test'
                }
            }
        }

        stage('CD: Validate AWS Configuration') {
            when {
                anyOf {
                    branch 'main'
                    expression { return env.GIT_BRANCH == 'origin/main' || env.GIT_BRANCH == 'main' }
                }
            }
            steps {
                script {
                    if (!env.REGISTRY_URL?.trim()) {
                        error 'Missing required Jenkins environment variable: REGISTRY_URL'
                    }
                    if (!env.EC2_INSTANCE_ID?.trim()) {
                        error 'Missing required Jenkins environment variable: EC2_INSTANCE_ID'
                    }
                    if (!env.S3_BUCKET?.trim()) {
                        error 'Missing required Jenkins environment variable: S3_BUCKET'
                    }
                    if (!env.CLOUDFRONT_DISTRIBUTION_ID?.trim()) {
                        error 'Missing required Jenkins environment variable: CLOUDFRONT_DISTRIBUTION_ID'
                    }
                }
                sh 'aws sts get-caller-identity --no-cli-pager'
            }
        }

        stage('CD: Login to ECR') {
            when {
                anyOf {
                    branch 'main'
                    expression { return env.GIT_BRANCH == 'origin/main' || env.GIT_BRANCH == 'main' }
                }
            }
            steps {
                sh '''
                    REGISTRY_DOMAIN="${REGISTRY_URL%%/*}"
                    aws ecr get-login-password --region "$AWS_REGION" \
                        | docker login --username AWS --password-stdin "$REGISTRY_DOMAIN"
                '''
            }
        }

        stage('CD: Build & Push Auth Service') {
            when {
                anyOf {
                    branch 'main'
                    expression { return env.GIT_BRANCH == 'origin/main' || env.GIT_BRANCH == 'main' }
                }
            }
            steps {
                sh '''
                    docker build \
                        -t "$REGISTRY_URL/auth-service:$IMAGE_TAG" \
                        -t "$REGISTRY_URL/auth-service:latest" \
                        apps/auth-service
                    docker push "$REGISTRY_URL/auth-service:$IMAGE_TAG"
                    docker push "$REGISTRY_URL/auth-service:latest"
                '''
            }
        }

        stage('CD: Build & Push Regulation Service') {
            when {
                anyOf {
                    branch 'main'
                    expression { return env.GIT_BRANCH == 'origin/main' || env.GIT_BRANCH == 'main' }
                }
            }
            steps {
                sh '''
                    docker build \
                        -t "$REGISTRY_URL/regulation-service:$IMAGE_TAG" \
                        -t "$REGISTRY_URL/regulation-service:latest" \
                        apps/regulation-service
                    docker push "$REGISTRY_URL/regulation-service:$IMAGE_TAG"
                    docker push "$REGISTRY_URL/regulation-service:latest"
                '''
            }
        }

        stage('CD: Build & Push Simulation Service') {
            when {
                anyOf {
                    branch 'main'
                    expression { return env.GIT_BRANCH == 'origin/main' || env.GIT_BRANCH == 'main' }
                }
            }
            steps {
                sh '''
                    docker build \
                        -t "$REGISTRY_URL/simulation-service:$IMAGE_TAG" \
                        -t "$REGISTRY_URL/simulation-service:latest" \
                        apps/simulation-service
                    docker push "$REGISTRY_URL/simulation-service:$IMAGE_TAG"
                    docker push "$REGISTRY_URL/simulation-service:latest"
                '''
            }
        }

        stage('CD: Deploy Frontend') {
            when {
                anyOf {
                    branch 'main'
                    expression { return env.GIT_BRANCH == 'origin/main' || env.GIT_BRANCH == 'main' }
                }
            }
            steps {
                sh '''
                    aws s3 sync apps/frontend/dist/assets/ "s3://$S3_BUCKET/assets/" \
                        --delete \
                        --cache-control "public,max-age=31536000,immutable" \
                        --no-progress

                    aws s3 sync apps/frontend/dist/ "s3://$S3_BUCKET/" \
                        --delete \
                        --exclude "assets/*" \
                        --cache-control "no-cache,no-store,must-revalidate" \
                        --no-progress

                    aws cloudfront create-invalidation \
                        --distribution-id "$CLOUDFRONT_DISTRIBUTION_ID" \
                        --paths "/*" \
                        --no-cli-pager
                '''
            }
        }

        stage('CD: Deploy Backend with SSM') {
            when {
                anyOf {
                    branch 'main'
                    expression { return env.GIT_BRANCH == 'origin/main' || env.GIT_BRANCH == 'main' }
                }
            }
            steps {
                script {
                    def composeBase64 = readFile(file: 'deploy/docker-compose.prod.yml', encoding: 'Base64').trim()
                    def nginxBase64 = readFile(file: 'deploy/nginx/api-gateway.conf.template', encoding: 'Base64').trim()
                    def commands = [
                        'set -eu',
                        'install -d -m 0755 /opt/hwalro/deploy/nginx',
                        "printf '%s' '${composeBase64}' | base64 -d > /opt/hwalro/deploy/docker-compose.prod.yml.tmp",
                        "printf '%s' '${nginxBase64}' | base64 -d > /opt/hwalro/deploy/nginx/api-gateway.conf.template.tmp",
                        'test -s /opt/hwalro/deploy/docker-compose.prod.yml.tmp',
                        'test -s /opt/hwalro/deploy/nginx/api-gateway.conf.template.tmp',
                        'mv /opt/hwalro/deploy/docker-compose.prod.yml.tmp /opt/hwalro/deploy/docker-compose.prod.yml',
                        'mv /opt/hwalro/deploy/nginx/api-gateway.conf.template.tmp /opt/hwalro/deploy/nginx/api-gateway.conf.template',
                        'test -f /opt/hwalro/deploy/.env.prod',
                        "export REGISTRY_URL='${env.REGISTRY_URL}'",
                        "export AWS_REGION='${env.AWS_REGION}'",
                        'export AUTH_TAG=latest SIMULATION_TAG=latest REGULATION_TAG=latest',
                        'REGISTRY_DOMAIN=$(printf "%s" "$REGISTRY_URL" | cut -d/ -f1)',
                        'aws ecr get-login-password --region "${AWS_REGION}" | docker login --username AWS --password-stdin "$REGISTRY_DOMAIN"',
                        'docker compose --env-file /opt/hwalro/deploy/.env.prod -f /opt/hwalro/deploy/docker-compose.prod.yml config --quiet',
                        'docker compose --env-file /opt/hwalro/deploy/.env.prod -f /opt/hwalro/deploy/docker-compose.prod.yml pull',
                        'docker compose --env-file /opt/hwalro/deploy/.env.prod -f /opt/hwalro/deploy/docker-compose.prod.yml up -d --remove-orphans',
                        'sleep 10',
                        'test "$(docker compose --env-file /opt/hwalro/deploy/.env.prod -f /opt/hwalro/deploy/docker-compose.prod.yml ps --status running --services | wc -l)" -eq 5',
                        'docker compose --env-file /opt/hwalro/deploy/.env.prod -f /opt/hwalro/deploy/docker-compose.prod.yml ps',
                        'docker image prune -f'
                    ]

                    writeFile(
                        file: 'ssm-deploy-parameters.json',
                        text: groovy.json.JsonOutput.toJson([commands: commands])
                    )

                    def commandId = sh(
                        script: '''
                            aws ssm send-command \
                                --region "$AWS_REGION" \
                                --instance-ids "$EC2_INSTANCE_ID" \
                                --document-name "AWS-RunShellScript" \
                                --comment "Deploy Hwalro build $BUILD_NUMBER" \
                                --parameters file://ssm-deploy-parameters.json \
                                --query 'Command.CommandId' \
                                --output text \
                                --no-cli-pager
                        ''',
                        returnStdout: true
                    ).trim()

                    echo "SSM command ID: ${commandId}"
                    sleep(time: 5, unit: 'SECONDS')

                    def deploymentStatus = 'Pending'
                    timeout(time: 15, unit: 'MINUTES') {
                        waitUntil(initialRecurrencePeriod: 5000) {
                            deploymentStatus = sh(
                                script: "aws ssm get-command-invocation --region '${env.AWS_REGION}' --command-id '${commandId}' --instance-id '${env.EC2_INSTANCE_ID}' --query Status --output text --no-cli-pager",
                                returnStdout: true
                            ).trim()
                            echo "SSM deployment status: ${deploymentStatus}"
                            return ['Success', 'Cancelled', 'Failed', 'TimedOut'].contains(deploymentStatus)
                        }
                    }

                    sh """
                        aws ssm get-command-invocation \
                            --region '${env.AWS_REGION}' \
                            --command-id '${commandId}' \
                            --instance-id '${env.EC2_INSTANCE_ID}' \
                            --query '{Status:Status,ResponseCode:ResponseCode,Output:StandardOutputContent,Error:StandardErrorContent}' \
                            --output json \
                            --no-cli-pager
                    """

                    if (deploymentStatus != 'Success') {
                        error "SSM deployment failed with status ${deploymentStatus}. Command ID: ${commandId}"
                    }
                }
            }
        }
    }

    post {
        always {
            sh 'rm -f ssm-deploy-parameters.json'
        }
        success {
            echo 'CI/CD Pipeline succeeded.'
        }
        failure {
            echo 'CI/CD Pipeline failed.'
        }
    }
}
