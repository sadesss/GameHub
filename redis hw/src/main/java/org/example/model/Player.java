package org.example.model;

public class Player {

    private String name;
    private long level;
    private String region;
    private String createdAt;

    public Player() {
    }

    public Player(String name, long level, String region, String createdAt) {
        this.name = name;
        this.level = level;
        this.region = region;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLevel() {
        return level;
    }

    public void setLevel(long level) {
        this.level = level;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
