package org.example.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisConnectionCheck implements ApplicationRunner {

    private final RedisConnectionFactory connectionFactory;

    public RedisConnectionCheck(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Exception lastError = null;

        for (int attempt = 1; attempt <= 15; attempt++) {
            try (RedisConnection connection = connectionFactory.getConnection()) {
                String response = connection.ping();
                System.out.println("Redis connection: " + response);
                return;
            } catch (Exception e) {
                lastError = e;
                System.out.println("Redis connection attempt " + attempt + "/15 failed: " + e.getMessage());
                Thread.sleep(1000);
            }
        }

        throw new IllegalStateException("Cannot connect to Redis", lastError);
    }
}
