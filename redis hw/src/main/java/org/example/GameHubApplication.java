package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс приложения GameHub.
 *
 * <p>Аннотация {@link SpringBootApplication} включает автоматическую
 * конфигурацию Spring Boot и сканирование компонентов приложения.</p>
 *
 * <p>Аннотация {@link EnableScheduling} включает поддержку периодических
 * задач, выполняемых с помощью механизма Spring Scheduling.</p>
 */
@SpringBootApplication
@EnableScheduling
public class GameHubApplication {

    /**
     * Точка входа в приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        // Запуск Spring Boot-приложения и создание Spring-контекста.
        SpringApplication.run(GameHubApplication.class, args);
    }
}