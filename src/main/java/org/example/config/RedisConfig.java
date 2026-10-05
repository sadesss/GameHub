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

        if ("standalone".equalsIgnoreCase(mode)) {
            RedisStandaloneConfiguration standalone =
                    new RedisStandaloneConfiguration(host, port);
            return new LettuceConnectionFactory(standalone, clientBuilder.build());
        }

        RedisSentinelConfiguration sentinel = new RedisSentinelConfiguration();
        sentinel.master(sentinelMaster);

        for (String node : sentinelNodes.split(",")) {
            String trimmed = node.trim();
            int separator = trimmed.lastIndexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("Invalid Sentinel node: " + trimmed);
            }
            String sentinelHost = trimmed.substring(0, separator);
            int sentinelPort = Integer.parseInt(trimmed.substring(separator + 1));
            sentinel.sentinel(sentinelHost, sentinelPort);
        }

        // Записи идут в master, чтения по возможности обслуживаются replica.
        clientBuilder.readFrom(ReadFrom.REPLICA_PREFERRED);

        return new LettuceConnectionFactory(sentinel, clientBuilder.build());
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
