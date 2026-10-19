# GameHub Redis

## Демонстрация

**Видео и материалы демонстрации:**  
https://drive.google.com/drive/folders/1DzfaBZcRNta6odQlAbgbpFZX-NSRhXfd?usp=share_link

---

## О проекте

**GameHub Redis** — учебное приложение на Java и Spring Boot, демонстрирующее практическое использование Redis в качестве высокопроизводительного хранилища данных.

В проекте реализованы:

- хранение профилей игроков;
- кеширование данных с TTL;
- счётчик входов;
- лидерборд;
- хранение достижений;
- Redis Streams для обработки уведомлений;
- массовая запись данных через Pipeline;
- репликация Redis;
- автоматическое переключение master через Redis Sentinel;
- защита от записи при потере достаточного количества реплик.

---

## Стек технологий

- Java 25
- Spring Boot 3.5.6
- Spring Data Redis
- Lettuce
- Redis 7.4
- Docker Compose
- Swagger / OpenAPI

---

## Используемые структуры Redis

| Назначение | Ключ | Тип Redis |
|---|---|---|
| Профиль игрока | `player:{id}` | Hash |
| Счётчик входов | `logins:{id}` | String |
| Кеш профиля | `cache:player:{id}` | String (JSON) |
| Лидерборд | `tournament:main` | Sorted Set |
| Достижения | `achievements:{id}` | Set |
| Очередь уведомлений | `notifications` | Stream |

### TTL

- `logins:{id}` — 24 часа;
- `cache:player:{id}` — 60 секунд.

---

# Запуск проекта

Если ранее запускался одиночный контейнер `gamehub-redis`, удалить его:

```bash
docker rm -f gamehub-redis
```

Собрать приложение:

```bash
mvn clean package
```

Запустить всю систему:

```bash
docker compose up --build -d
```

Проверить состояние контейнеров:

```bash
docker compose ps
```

После запуска должны работать:

- `redis-master`;
- `redis-replica-1`;
- `redis-replica-2`;
- `sentinel-1`;
- `sentinel-2`;
- `sentinel-3`;
- `gamehub-app`;
- `redis-insight`.

Проверить репликацию:

```bash
docker exec redis-master redis-cli INFO replication
```

В выводе ожидается:

```text
role:master
connected_slaves:2
```

---

## Swagger UI

API приложения доступен по адресу:

```text
http://localhost:8080/swagger-ui.html
```

---

## Redis Insight

Redis Insight доступен по адресу:

```text
http://localhost:5540
```

Для подключения к Redis:

```text
Host: redis-master
Port: 6379
```

Логин и пароль не требуются.

---

# API

## Профили игроков

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/players/{id}` | Создание профиля |
| `GET` | `/api/players/{id}` | Получение профиля |
| `PATCH` | `/api/players/{id}/level` | Изменение уровня |
| `POST` | `/api/players/{id}/login` | Увеличение счётчика входов |
| `POST` | `/api/players/batch` | Массовое создание профилей |

## Лидерборд

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/leaderboard/score` | Добавление очков |
| `GET` | `/api/leaderboard/top?limit=10` | Получение топа игроков |
| `GET` | `/api/leaderboard/rank/{playerId}` | Получение места игрока |

