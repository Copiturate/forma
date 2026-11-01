# API FORMA

Базовый URL: `http://localhost:8080`. Тела запросов — JSON UTF-8. Статические страницы и API работают с одного origin. Никаких API-ключей в frontend нет.

## CSRF

Для записи нужна сессия с токеном. Пример для bash / curl:

```bash
curl -sS -c cookies.txt http://localhost:8080/api/csrf
# Скопируйте поле token из ответа в CSRF, сохранив cookies.txt.
CSRF='token-from-response'
curl -sS -b cookies.txt -H "X-CSRF-TOKEN: $CSRF" \
  -H 'Content-Type: application/json' \
  -d '{"service":"INTERIOR","area":80}' \
  http://localhost:8080/api/estimates
```

Для PowerShell используйте `curl.exe` и файл тела с `--data-binary @file.json` либо примеры из `requests.http`. Не публикуйте session cookies и пароли. После входа токен меняется — получите `/api/csrf` снова.

## Публичные методы

| Метод | Путь | Результат |
|---|---|---|
| GET | `/api/csrf` | `{headerName, token}` и cookie сессии |
| GET | `/api/services` | Массив направлений: code, title, minRate, maxRate, duration |
| POST | `/api/estimates` | Расчёт по направлению и площади |
| POST | `/api/inquiries` | Сохранение брифа, `201 Created` |

### Расчёт

```json
{"service":"INTERIOR","area":80}
```

Ответ:

```json
{"service":"INTERIOR","area":80,"min":320000,"max":480000,"currency":"RUB","pricingVersion":"2026-01"}
```

Направления: `ARCHITECTURE` (2500–3500 ₽/м²), `INTERIOR` (4000–6000 ₽/м²), `SUPERVISION` (1000–1500 ₽/м² за весь проект). Допустимая площадь — целое число 20–2000. Это демонстрационные тарифы стоимости услуг бюро, без ремонта, мебели и материалов.

### Заявка

```json
{
  "requestId":"cd49bb1b-884a-45e7-beb7-23fe3afbc2b2",
  "name":"Анна",
  "email":"anna@example.com",
  "service":"INTERIOR",
  "area":80,
  "message":"Нужен светлый интерьер с рабочим местом.",
  "consent":true
}
```

Имя: 2–80 символов, минимум два непробельных. Email: валидный адрес до 254 символов. Сообщение: строка до 2000 символов, может быть пустой. Все поля обязательны, `consent` должен быть `true`. Цена вычисляется сервером. Неизвестные поля отклоняются. Идентичный повтор с тем же `requestId` также возвращает `201` и ту же квитанцию, без новой строки в базе.

```json
{
  "id":"cd49bb1b-884a-45e7-beb7-23fe3afbc2b2",
  "createdAt":"2026-11-01T12:00:00Z",
  "estimateMin":320000,
  "estimateMax":480000,
  "currency":"RUB"
}
```

Публичного GET заявки по номеру нет: квитанция не предоставляет доступ к контактам.

## Авторизация команды

Откройте `/studio.html`: неавторизованного посетителя перенаправит на `/login.html`. HTML-форма отправляет `username`, `password`, `_csrf` на `POST /login`. После успешного входа cookie `JSESSIONID` даёт доступ к кабинету. Выход — `POST /logout` с CSRF. HTTP Basic не включён.

Для curl можно выполнить ту же последовательность: GET csrf + cookie → POST login с form-urlencoded полями → GET нового csrf с cookie → запросы API с cookie и токеном. Для ручной проверки проще использовать встроенный кабинет.

## Защищённые методы

| Метод | Путь | Результат |
|---|---|---|
| GET | `/api/admin/inquiries?page=0&status=NEW` | Страница из максимум 12 заявок; status необязателен |
| PATCH | `/api/admin/inquiries/{id}/status` | Обновлённая заявка |

Ответ списка: `{items: [...], page: 0, totalPages: 1, totalElements: 1}`. Каждая запись содержит `id`, `name`, `email`, `service`, `area`, `message`, `estimateMin`, `estimateMax`, `pricingVersion`, `status`, `createdAt`, `updatedAt`, `version`. Время — ISO 8601 UTC. Страницы начинаются с 0; диапазон page 0–100000.

Обновление:

```http
PATCH /api/admin/inquiries/cd49bb1b-884a-45e7-beb7-23fe3afbc2b2/status
Content-Type: application/json
X-CSRF-TOKEN: token-from-current-session

{"status":"REVIEWING","version":0}
```

Разрешённые переходы: NEW → REVIEWING/CLOSED, REVIEWING → CONTACTED/CLOSED, CONTACTED → CLOSED. CLOSED терминальный. Ожидаемая версия обязательна; успешное обновление увеличивает её.

## Ошибки и ограничения

| Код | Причина |
|---|---|
| 400 | Валидация, неизвестный enum/поле, повреждённый JSON, некорректный page |
| 401 | Нет авторизованной сессии для admin API |
| 403 | Нет CSRF-токена / прав доступа |
| 404 | Заявка отсутствует |
| 405 / 415 | Неподходящий HTTP-метод / Content-Type |
| 409 | Конфликт версии, запрещённый переход или UUID с другим содержимым |
| 413 | Тело API-запроса более 16 КиБ |
| 429 | Лимит запросов, заголовок `Retry-After: 60` |
| 500 | Внутренняя ошибка без раскрытия технических деталей |

Контроллеры возвращают `application/problem+json` с `status`, `title`, `detail`, `instance`. Валидация добавляет `errors` — карту полей. У отказов Spring Security тело может отсутствовать; не следует предполагать, что каждый ответ содержит JSON.

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"Проверьте заполнение полей.","errors":{"email":"must be a well-formed email address"}}
```

Лимиты: 60 POST/PATCH API-запросов на адрес в минуту, отдельно 10 POST /login в минуту. Это локальная защита одного процесса. Автоматическая повторная отправка PATCH не выполняется: при конфликте нужно обновить данные.
