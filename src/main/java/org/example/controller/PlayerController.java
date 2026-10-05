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

    @PostMapping("/{id}")
    public Player savePlayer(
            @PathVariable String id,
            @RequestBody Player player) {
        return playerService.savePlayer(id, player);
    }

    @GetMapping("/{id}")
    public Player getPlayer(@PathVariable String id) {
        return playerService.getPlayer(id);
    }

    @PatchMapping("/{id}/level")
    public Map<String, Long> updateLevel(
            @PathVariable String id,
            @RequestBody LevelUpdateRequest request) {
        long level = playerService.updateLevel(id, request.getDelta());
        return Map.of("level", level);
    }

    @PostMapping("/{id}/login")
    public Map<String, Long> login(@PathVariable String id) {
        long count = playerService.registerLogin(id);
        return Map.of("logins", count);
    }

    @PostMapping("/{id}/achievements")
    public Map<String, Object> addAchievement(
            @PathVariable String id,
            @RequestBody AchievementRequest request) {
        boolean added = achievementService.add(id, request.getName());
        return Map.of(
                "achievement", request.getName(),
                "added", added
        );
    }

    @GetMapping("/{id}/achievements/{name}")
    public Map<String, Object> hasAchievement(
            @PathVariable String id,
            @PathVariable String name) {
        return Map.of(
                "achievement", name,
                "present", achievementService.contains(id, name)
        );
    }

    @GetMapping("/{id1}/achievements/common/{id2}")
    public Set<String> commonAchievements(
            @PathVariable String id1,
            @PathVariable String id2) {
        return achievementService.common(id1, id2);
    }

    @PostMapping("/batch")
    public Map<String, Object> batchCreate(@RequestBody List<BatchPlayerRequest> players) {
        return playerService.batchSave(players);
    }
}
