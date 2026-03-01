param (
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev", "test", "prod")]
    [string]$Environment,
    [switch]$CleanStart
)

function Run-DeployStep {
    param(
        $Db,
        $Kc,
        $Svc,
        $Kafka,
        $Monitoring,
        $Elastic,
        $Kibana,
        $Logstash
    )

    $Command = "helm upgrade --install $ReleaseName $HelmPath -n $Namespace " +
               "-f $HelmPath/values.yaml " +
               "-f $HelmPath/values-$Environment.yaml " +
               "--wait --timeout 20m " +
               "--set global.deployDatabases=$Db " +
               "--set global.deployKeycloak=$Kc " +
               "--set global.deployServices=$Svc " +
               "--set global.deployKafka=$Kafka " +
               "--set kafka.enabled=$Kafka " +
               "--set monitoring.enabled=$Monitoring " +
               "--set elasticsearch.enabled=$Elastic " +
               "--set kibana.enabled=$Kibana " +
               "--set logstash.enabled=$Logstash " +
               "--set account.image.pullPolicy=Never " +
               "--set cash.image.pullPolicy=Never " +
               "--set front-ui.image.pullPolicy=Never " +
               "--set gateway.image.pullPolicy=Never " +
               "--set notification.image.pullPolicy=Never " +
               "--set transfer.image.pullPolicy=Never"

    Invoke-Expression $Command
}

$ReleaseName = "bank-$Environment"
$Namespace = $Environment
$HelmPath = "./helm/my-bank"

Write-Host "Action: Building JAR files with Gradle..."
./gradlew clean assemble

if ($CleanStart) {
    Write-Host "Action: Recreating Minikube"
    minikube delete
    minikube start --driver=docker --memory=8192 --cpus=4
    minikube addons enable ingress
    minikube addons enable storage-provisioner
    minikube addons enable default-storageclass
}

if (-not (kubectl get ns $Namespace --ignore-not-found)) {
    kubectl create namespace $Namespace
}

& minikube -p minikube docker-env --shell powershell | Invoke-Expression

Write-Host "Step 1: Deploying Databases"
Run-DeployStep "true" "false" "false" "false" "false" "false" "false" "false"

Write-Host "Step 2: Deploying Keycloak"
Run-DeployStep "true" "true" "false" "false" "false" "false" "false" "false"

Write-Host "Step 3: Deploying Kafka"
Run-DeployStep "true" "true" "false" "true" "false" "false" "false" "false"

Write-Host "Action: Building Docker images"
$services = @("account", "cash", "front-ui", "gateway", "notification", "transfer")

foreach ($svc in $services) {
    Write-Host "Building image for $svc..."
    docker build -t "${svc}:latest" "./$svc"
}

Write-Host "Step 4: Deploying Services"
Run-DeployStep "true" "true" "true" "true" "false" "false" "false" "false"

Write-Host "Step 5: Deploying Elasticsearch"
Run-DeployStep "true" "true" "true" "true" "false" "true" "false" "false"

Write-Host "Step 6: Deploying Kibana + Logstash"
Run-DeployStep "true" "true" "true" "true" "false" "true" "true" "true"

Write-Host "Step 7: Deploying Monitoring (Prometheus + Grafana)"
Run-DeployStep "true" "true" "true" "true" "true" "true" "true" "true"

Write-Host "Status: Deployment completed"
kubectl get pods -n $Namespace