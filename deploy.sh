#!/bin/bash

set -e

usage() {
    echo "Использование: $0 --env {dev|test|prod} [--clean]"
    exit 1
}

ENVIRONMENT=""
CLEAN_START=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --env) ENVIRONMENT="$2"; shift ;;
        --clean) CLEAN_START=true ;;
        *) usage ;;
    esac
    shift
done

if [[ ! "$ENVIRONMENT" =~ ^(dev|test|prod)$ ]]; then
    echo "Ошибка: Укажите корректный --env (dev, test или prod)"
    usage
fi

RELEASE_NAME="bank-$ENVIRONMENT"
NAMESPACE="$ENVIRONMENT"
HELM_PATH="./helm/my-bank"

run_deploy_step() {
    local deploy_db=$1
    local deploy_kc=$2
    local deploy_svc=$3

    echo "Выполнение Helm Upgrade: DB=$deploy_db, KC=$deploy_kc, SVC=$deploy_svc"

    helm upgrade --install "$RELEASE_NAME" "$HELM_PATH" -n "$NAMESPACE" \
        -f "$HELM_PATH/values.yaml" \
        -f "$HELM_PATH/values-$ENVIRONMENT.yaml" \
        --set global.deployDatabases="$deploy_db" \
        --set global.deployKeycloak="$deploy_kc" \
        --set global.deployServices="$deploy_svc" \
        --set account.image.pullPolicy=Never \
        --set cash.image.pullPolicy=Never \
        --set front-ui.image.pullPolicy=Never \
        --set gateway.image.pullPolicy=Never \
        --set notification.image.pullPolicy=Never \
        --set transfer.image.pullPolicy=Never \
        --set kafka.enabled=true \
        --set global.deployKafka=true
}

echo ">>> Action: Building JAR files with Gradle..."
./gradlew clean assemble

if [ "$CLEAN_START" = true ]; then
    echo ">>> Action: Recreating Minikube..."
    minikube delete
    minikube start --driver=docker --memory=8g --cpus=4
    minikube addons enable ingress
fi

if ! kubectl get ns "$NAMESPACE" >/dev/null 2>&1; then
    kubectl create namespace "$NAMESPACE"
fi

echo ">>> Action: Switching Docker env to Minikube..."
eval $(minikube -p minikube docker-env)

echo ">>> Step 1: Deploying Databases..."
run_deploy_step "true" "false" "false"
sleep 10

echo ">>> Step 2: Deploying Keycloak..."
run_deploy_step "true" "true" "false"

echo ">>> Action: Building Docker images..."
SERVICES=("account" "cash" "front-ui" "gateway" "notification" "transfer")
for svc in "${SERVICES[@]}"; do
    echo "Building image for $svc..."
    docker build -t "${svc}:latest" "./$svc"
done

echo ">>> Step 3: Deploying Services..."
run_deploy_step "true" "true" "true"

echo ">>> Status: Deployment completed"
kubectl get pods -n "$NAMESPACE"