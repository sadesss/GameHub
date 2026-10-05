package org.example.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final String STREAM = "notifications";
    private static final String GROUP = "notifications-group";
    private static final String CONSUMER = "gamehub-consumer-1";
    private static final Duration RETENTION = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private volatile boolean groupInitialized = false;

    public NotificationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void initializeConsumerGroup() {
        if (groupInitialized) {
            return;
        }

        try {
            // Spring Data Redis создаёт stream при createGroup, что эквивалентно XGROUP CREATE ... MKSTREAM.
            redisTemplate.opsForStream().createGroup(STREAM, ReadOffset.latest(), GROUP);
            System.out.println("Created Redis Stream consumer group: " + GROUP);
        } catch (DataAccessException e) {
            String message = e.getMessage();
            if (message == null || !message.contains("BUSYGROUP")) {
                throw e;
            }
        }

        groupInitialized = true;
    }

    public void send(String playerId, String type, String message) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("player_id", playerId);
        fields.put("type", type);
        fields.put("message", message);
        fields.put("timestamp", Instant.now().toString());

        redisTemplate.opsForStream().add(STREAM, fields);
        trimOlderThanSevenDays();
    }

    private void trimOlderThanSevenDays() {
        long cutoffMillis = Instant.now().minus(RETENTION).toEpochMilli();
        String minId = cutoffMillis + "-0";

        redisTemplate.execute((RedisCallback<Object>) connection ->
                connection.execute(
                        "XTRIM",
                        bytes(STREAM),
                        bytes("MINID"),
                        bytes("~"),
                        bytes(minId)
                )
        );
    }

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        try {
            initializeConsumerGroup();

            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(10),
                    StreamOffset.create(STREAM, ReadOffset.lastConsumed())
            );

            if (records == null || records.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> record : records) {
                System.out.println("Notification consumed: " + record.getValue());
                redisTemplate.opsForStream().acknowledge(STREAM, GROUP, record.getId());
            }
        } catch (Exception e) {
            System.err.println("Notification consumer error: " + e.getMessage());
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
