package org.example.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Сервис для работы с достижениями игроков.
 *
 * <p>Достижения каждого игрока хранятся в Redis в структуре Set
 * с ключом вида {@code achievements:{playerId}}.</p>
 *
 * <p>Использование Set позволяет:</p>
 * <ul>
 *     <li>не хранить одинаковое достижение несколько раз;</li>
 *     <li>быстро проверять наличие достижения;</li>
 *     <li>находить общие достижения двух игроков через пересечение множеств.</li>
 * </ul>
 */
@Service
public class AchievementService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Создаёт сервис с доступом к Redis.
     *
     * @param redisTemplate шаблон для выполнения операций с Redis
     */
    public AchievementService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Добавляет достижение игроку.
     *
     * @param playerId идентификатор игрока
     * @param achievementName название достижения
     * @return {@code true}, если достижение было добавлено;
     *         {@code false}, если оно уже существовало
     */
    public boolean add(String playerId, String achievementName) {
        // SADD возвращает количество новых элементов, добавленных в Set.
        Long added = redisTemplate.opsForSet().add(
                key(playerId),
                achievementName
        );

        return added != null && added > 0;
    }

    /**
     * Проверяет, есть ли у игрока указанное достижение.
     *
     * @param playerId идентификатор игрока
     * @param achievementName название достижения
     * @return {@code true}, если достижение присутствует
     */
    public boolean contains(String playerId, String achievementName) {
        // Операция соответствует команде Redis SISMEMBER.
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet()
                        .isMember(key(playerId), achievementName)
        );
    }

    /**
     * Возвращает достижения, которые есть у обоих игроков.
     *
     * @param firstPlayerId идентификатор первого игрока
     * @param secondPlayerId идентификатор второго игрока
     * @return множество общих достижений
     */
    public Set<String> common(String firstPlayerId, String secondPlayerId) {
        // Пересечение двух Redis Set выполняется командой SINTER.
        Set<String> result = redisTemplate.opsForSet().intersect(
                key(firstPlayerId),
                key(secondPlayerId)
        );

        // RedisTemplate может вернуть null, поэтому наружу всегда отдаём Set.
        return result == null ? Set.of() : result;
    }

    /**
     * Формирует Redis-ключ для множества достижений конкретного игрока.
     *
     * @param playerId идентификатор игрока
     * @return ключ вида achievements:{playerId}
     */
    private String key(String playerId) {
        return "achievements:" + playerId;
    }
}