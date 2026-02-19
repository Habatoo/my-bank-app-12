<#import "template.ftl" as layout>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${url.resourcesPath}/css/style.css">
    <title>Chassis Bank | Регистрация</title>
</head>
<body>
<div class="auth-card">
    <div class="text-center mb-4">
        <h3 class="fw-bold"><span class="text-primary">CHASSIS</span> BANK</h3>
        <p class="text-muted small">Создание нового аккаунта</p>
    </div>

    <ul class="nav nav-pills nav-fill mb-4">
        <li class="nav-item">
            <a class="nav-link" href="${url.loginUrl}">Вход</a>
        </li>
        <li class="nav-item">
            <a class="nav-link active" href="#">Регистрация</a>
        </li>
    </ul>

    <#if message?exists && message.type == 'error'>
        <div class="alert alert-danger">${message.summary}</div>
    </#if>

    <form action="${url.registrationAction}" method="post">
        <#-- username нужен Keycloak для создания учетной записи -->
        <div class="mb-3">
            <label class="form-label small">Логин (используется для входа)</label>
            <input type="text" class="form-control" name="username" id="username" value="${(register.formData.username!'')}" placeholder="ivanov_ivan" required>
        </div>

        <#--firstName мапим на поле 'name' -->
        <div class="mb-3">
            <label class="form-label small">Полное имя</label>
            <input type="text" class="form-control" name="firstName" id="firstName" value="${(register.formData.firstName!'')}" placeholder="Иванов Иван" required>
        </div>

        <#-- lastName скрываем или используем как заглушку, так как в DTO его нет -->
        <input type="hidden" name="lastName" value="-">

        <div class="mb-3">
            <label class="form-label small">Email</label>
            <input type="email" class="form-control" name="email" id="email" value="${(register.formData.email!'')}" placeholder="example@mail.ru" required>
        </div>

        <#-- Дата рождения как кастомный атрибут -->
        <div class="mb-3">
            <label class="form-label small">Дата рождения</label>
            <input type="date" class="form-control" name="user.attributes.birthdate" id="birthdate" value="${(register.formData['user.attributes.birthdate']!'')}" required>
        </div>

        <div class="mb-3">
            <label class="form-label small">Пароль</label>
            <input type="password" class="form-control" name="password" id="password" placeholder="••••••••" required>
        </div>

        <div class="mb-3">
            <label class="form-label small">Подтвердите пароль</label>
            <input type="password" class="form-control" name="password-confirm" id="password-confirm" placeholder="••••••••" required>
        </div>

        <button type="submit" class="btn btn-primary btn-auth w-100">Создать аккаунт</button>
    </form>
</div>
</body>
</html>