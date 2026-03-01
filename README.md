# My Bank App

![Java](https://img.shields.io/badge/Java-17-informational?logo=java)
![Postgres](https://img.shields.io/badge/PostgreSQL-17-informational?logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-compose-blue?logo=docker)
![Kafka](https://img.shields.io/badge/Kafka-blue?logo=kafka)

## О проекте
my-bank-app-12 — это современное банковское приложение, 
построенное на микросервисной архитектуре с использованием реактивного стека (Spring WebFlux). 
Система обеспечивает полный цикл управления личными финансами в безопасной среде.
Обмен сообщениями осуществляется через Kafka.

## Основные возможности:
* Управление аккаунтом: Редактирование персональных данных (ФИО, дата рождения) с валидацией возраста (18+).
* Операции с наличностью (Cash): Виртуальное внесение и снятие средств с проверкой баланса.
* Денежные переводы (Transfer): Переводы между счетами пользователей внутри системы.
* Уведомления: Логирование и оповещение о каждой успешной транзакции через Kafka.
* Security: Полная защита API с помощью Keycloak (OAuth 2.0 / OpenID Connect).

## Архитектура и структура проекта
Проект разворачивается в кластере Kubernetes (Minikube) и управляется с помощью Helm.

---

## Структура проекта

```declarative;
my-bank-app/
├── account/
│   ├── main/                 # Application - @SpringBootApplication -jar для сервиса работы со счетом
│   │ └── db/changelog/       # Миграции Liquibase
│   ├── integrationtests/     # Интеграционные и контрактные тесты (Postgres)
│   └── Dockerfile            # Конфигурация Docker для account
├── cash/
│   ├── main/                 # Application - @SpringBootApplication -jar для сервиса пополнения снятия со счета
│   │ └── db/changelog/       # Миграции Liquibase
│   ├── integrationtests/     # Интеграционные и контрактные тесты (Postgres)
│   └── Dockerfile            # Конфигурация Docker для cash
├── documentation/            # Документация по модулям 
├── front-ui/
│   ├── main/                 # Application - @SpringBootApplication -jar для сервиса фронта
│   ├── integrationtests/     # Интеграционные и контрактные тесты (Postgres)
│   └── Dockerfile            # Конфигурация Docker для front-ui
├── gateway/
│   ├── main/                 # Application - @SpringBootApplication -jar для сервиса гейтвея с фронта
│   ├── integrationtests/     # Интеграционные и контрактные тесты (Postgres)
│   └── Dockerfile            # Конфигурация Docker для gateway
├── helm/                     # Чарты Helm для разворачивания в K8s
├── microservice-chases/
├── notification/
│   ├── main/                 # Application - @SpringBootApplication -jar для сервиса уведомлений
│   │ └── db/changelog/       # Миграции Liquibase
│   ├── integrationtests/     # Интеграционные тесты (Postgres)
│   └── Dockerfile            # Конфигурация Docker для notification
├── transfer/
│   ├── main/                 # Application - @SpringBootApplication -jar для сервиса переводов
│   │ └── db/changelog/       # Миграции Liquibase
│   ├── integrationtests/     # Интеграционные и контрактные тесты (Postgres)
│   └── Dockerfile            # Конфигурация Docker для transfer
├── deploy.ps1                # Главный файл разворачивания сервиса
├── README.md
└── settings.gradle
```

---

## Применяемые технологии
- **Backend**: Java 17, Spring Boot, WebFlux, Project Reactor.
- **Database**: PostgreSQL (отдельные БД для каждого сервиса), Liquibase.
- **Infrastructure**: Kubernetes, Helm, Docker.
- **Security**: Keycloak (RBAC, OAuth2).
- **Notification**: Kafka.
- **Testing**: JUnit 5, StepVerifier (для реактивных потоков), Testcontainers.

## Быстрый старт
Быстрый старт
1. Подготовка окружения
```bash
   git clone -b feature/module_two_sprint_eight_branch https://github.com/Habatoo/my-bank-app-12.git
   cd my-bank-app-12
```

2. Автоматический деплой (Win)
<br>
Универсальный скрипт для развертывания - соберет образы, настроит Minikube и запустит Helm-чарты в правильном порядке.
```bash

# Установка политики выполнения (единоразово)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process

# Чистый запуск в DEV среде (удалит старый Minikube и создаст новый)
./deploy.ps1 -Environment dev -CleanStart

# Обновление существующей среды без удаления кластера
./deploy.ps1 -Environment dev

# Запуск TEST среды
./deploy.ps1 -Environment test -CleanStart

# Запуск PROD среды
./deploy.ps1 -Environment prod
```
3. Автоматический деплой (Linux)
<br>
Универсальный скрипт для развертывания - соберет образы, настроит Minikube и запустит Helm-чарты в правильном порядке.
```bash

chmod +x deploy.sh

# Чистый запуск в DEV среде (удалит старый Minikube и создаст новый)
./deploy.sh dev --clean

# Обновление существующей среды без удаления кластера
./deploy.sh dev
```
4. Ручное управление (Для отладки)
<br>
- Проброс портов для БД: kubectl port-forward svc/bank-dev-account-db 5432:5432 -n dev
- Доступ к Keycloak: kubectl port-forward svc/keycloak 8080:8080 -n dev
- Логи сервиса: kubectl logs -l app=gateway -n dev
  
5. UI Kafka
- `kubectl get svc -n dev` ->  `kubectl port-forward -n dev svc/kafka-ui-service 8888:8080`.
- http://localhost:8888
- Перейдите в раздел Topics.
- Выберите топик (например, system-alerts из вашего конфига).
- Нажмите вкладку Messages. Вы увидите поток данных в реальном времени

6. Zipkin
- `kubectl get svc -n dev` ->  `kubectl port-forward -n dev svc/zipkin 9411:9411`.
- http://localhost:9411
- Запустить `RUN QUERRY`.

7. Prometheus
- `kubectl get svc -n dev` ->  `kubectl port-forward -n dev svc/zipkin 9411:9411`.
- http://localhost:9411
- Запустить `RUN QUERRY`.

8. Grafana
- `kubectl get svc -n dev` ->  `kubectl port-forward -n dev svc/zipkin 9411:9411`.
- http://localhost:9411
- Запустить `RUN QUERRY`.

9.  Logstash, Elasticsearch и Kibana
- `kubectl get svc -n dev` ->  `kubectl port-forward -n dev svc/zipkin 9411:9411`.
- Запустить `Kibana`.
- http://localhost:5601
- Просмотр Data View, визуализации и дашборды для логов приложения.

## Тесты
Из корневой директории - запуск линтера и тестов Helm:
```bash
# Проверка синтаксиса чартов
helm lint ./helm/my-bank -f ./helm/my-bank/values.yaml
```
```bash
# Запуск интеграционных тестов после деплоя
helm test bank-dev -n dev --logs
```

## Доступ к приложению
- Front UI: http://my-bank (user/user, customer/customer)
- Keycloak: http://keycloak/admin/master/console/ (Admin: admin/admin)

## Модули приложения
Рестарт только модулей:
```bash

kubectl rollout restart deployment gateway notification front-ui account cash transfer -n dev  
```
Подробную информацию о каждом модуле, его API и специфичных настройках вы найдете в соответствующих разделах:
<br><br>
📦 Модуль Accounts — Хранение данных пользователей и балансов. [Документация модуля account](./account/README.md)
<br>
💸 Модуль Cash — Логика ввода/вывода средств. [Документация модуля cash](./cash/README.md)
<br>
🔄 Модуль Transfer — Проведение транзакций между счетами. [Документация модуля transfer](./transfer/README.md)
<br>
🔔 Модуль Notifications — Система оповещений. [Документация модуля notification](./notification/README.md)
<br>
🌐 Модуль Gateway — Единая точка входа и безопасность. [Документация модуля gateway](./gateway/README.md)
<br>
💻 Модуль Front UI — Пользовательский интерфейс. [Документация модуля front-ui](./front-ui/README.md)
<hr>


minikube stop
minikube delete

minikube start --memory=8192 --cpus=4 --driver=docker
kubectl get nodes

minikube addons enable storage-provisioner
minikube addons enable default-storageclass

kubectl create namespace dev

helm upgrade --install bank-dev ./helm/my-bank -n dev -f ./helm/my-bank/values.yaml -f ./helm/my-bank/values-dev.yaml --wait --timeout 15m

helm upgrade --install bank-dev ./helm/my-bank -n dev -f ./helm/my-bank/values.yaml -f ./helm/my-bank/values-dev.yaml --wait --timeout 20m

