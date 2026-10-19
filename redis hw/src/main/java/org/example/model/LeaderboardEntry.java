package org.example.model;

public class LeaderboardEntry {

    private String playerId;
    private double score;
    private long position;

    public LeaderboardEntry(String playerId, double score, long position) {
        this.playerId = playerId;
        this.score = score;
        this.position = position;
    }

    public String getPlayerId() {
        return playerId;
    }

    public double getScore() {
        return score;
    }

    public long getPosition() {
        return position;
    }
}
