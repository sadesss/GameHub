package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.BatchPlayerRequest;
import org.example.model.Player;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с профилями игроков.
 *
 * <p>Отвечает за:</p>
 * <ul>
 *     <li>сохранение профилей игроков в Redis Hash;</li>
 *     <li>получение профилей с использованием кеша;</li>
 *     <li>изменение уровня игрока;</li>
 *     <li>подсчёт количества входов;</li>
 *     <li>массовую запись профилей через Redis Pipeline;</li>
 *     <li>отправку уведомлений об изменениях профиля.</li>
 * </ul>
 */
@Service
public class PlayerService {

    /**
     * Время жизни кеша профиля игрока.
     */
    private static final Duration PROFILE_CACHE_TTL = Duration.ofSeconds(60);

    /**
     * Lua-скрипт для атомарного увеличения счётчика входов.
     *
     * <p>При первом входе создаётся ключ и устанавливается TTL 24 часа.
     * При последующих входах увеличивается только значение счётчика,
     * а исходный TTL сохраняется.</p>
     */
    private static final DefaultRedisScript<Long> LOGIN_SCRIPT =
            new DefaultRedisScript<>("""
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                        redis.call('EXPIRE', KEYS[1], 86400)
                    end
                    return count
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    /**
     * Создаёт сервис профилей игроков.
     *
     * @param redisTemplate       шаблон для выполнения операций с Redis
     * @param objectMapper        преобразователь Java-объектов в JSON и обратно
     * @param notificationService сервис отправки уведомлений
     */
    public PlayerService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            NotificationService notificationService) {

        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    /**
     * Создаёт или обновляет профиль игрока.
     *
     * <p>Профиль хранится в Redis Hash с ключом вида
     * {@code player:{id}}.</p>
     *
     * <p>После изменения профиля кеш удаляется, чтобы следующий запрос
     * получил актуальные данные из основного хранилища.</p>
     *
     * @param id     идентификатор игрока
     * @param player данные игрока
     * @return сохранённый профиль
     */
    public Player savePlayer(String id, Player player) {
        // Если дата создания не передана, фиксируем текущее время.
        if (player.getCreatedAt() == null || player.getCreatedAt().isBlank()) {
            player.setCreatedAt(Instant.now().toString());
        }

        String key = playerKey(id);

        // Сохраняем поля объекта Player в Redis Hash.
        redisTemplate.opsForHash().putAll(
                key,
                toRedisHash(player)
        );

        /*
         * Данные профиля изменились, поэтому ранее сохранённый кеш
         * становится неактуальным и должен быть удалён.
         */
        redisTemplate.delete(cacheKey(id));

        // Отправляем событие об изменении профиля в систему уведомлений.
        notificationService.send(
                id,
                "profile_updated",
                "Player profile created or updated"
        );

        return player;
    }

    /**
     * Возвращает профиль игрока.
     *
     * <p>Сначала выполняется попытка получить данные из кеша.
     * При отсутствии кеша профиль читается из Redis Hash и затем
     * сохраняется в кеш на 60 секунд.</p>
     *
     * @param id идентификатор игрока
     * @return профиль игрока
     */
    public Player getPlayer(String id) {
        String cacheKey = cacheKey(id);

        // Сначала пытаемся получить сериализованный профиль из кеша.
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            System.out.println("CACHE HIT: " + cacheKey);

            try {
                // В кеше профиль хранится в формате JSON.
                return objectMapper.readValue(cached, Player.class);

            } catch (JsonProcessingException e) {
                /*
                 * Если содержимое кеша повреждено или имеет неверный формат,
                 * удаляем его, чтобы не использовать некорректные данные.
                 */
                redisTemplate.delete(cacheKey);

                throw new IllegalStateException(
                        "Cannot deserialize cached player",
                        e
                );
            }
        }

        System.out.println("CACHE MISS: " + cacheKey);

        // При промахе кеша читаем данные из основного Redis Hash.
        Map<Object, Object> values =
                redisTemplate.opsForHash().entries(playerKey(id));

        if (values.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Player not found: " + id
            );
        }

        // Преобразуем поля Redis Hash обратно в объект Player.
        Player player = fromRedisHash(values);

        try {
            /*
             * Сохраняем профиль в кеш в виде JSON.
             * Кеш автоматически удалится через PROFILE_CACHE_TTL.
             */
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(player),
                    PROFILE_CACHE_TTL
            );

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Cannot serialize player to cache",
                    e
            );
        }

        return player;
    }

    /**
     * Изменяет уровень игрока на указанную величину.
     *
     * @param id    идентификатор игрока
     * @param delta величина изменения уровня
     * @return новое значение уровня
     */
    public long updateLevel(String id, long delta) {
        String playerKey = playerKey(id);

        // Проверяем существование профиля перед изменением уровня.
        if (Boolean.FALSE.equals(redisTemplate.hasKey(playerKey))) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Player not found: " + id
            );
        }

        /*
         * HINCRBY атомарно изменяет числовое поле level
         * непосредственно внутри Redis Hash.
         */
        Long newLevel = redisTemplate.opsForHash()
                .increment(playerKey, "level", delta);

        if (newLevel == null) {
            throw new IllegalStateException(
                    "Redis did not return updated level"
            );
        }

        // После изменения уровня кеш профиля становится неактуальным.
        redisTemplate.delete(cacheKey(id));

        // Формируем уведомление об изменении уровня.
        notificationService.send(
                id,
                "level_changed",
                "Player level changed by " + delta
        );

        return newLevel;
    }

    /**
     * Регистрирует очередной вход игрока.
     *
     * <p>Счётчик хранится в ключе {@code logins:{id}}.
     * Для атомарного выполнения INCR и установки TTL используется
     * Lua-скрипт.</p>
     *
     * @param id идентификатор игрока
     * @return текущее количество входов за период действия ключа
     */
    public long registerLogin(String id) {
        /*
         * Скрипт выполняется целиком внутри Redis,
         * поэтому увеличение счётчика и установка TTL атомарны.
         */
        Long count = redisTemplate.execute(
                LOGIN_SCRIPT,
                List.of(loginKey(id))
        );

        if (count == null) {
            throw new IllegalStateException(
                    "Redis did not return login count"
            );
        }

        return count;
    }

    /**
     * Массово сохраняет профили игроков.
     *
     * <p>Для снижения количества сетевых запросов используется
     * Redis Pipeline. Все команды отправляются Redis одной группой,
     * а не отдельными запросами.</p>
     *
     * @param players список игроков
     * @return информация о количестве записанных профилей
     *         и времени выполнения операции
     */
    public Map<String, Object> batchSave(List<BatchPlayerRequest> players) {
        // Согласно требованиям batch должен содержать минимум 10 игроков.
        if (players == null || players.size() < 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Batch must contain at least 10 players"
            );
        }

        long started = System.nanoTime();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

            for (BatchPlayerRequest player : players) {

                // Каждый профиль обязан иметь собственный идентификатор.
                if (player.getId() == null || player.getId().isBlank()) {
                    throw new IllegalArgumentException(
                            "Every batch player must have id"
                    );
                }

                String createdAt = player.getCreatedAt();

                // При отсутствии даты устанавливаем текущее время.
                if (createdAt == null || createdAt.isBlank()) {
                    createdAt = Instant.now().toString();
                }

                /*
                 * При работе через низкоуровневый RedisConnection
                 * ключи и значения передаются в виде byte[].
                 */
                Map<byte[], byte[]> hash = new LinkedHashMap<>();

                hash.put(
                        bytes("name"),
                        bytes(player.getName())
                );

                hash.put(
                        bytes("level"),
                        bytes(String.valueOf(player.getLevel()))
                );

                hash.put(
                        bytes("region"),
                        bytes(player.getRegion())
                );

                hash.put(
                        bytes("created_at"),
                        bytes(createdAt)
                );

                // Добавляем команду сохранения Hash в Pipeline.
                connection.hashCommands().hMSet(
                        bytes(playerKey(player.getId())),
                        hash
                );

                /*
                 * При массовом обновлении также удаляем старый кеш,
                 * чтобы не возвращать устаревшие данные.
                 */
                connection.keyCommands().del(
                        bytes(cacheKey(player.getId()))
                );
            }

            return null;
        });

        // Измеряем длительность выполнения batch-запроса.
        long elapsedMillis =
                (System.nanoTime() - started) / 1_000_000;

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("created", players.size());
        result.put("elapsedMs", elapsedMillis);

        return result;
    }

    /**
     * Преобразует объект Player в набор полей для Redis Hash.
     *
     * @param player профиль игрока
     * @return отображение "поле -> значение"
     */
    private Map<String, String> toRedisHash(Player player) {
        Map<String, String> data = new LinkedHashMap<>();

        data.put("name", player.getName());
        data.put("level", String.valueOf(player.getLevel()));
        data.put("region", player.getRegion());
        data.put("created_at", player.getCreatedAt());

        return data;
    }

    /**
     * Восстанавливает объект Player из Redis Hash.
     *
     * @param values значения полей Redis Hash
     * @return профиль игрока
     */
    private Player fromRedisHash(Map<Object, Object> values) {
        return new Player(
                String.valueOf(values.get("name")),
                Long.parseLong(String.valueOf(values.get("level"))),
                String.valueOf(values.get("region")),
                String.valueOf(values.get("created_at"))
        );
    }

    /**
     * Формирует ключ основного профиля игрока.
     */
    private String playerKey(String id) {
        return "player:" + id;
    }

    /**
     * Формирует ключ кеша профиля.
     */
    private String cacheKey(String id) {
        return "cache:player:" + id;
    }

    /**
     * Формирует ключ счётчика входов.
     */
    private String loginKey(String id) {
        return "logins:" + id;
    }

    /**
     * Преобразует строку в массив байтов UTF-8.
     *
     * <p>Метод используется при работе с низкоуровневым
     * RedisConnection внутри Pipeline.</p>
     *
     * @param value исходная строка
     * @return строка в виде byte[]
     */
    private static byte[] bytes(String value) {
        if (value == null) {
            return new byte[0];
        }

        return value.getBytes(StandardCharsets.UTF_8);
    }
}