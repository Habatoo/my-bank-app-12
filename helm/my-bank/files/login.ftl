<#import "template.ftl" as layout>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${url.resourcesPath}/css/style.css">
    <title>Chassis Bank | Вход</title>
</head>
<body>
<div class="auth-card">
    <div class="text-center mb-4">
        <h3 class="fw-bold"><span class="text-primary">CHASSIS</span> BANK</h3>
        <p class="text-muted small">Добро пожаловать в безопасный банкинг</p>
    </div>

    <ul class="nav nav-pills nav-fill mb-4">
        <li class="nav-item">
            <a class="nav-link active" href="#">Вход</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" href="${url.registrationUrl}">Регистрация</a>
        </li>
    </ul>

    <#-- Сообщения об ошибках от Keycloak -->
    <#if message?exists && message.type == 'error'>
        <div class="alert alert-error">${message.summary}</div>
    </#if>

    <form action="${url.loginAction}" method="post">
        <div class="mb-3">
            <label class="form-label small">Логин</label>
            <input type="text" class="form-control" name="username" id="username" autofocus autocomplete="off">
        </div>
        <div class="mb-4">
            <label class="form-label small">Пароль</label>
            <input type="password" class="form-control" name="password" id="password" autocomplete="off">
        </div>
        <button type="submit" class="btn btn-primary btn-auth">Войти в кабинет</button>
    </form>
</div>
</body>
</html>