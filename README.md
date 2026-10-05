# GameHub Redis

## Стек

- Java 25
- Spring Boot 3.5.6
- Spring Data Redis / Lettuce
- Redis 7.4
- Docker Compose
- Swagger / OpenAPI

## Redis-структуры

- `player:{id}` — Hash
- `logins:{id}` — String, TTL 24 часа
- `cache:player:{id}` — String (JSON), TTL 60 секунд
- `tournament:main` — Sorted Set
- `achievements:{id}` — Set
- `notifications` — Stream

## Запуск финальной конфигурации

Если ранее был запущен одиночный контейнер `gamehub-redis`, остановите его:

```bash
docker rm -f gamehub-redis
```

Соберите приложение:

```bash
mvn clean package
```

Запустите всю систему:

```bash
docker compose up --build -d
```

Проверьте:

```bash
docker compose ps
docker exec redis-master redis-cli INFO replication
```

У мастера должно быть `connected_slaves:2`.

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

## Локальный режим разработки без Sentinel

Для запуска приложения из IntelliJ против одиночного Redis используйте профиль `local`:

```text
-Dspring.profiles.active=local
```

Финальная Docker-конфигурация работает через Sentinel.

## API

### Профили

- `POST /api/players/{id}`
- `GET /api/players/{id}`
- `PATCH /api/players/{id}/level`
- `POST /api/players/{id}/login`
- `POST /api/players/batch`

### Лидерборд

- `POST /api/leaderboard/score`
- `GET /api/leaderboard/top?limit=10`
- `GET /api/leaderboard/rank/{playerId}`

### Достижения

- `POST /api/players/{id}/achievements`
- `GET /api/players/{id}/achievements/{name}`
- `GET /api/players/{id1}/achievements/common/{id2}`

## Примеры JSON

Создание игрока:

```json
{
  "name": "Alice",
  "level": 5,
  "region": "EU",
  "createdAt": "2026-10-04T20:00:00Z"
}
```

Изменение уровня:

```json
{
  "delta": 3
}
```

Добавление очков:

```json
{
  "playerId": "1",
  "score": 100
}
```

Достижение:

```json
{
  "name": "FIRST_WIN"
}
```

Batch должен содержать минимум 10 объектов:

```json
[
  {"id":"101","name":"P101","level":1,"region":"EU","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"102","name":"P102","level":2,"region":"EU","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"103","name":"P103","level":3,"region":"EU","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"104","name":"P104","level":4,"region":"EU","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"105","name":"P105","level":5,"region":"EU","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"106","name":"P106","level":6,"region":"US","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"107","name":"P107","level":7,"region":"US","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"108","name":"P108","level":8,"region":"US","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"109","name":"P109","level":9,"region":"US","createdAt":"2026-10-04T20:00:00Z"},
  {"id":"110","name":"P110","level":10,"region":"US","createdAt":"2026-10-04T20:00:00Z"}
]
```

## Проверка кеша

Выполните два раза:

```text
GET /api/players/1
```

В логах приложения первый запрос должен дать `CACHE MISS`, второй — `CACHE HIT`.

TTL:

```bash
docker exec redis-master redis-cli TTL cache:player:1
```

## Проверка логинов

После `POST /api/players/1/login`:

```bash
docker exec redis-master redis-cli GET logins:1
docker exec redis-master redis-cli TTL logins:1
```

## Проверка Streams

После изменения профиля или уровня:

```bash
docker exec redis-master redis-cli XLEN notifications
docker exec redis-master redis-cli XPENDING notifications notifications-group
```

Потребитель выводит сообщения в логи и делает `XACK`:

```bash
docker logs gamehub-app
```

## Проверка реплик

```bash
docker exec redis-replica-1 redis-cli HGETALL player:1
docker exec redis-replica-2 redis-cli HGETALL player:1
```

## Защита от split-brain

Лучше проверять до сценария failover.

```bash
docker pause redis-replica-1 redis-replica-2
```

Попытка записи на master должна через несколько секунд начать завершаться ошибкой из-за `min-replicas-to-write 1`.

После демонстрации:

```bash
docker unpause redis-replica-1 redis-replica-2
```

## Sentinel failover

```bash
docker stop redis-master
```

Подождите около 15 секунд и проверьте:

```bash
docker exec sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

Sentinel должен показать новый master. После этого API приложения должен продолжить работу.

## Полный сброс окружения

```bash
docker compose down -v
docker compose up --build -d
```
