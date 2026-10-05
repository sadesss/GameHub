package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GameHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameHubApplication.class, args);
    }
}
