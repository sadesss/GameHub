package org.example.controller;

import org.example.model.LeaderboardEntry;
import org.example.model.ScoreRequest;
import org.example.service.LeaderboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @PostMapping("/score")
    public Map<String, Object> addScore(@RequestBody ScoreRequest request) {
        double score = leaderboardService.addScore(
                request.getPlayerId(),
                request.getScore()
        );

        return Map.of(
                "playerId", request.getPlayerId(),
                "score", score
        );
    }

    @GetMapping("/top")
    public List<LeaderboardEntry> top(
            @RequestParam(defaultValue = "10") int limit) {
        return leaderboardService.getTop(limit);
    }

    @GetMapping("/rank/{playerId}")
    public Map<String, Object> rank(@PathVariable String playerId) {
        return Map.of(
                "playerId", playerId,
                "rank", leaderboardService.getRank(playerId)
        );
    }
}
