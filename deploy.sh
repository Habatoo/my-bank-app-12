#!/usr/bin/env bash

set -e

if [[ -z "$1" ]]; then
  echo "Usage: ./deploy.sh <dev|test|prod> [--clean]"
  exit 1
fi

ENVIRONMENT=$1
CLEAN_START=false

if [[ "$2" == "--clean" ]]; then
  CLEAN_START=true
fi

if [[ "$ENVIRONMENT" != "dev" && "$ENVIRONMENT" != "test" && "$ENVIRONMENT" != "prod" ]]; then
  echo "Environment must be one of: dev | test | prod"
  exit 1
fi

RELEASE_NAME="bank-$ENVIRONMENT"
NAMESPACE="$ENVIRONMENT"
HELM_PATH="./helm/my-bank"

run_deploy_step() {

  DB=$1
  KC=$2
  SVC=$3
  MON=$4
  ELK=$5

  helm upgrade --install "$RELEASE_NAME" "$HELM_PATH" -n "$NAMESPACE" \
    -f "$HELM_PATH/values.yaml" \
    -f "$HELM_PATH/values-$ENVIRONMENT.yaml" \
    --set global.deployDatabases="$DB" \
    --set global.deployKeycloak="$KC" \
    --set global.deployServices="$SVC" \
    --set global.deployKafka=true \
    --set global.deployELK="$ELK" \
    --set kafka.enabled=true \
    --set elk.enabled="$ELK" \
    --set monitoring.enabled="$MON" \
    --set account.image.pullPolicy=Never \
    --set cash.image.pullPolicy=Never \
    --set front-ui.image.pullPolicy=Never \
    --set gateway.image.pullPolicy=Never \
    --set notification.image.pullPolicy=Never \
    --set transfer.image.pullPolicy=Never \
    --wait
}

echo "Action: Building JAR files with Gradle..."
./gradlew clean assemble

if [ "$CLEAN_START" = true ]; then
  echo "Action: Recreating Minikube..."
  minikube delete || true
  minikube start --driver=docker --memory=8g --cpus=4
  minikube addons enable ingress
fi

if ! kubectl get ns "$NAMESPACE" >/dev/null 2>&1; then
    kubectl create namespace "$NAMESPACE"
fi

eval "$(minikube docker-env)"

echo "Step 1: Deploying Databases..."
run_deploy_step true false false false false
sleep 10

echo "Step 2: Deploying Keycloak..."
run_deploy_step true true false false false

echo "Step 3: Deploying Kafka..."
run_deploy_step true true false false false

echo "Step 4: Deploying ELK (Elasticsearch + Logstash + Kibana)..."
run_deploy_step true true false false true

echo "Action: Building Docker images..."

SERVICES=("account" "cash" "front-ui" "gateway" "notification" "transfer")

for svc in "${SERVICES[@]}"
do
  echo "Building image for $svc..."
  minikube image build -t "${svc}:latest" "./$svc"
done

echo "Step 5: Deploying Services..."
run_deploy_step true true true false true

echo "Step 6: Deploying Monitoring..."
run_deploy_step true true true true true

echo "Deployment completed."
kubectl get pods -n "$NAMESPACE"