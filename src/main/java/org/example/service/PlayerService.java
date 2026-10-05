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

@Service
public class PlayerService {

    private static final Duration PROFILE_CACHE_TTL = Duration.ofSeconds(60);

    private static final DefaultRedisScript<Long> LOGIN_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], 86400)
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public PlayerService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            NotificationService notificationService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    public Player savePlayer(String id, Player player) {
        if (player.getCreatedAt() == null || player.getCreatedAt().isBlank()) {
            player.setCreatedAt(Instant.now().toString());
        }

        String key = playerKey(id);
        redisTemplate.opsForHash().putAll(key, toRedisHash(player));

        redisTemplate.delete(cacheKey(id));
        notificationService.send(id, "profile_updated", "Player profile created or updated");

        return player;
    }

    public Player getPlayer(String id) {
        String cached = redisTemplate.opsForValue().get(cacheKey(id));

        if (cached != null) {
            System.out.println("CACHE HIT: " + cacheKey(id));
            try {
                return objectMapper.readValue(cached, Player.class);
            } catch (JsonProcessingException e) {
                redisTemplate.delete(cacheKey(id));
                throw new IllegalStateException("Cannot deserialize cached player", e);
            }
        }

        System.out.println("CACHE MISS: " + cacheKey(id));

        Map<Object, Object> values = redisTemplate.opsForHash().entries(playerKey(id));
        if (values.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + id);
        }

        Player player = fromRedisHash(values);

        try {
            redisTemplate.opsForValue().set(
                    cacheKey(id),
                    objectMapper.writeValueAsString(player),
                    PROFILE_CACHE_TTL
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize player to cache", e);
        }

        return player;
    }

    public long updateLevel(String id, long delta) {
        if (Boolean.FALSE.equals(redisTemplate.hasKey(playerKey(id)))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + id);
        }

        Long newLevel = redisTemplate.opsForHash().increment(playerKey(id), "level", delta);
        if (newLevel == null) {
            throw new IllegalStateException("Redis did not return updated level");
        }

        redisTemplate.delete(cacheKey(id));
        notificationService.send(id, "level_changed", "Player level changed by " + delta);

        return newLevel;
    }

    public long registerLogin(String id) {
        Long count = redisTemplate.execute(LOGIN_SCRIPT, List.of(loginKey(id)));
        if (count == null) {
            throw new IllegalStateException("Redis did not return login count");
        }
        return count;
    }

    public Map<String, Object> batchSave(List<BatchPlayerRequest> players) {
        if (players == null || players.size() < 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Batch must contain at least 10 players"
            );
        }

        long started = System.nanoTime();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (BatchPlayerRequest player : players) {
                if (player.getId() == null || player.getId().isBlank()) {
                    throw new IllegalArgumentException("Every batch player must have id");
                }

                String createdAt = player.getCreatedAt();
                if (createdAt == null || createdAt.isBlank()) {
                    createdAt = Instant.now().toString();
                }

                Map<byte[], byte[]> hash = new LinkedHashMap<>();
                hash.put(bytes("name"), bytes(player.getName()));
                hash.put(bytes("level"), bytes(String.valueOf(player.getLevel())));
                hash.put(bytes("region"), bytes(player.getRegion()));
                hash.put(bytes("created_at"), bytes(createdAt));

                connection.hashCommands().hMSet(bytes(playerKey(player.getId())), hash);
                connection.keyCommands().del(bytes(cacheKey(player.getId())));
            }
            return null;
        });

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", players.size());
        result.put("elapsedMs", elapsedMillis);
        return result;
    }

    private Map<String, String> toRedisHash(Player player) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("name", player.getName());
        data.put("level", String.valueOf(player.getLevel()));
        data.put("region", player.getRegion());
        data.put("created_at", player.getCreatedAt());
        return data;
    }

    private Player fromRedisHash(Map<Object, Object> values) {
        return new Player(
                String.valueOf(values.get("name")),
                Long.parseLong(String.valueOf(values.get("level"))),
                String.valueOf(values.get("region")),
                String.valueOf(values.get("created_at"))
        );
    }

    private String playerKey(String id) {
        return "player:" + id;
    }

    private String cacheKey(String id) {
        return "cache:player:" + id;
    }

    private String loginKey(String id) {
        return "logins:" + id;
    }

    private static byte[] bytes(String value) {
        if (value == null) {
            return new byte[0];
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