## Достижения

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/players/{id}/achievements` | Добавление достижения |
| `GET` | `/api/players/{id}/achievements/{name}` | Проверка наличия достижения |
| `GET` | `/api/players/{id1}/achievements/common/{id2}` | Общие достижения двух игроков |

---

# Демонстрация функциональности

## 1. Создание профиля

Создать игрока:

```text
POST /api/players/1001
```

Request body:

```json
{
  "name": "Alice",
  "level": 5,
  "region": "EU",
  "createdAt": "2026-10-05T20:00:00Z"
}
```

Получить профиль:

```text
GET /api/players/1001
```

Проверить данные непосредственно в Redis:

```bash
docker exec redis-master redis-cli HGETALL player:1001
```

---

## 2. Изменение уровня

Выполнить:

```text
PATCH /api/players/1001/level
```

Request body:

```json
{
  "delta": 3
}
```

После этого снова выполнить:

```text
GET /api/players/1001
```

Значение `level` должно увеличиться на `3`.

---

## 3. Лидерборд

Добавить очки:

```text
POST /api/leaderboard/score
```

Request body:

```json
{
  "playerId": "1001",
  "score": 100
}
```

Получить топ-10 игроков:

```text
GET /api/leaderboard/top?limit=10
```

Получить место игрока:

```text
GET /api/leaderboard/rank/1001
```

Проверить Sorted Set:

```bash
docker exec redis-master redis-cli ZREVRANGE tournament:main 0 9 WITHSCORES
```

---

## 4. Достижения

Добавить достижение игроку:

```text
POST /api/players/1001/achievements
```

```json
{
  "name": "FIRST_WIN"
}
```

Проверить наличие достижения:

```text
GET /api/players/1001/achievements/FIRST_WIN
```

Создать второго игрока:

```text
POST /api/players/1002
```

```json
{
  "name": "Bob",
  "level": 3,
  "region": "EU",
  "createdAt": "2026-10-05T20:00:00Z"
}
```

Добавить ему такое же достижение:

```text
POST /api/players/1002/achievements
```

```json
{
  "name": "FIRST_WIN"
}
```

Проверить общие достижения:

```text
GET /api/players/1001/achievements/common/1002
```

Ожидаемый результат:

```text
FIRST_WIN
```

---

## 5. Кеширование

Удалить существующий кеш:

```bash
docker exec redis-master redis-cli DEL cache:player:1001
```

Выполнить первый запрос:

```text
GET /api/players/1001
```

Проверить логи:

```bash
docker logs gamehub-app --tail 50
```

Ожидается:

```text
CACHE MISS: cache:player:1001
```

Проверить TTL:

```bash
docker exec redis-master redis-cli TTL cache:player:1001
```

TTL должен быть больше `0` и не превышать `60` секунд.

Сразу выполнить второй запрос:

```text
GET /api/players/1001
```

Снова проверить логи:

```bash
docker logs gamehub-app --tail 50
```

Ожидается:

```text
CACHE HIT: cache:player:1001
```

Таким образом демонстрируется полный цикл кеширования:

1. первый запрос — `CACHE MISS`;
2. данные читаются из Hash;
3. профиль помещается в кеш;
4. кеш получает TTL 60 секунд;
5. второй запрос — `CACHE HIT`.

---

## 6. Счётчик входов

Выполнить:

```text
POST /api/players/1001/login
```

Проверить значение:

```bash
docker exec redis-master redis-cli GET logins:1001
```

После первого вызова ожидается:

```text
1
```

Проверить TTL:

```bash
docker exec redis-master redis-cli TTL logins:1001
```

TTL должен быть положительным и близким к:

```text
86400
```

Повторный вызов:

```text
POST /api/players/1001/login
```

должен увеличить значение счётчика.

Операции `INCR` и установки TTL выполняются атомарно с использованием Lua-скрипта.

---

## 7. Redis Streams

Изменить уровень игрока:

```text
PATCH /api/players/1001/level
```

```json
{
  "delta": 1
}
```

Проверить наличие сообщений:

```bash
docker exec redis-master redis-cli XLEN notifications
```

Результат должен быть больше `0`.

Посмотреть содержимое Stream:

```bash
docker exec redis-master redis-cli XRANGE notifications - +
```

Проверить работу consumer:

```bash
docker logs gamehub-app --tail 100
```

В логах ожидается сообщение:

```text
Notification consumed: ...
```

Проверить consumer group:

```bash
docker exec redis-master redis-cli XPENDING notifications notifications-group
```

После обработки сообщения и выполнения `XACK` количество неподтверждённых сообщений должно быть `0`.

---

## 8. Массовая загрузка через Pipeline

Выполнить:

```text
POST /api/players/batch
```

Запрос должен содержать минимум 10 профилей.

Пример:

```json
[
  {"id":"2001","name":"Player2001","level":1,"region":"EU","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2002","name":"Player2002","level":2,"region":"EU","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2003","name":"Player2003","level":3,"region":"EU","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2004","name":"Player2004","level":4,"region":"EU","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2005","name":"Player2005","level":5,"region":"EU","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2006","name":"Player2006","level":6,"region":"US","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2007","name":"Player2007","level":7,"region":"US","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2008","name":"Player2008","level":8,"region":"US","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2009","name":"Player2009","level":9,"region":"US","createdAt":"2026-10-05T20:00:00Z"},
  {"id":"2010","name":"Player2010","level":10,"region":"US","createdAt":"2026-10-05T20:00:00Z"}
]
```

В ответе API выводятся количество созданных профилей и время выполнения операции.

Проверить созданные профили:

```bash
docker exec redis-master redis-cli KEYS "player:*"
```

Массовая запись реализована через Redis Pipeline (`executePipelined`).

---

# Отказоустойчивость

## 9. Защита от split-brain

> Проверку рекомендуется выполнять **до Sentinel failover**.

Приостановить обе реплики:

```bash
docker pause redis-replica-1 redis-replica-2
```

Подождать более 10 секунд:

```bash
sleep 12
```

Попробовать выполнить запись напрямую в master:

```bash
docker exec redis-master redis-cli SET split-brain-test 1
```

Ожидается ошибка:

```text
NOREPLICAS Not enough good replicas to write
```

Защита обеспечивается настройками:

```text
min-replicas-to-write 1
min-replicas-max-lag 10
```

Восстановить реплики:

```bash
docker unpause redis-replica-1 redis-replica-2
```

Подождать несколько секунд:

```bash
sleep 5
```

Проверить репликацию:

```bash
docker exec redis-master redis-cli INFO replication
```

Ожидается:

```text
connected_slaves:2
```

---

## 10. Redis Sentinel failover

Проверить текущий master:

```bash
docker exec sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

