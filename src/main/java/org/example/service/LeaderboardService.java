package org.example.service;

import org.example.model.LeaderboardEntry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Сервис для работы с таблицей лидеров игроков.
 *
 * <p>Лидерборд хранится в Redis в структуре Sorted Set
 * под ключом {@code tournament:main}.</p>
 *
 * <p>В качестве элемента Sorted Set используется идентификатор игрока,
 * а в качестве score — количество набранных им очков.</p>
 */
@Service
public class LeaderboardService {

    /**
     * Redis-ключ основного лидерборда.
     */
    private static final String KEY = "tournament:main";

    private final StringRedisTemplate redisTemplate;

    /**
     * Создаёт сервис с доступом к Redis.
     *
     * @param redisTemplate шаблон для выполнения операций с Redis
     */
    public LeaderboardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Добавляет указанное количество очков игроку.
     *
     * <p>Если игрок уже существует в лидерборде, его текущий score
     * увеличивается. Если игрок отсутствует, он будет автоматически добавлен.</p>
     *
     * @param playerId идентификатор игрока
     * @param score количество добавляемых очков
     * @return новое итоговое количество очков игрока
     */
    public double addScore(String playerId, double score) {
        // ZINCRBY атомарно увеличивает score элемента в Sorted Set.
        Double result = redisTemplate.opsForZSet()
                .incrementScore(KEY, playerId, score);

        if (result == null) {
            throw new IllegalStateException(
                    "Redis did not return leaderboard score"
            );
        }

        return result;
    }

    /**
     * Возвращает игроков с наибольшим количеством очков.
     *
     * @param limit максимальное количество игроков в результате
     * @return список игроков, отсортированных по убыванию score
     */
    public List<LeaderboardEntry> getTop(int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be > 0"
            );
        }

        /*
         * ZREVRANGE WITHSCORES возвращает элементы Sorted Set
         * в порядке убывания score вместе с их значениями score.
         *
         * Диапазон в Redis задаётся включительно, поэтому для получения
         * limit элементов используется индекс limit - 1.
         */
        Set<ZSetOperations.TypedTuple<String>> values =
                redisTemplate.opsForZSet()
                        .reverseRangeWithScores(KEY, 0, limit - 1L);

        List<LeaderboardEntry> result = new ArrayList<>();

        // При отсутствии результата возвращаем пустой список.
        if (values == null) {
            return result;
        }

        /*
         * Для представления пользователю позиции нумеруются с 1,
         * поэтому первый элемент получает position = 1.
         */
        long position = 1;

        for (ZSetOperations.TypedTuple<String> value : values) {
            result.add(new LeaderboardEntry(
                    value.getValue(),
                    value.getScore() == null ? 0.0 : value.getScore(),
                    position++
            ));
        }

        return result;
    }

    /**
     * Возвращает позицию игрока в Sorted Set согласно команде Redis ZRANK.
     *
     * <p>Важно: Redis использует нумерацию позиций с нуля,
     * поэтому игрок с минимальным score имеет rank = 0.</p>
     *
     * @param playerId идентификатор игрока
     * @return позиция игрока, начиная с 0
     * @throws ResponseStatusException если игрок отсутствует в лидерборде
     */
    public long getRank(String playerId) {
        /*
         * По требованиям используется именно ZRANK.
         * В отличие от отображаемого топа, ZRANK сортирует элементы
         * по возрастанию score и возвращает индекс начиная с 0.
         */
        Long rank = redisTemplate.opsForZSet()
                .rank(KEY, playerId);

        if (rank == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Player is not present in leaderboard: " + playerId
            );
        }

        return rank;
    }
}