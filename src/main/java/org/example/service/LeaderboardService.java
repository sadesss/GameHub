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

@Service
public class LeaderboardService {

    private static final String KEY = "tournament:main";

    private final StringRedisTemplate redisTemplate;

    public LeaderboardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public double addScore(String playerId, double score) {
        Double result = redisTemplate.opsForZSet().incrementScore(KEY, playerId, score);
        if (result == null) {
            throw new IllegalStateException("Redis did not return leaderboard score");
        }
        return result;
    }

    public List<LeaderboardEntry> getTop(int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be > 0");
        }

        Set<ZSetOperations.TypedTuple<String>> values =
                redisTemplate.opsForZSet().reverseRangeWithScores(KEY, 0, limit - 1L);

        List<LeaderboardEntry> result = new ArrayList<>();
        if (values == null) {
            return result;
        }

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

    public long getRank(String playerId) {
        // По ТЗ используется именно ZRANK. Redis возвращает rank с 0.
        Long rank = redisTemplate.opsForZSet().rank(KEY, playerId);
        if (rank == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Player is not present in leaderboard: " + playerId
            );
        }
        return rank;
    }
}
