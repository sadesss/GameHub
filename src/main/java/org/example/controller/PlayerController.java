package org.example.controller;

import org.example.model.AchievementRequest;
import org.example.model.BatchPlayerRequest;
import org.example.model.LevelUpdateRequest;
import org.example.model.Player;
import org.example.service.AchievementService;
import org.example.service.PlayerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST-контроллер для работы с игроками и их достижениями.
 */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;
    private final AchievementService achievementService;

    public PlayerController(
            PlayerService playerService,
            AchievementService achievementService) {

        this.playerService = playerService;
        this.achievementService = achievementService;
    }

    /**
     * Создаёт или обновляет профиль игрока.
     */
    @PostMapping("/{id}")
    public Player savePlayer(
            @PathVariable String id,
            @RequestBody Player player) {

        return playerService.savePlayer(id, player);
    }

    /**
     * Возвращает профиль игрока.
     */
    @GetMapping("/{id}")
    public Player getPlayer(@PathVariable String id) {
        return playerService.getPlayer(id);
    }

    /**
     * Изменяет уровень игрока.
     */
    @PatchMapping("/{id}/level")
    public Map<String, Long> updateLevel(
            @PathVariable String id,
            @RequestBody LevelUpdateRequest request) {

        long level = playerService.updateLevel(id, request.getDelta());

        return Map.of(
                "level", level
        );
    }

    /**
     * Регистрирует вход игрока.
     */
    @PostMapping("/{id}/login")
    public Map<String, Long> login(@PathVariable String id) {
        long count = playerService.registerLogin(id);

        return Map.of(
                "logins", count
        );
    }

    /**
     * Добавляет достижение игроку.
     */
    @PostMapping("/{id}/achievements")
    public Map<String, Object> addAchievement(
            @PathVariable String id,
            @RequestBody AchievementRequest request) {

        boolean added = achievementService.add(
                id,
                request.getName()
        );

        return Map.of(
                "achievement", request.getName(),
                "added", added
        );
    }

    /**
     * Проверяет наличие достижения.
     */
    @GetMapping("/{id}/achievements/{name}")
    public Map<String, Object> hasAchievement(
            @PathVariable String id,
            @PathVariable String name) {

        return Map.of(
                "achievement", name,
                "present", achievementService.contains(id, name)
        );
    }

    /**
     * Возвращает общие достижения двух игроков.
     */
    @GetMapping("/{id1}/achievements/common/{id2}")
    public Set<String> commonAchievements(
            @PathVariable String id1,
            @PathVariable String id2) {

        return achievementService.common(id1, id2);
    }

    /**
     * Массово создаёт профили игроков.
     */
    @PostMapping("/batch")
    public Map<String, Object> batchCreate(
            @RequestBody List<BatchPlayerRequest> players) {

        return playerService.batchSave(players);
    }
}