Остановить исходный master:

```bash
docker stop redis-master
```

Подождать переключения:

```bash
sleep 20
```

Снова запросить master:

```bash
docker exec sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

Адрес должен измениться: одна из реплик становится новым master.

Проверить роли:

```bash
docker exec redis-replica-1 redis-cli INFO replication | grep role
docker exec redis-replica-2 redis-cli INFO replication | grep role
```

Одна из реплик должна содержать:

```text
role:master
```

После failover проверить приложение через Swagger:

```text
GET /api/players/1001
```

API должен продолжить работу после автоматического переключения master.

> После failover контейнер `redis-master` при повторном запуске может стать репликой нового master. Это нормальное поведение Redis Sentinel.

---

# Проверка через Redis Insight

В Redis Insight можно визуально проверить следующие ключи:

```text
player:*
logins:*
cache:player:*
achievements:*
tournament:main
notifications
```

---

# Скрипт самопроверки

Перед сдачей рекомендуется запустить:

```bash
./dz1_check.py
```

или:

```bash
python3 dz1_check.py
```

Перед запуском убедиться, что:

- существует `player:1001`;
- игрок `1001` присутствует в `tournament:main`;
- у игрока есть хотя бы одно достижение;
- выполнен `POST /api/players/1001/login`;
- выполнен `GET /api/players/1001`, чтобы был создан кеш;
- в `notifications` имеется сообщение;
- через `/api/players/batch` создано не менее 10 профилей.

Так как TTL кеша составляет всего 60 секунд, скрипт рекомендуется запускать сразу после:

```text
GET /api/players/1001
```

---

# Локальный режим разработки

Для запуска приложения из IntelliJ с одиночным экземпляром Redis используется профиль:

```text
-Dspring.profiles.active=local
```

Финальная Docker-конфигурация предназначена для работы через Redis Sentinel.

---

# Полный сброс окружения

Для полного удаления контейнеров и данных:

```bash
docker compose down -v
```

После этого систему можно запустить заново:

```bash
docker compose up --build -d
```
