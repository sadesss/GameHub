package org.example.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AchievementService {

    private final StringRedisTemplate redisTemplate;

    public AchievementService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean add(String playerId, String achievementName) {
        Long added = redisTemplate.opsForSet().add(key(playerId), achievementName);
        return added != null && added > 0;
    }

    public boolean contains(String playerId, String achievementName) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(key(playerId), achievementName)
        );
    }

    public Set<String> common(String firstPlayerId, String secondPlayerId) {
        Set<String> result = redisTemplate.opsForSet().intersect(
                key(firstPlayerId),
                key(secondPlayerId)
        );
        return result == null ? Set.of() : result;
    }

    private String key(String playerId) {
        return "achievements:" + playerId;
    }
}
