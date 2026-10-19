package org.example.service;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final String STREAM = "notifications";
    private static final String GROUP = "notifications-group";
    private static final String CONSUMER = "gamehub-consumer";

    private final StringRedisTemplate redisTemplate;

    public NotificationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        initializeConsumerGroup();
    }

    private void initializeConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(
                    STREAM,
                    ReadOffset.latest(),
                    GROUP
            );

            System.out.println("Consumer group created: " + GROUP);

        } catch (RedisSystemException e) {
            String message = getFullExceptionMessage(e);

            if (message.contains("BUSYGROUP")) {
                System.out.println("Consumer group already exists: " + GROUP);
                return;
            }

            throw e;
        }
    }

    public void send(
            String playerId,
            String type,
            String message
    ) {
        Map<String, String> fields = new HashMap<>();

        fields.put("player_id", playerId);
        fields.put("type", type);
        fields.put("message", message);
        fields.put("timestamp", Instant.now().toString());

        redisTemplate.opsForStream().add(
                STREAM,
                fields
        );

        trimOldNotifications();
    }

    private void trimOldNotifications() {
        /*
         * В задании требуется хранить уведомления 7 дней.
         *
         * Spring Data Redis не даёт удобного прямого метода
         * "удали всё старше 7 дней" по timestamp-полю,
         * поэтому для учебного проекта можно ограничивать размер Stream.
         *
         * Если у тебя уже была своя реализация MAXLEN,
         * можешь оставить её вместо этой.
         */
        redisTemplate.opsForStream().trim(
                STREAM,
                10_000
        );
    }

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        try {
            List<MapRecord<String, Object, Object>> messages =
                    redisTemplate.opsForStream().read(
                            Consumer.from(GROUP, CONSUMER),
                            StreamReadOptions.empty()
                                    .count(10)
                                    .block(Duration.ofSeconds(1)),
                            StreamOffset.create(
                                    STREAM,
                                    ReadOffset.lastConsumed()
                            )
                    );

            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> message : messages) {

                System.out.println(
                        "Notification consumed: " + message.getValue()
                );

                redisTemplate.opsForStream().acknowledge(
                        STREAM,
                        GROUP,
                        message.getId()
                );
            }

        } catch (Exception e) {
            System.err.println("Notification consumer error:");
            e.printStackTrace();
        }
    }

    private String getFullExceptionMessage(Throwable throwable) {
        StringBuilder result = new StringBuilder();

        Throwable current = throwable;

        while (current != null) {
            if (current.getMessage() != null) {
                result.append(current.getMessage()).append(" ");
            }

            current = current.getCause();
        }

        return result.toString();
    }
}