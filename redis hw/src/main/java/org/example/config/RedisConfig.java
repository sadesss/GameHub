package org.example.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Конфигурация подключения приложения к Redis.
 */
@Configuration
public class RedisConfig {

    @Value("${app.redis.mode:sentinel}")
    private String mode;

    @Value("${app.redis.host:localhost}")
    private String host;

    @Value("${app.redis.port:6379}")
    private int port;

    @Value("${app.redis.sentinel.master:mymaster}")
    private String sentinelMaster;

    @Value("${app.redis.sentinel.nodes:sentinel-1:26379,sentinel-2:26379,sentinel-3:26379}")
    private String sentinelNodes;

    /**
     * Создаёт фабрику подключений к Redis.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build())
                .build();

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientBuilder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(2))
                        .shutdownTimeout(Duration.ZERO)
                        .clientOptions(clientOptions);

        // Локальный режим с одиночным Redis.
        if ("standalone".equalsIgnoreCase(mode)) {
            RedisStandaloneConfiguration standalone =
                    new RedisStandaloneConfiguration(host, port);

            return new LettuceConnectionFactory(
                    standalone,
                    clientBuilder.build()
            );
        }

        // Основной режим работы через Redis Sentinel.
        RedisSentinelConfiguration sentinel =
                new RedisSentinelConfiguration();

        sentinel.master(sentinelMaster);

        // Добавляем Sentinel-узлы из конфигурации приложения.
        for (String node : sentinelNodes.split(",")) {
            String trimmed = node.trim();
            int separator = trimmed.lastIndexOf(':');

            if (separator <= 0) {
                throw new IllegalArgumentException(
                        "Invalid Sentinel node: " + trimmed
                );
            }

            String sentinelHost = trimmed.substring(0, separator);
            int sentinelPort =
                    Integer.parseInt(trimmed.substring(separator + 1));

            sentinel.sentinel(sentinelHost, sentinelPort);
        }

        // Операции выполняются через текущий master.
        clientBuilder.readFrom(ReadFrom.MASTER);

        return new LettuceConnectionFactory(
                sentinel,
                clientBuilder.build()
        );
    }

    /**
     * Создаёт шаблон для строковых операций с Redis.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        return new StringRedisTemplate(connectionFactory);
    }
}