package com.longswordsmp.nolife.data;

import java.util.UUID;

/**
 * The persistent state we track for a single player: how many lives they have
 * and whether they are currently eliminated (death-banned).
 */
public class PlayerData {

    private final UUID uuid;
    private String name;
    private int lives;
    private boolean eliminated;
    private boolean infinite;

    public PlayerData(UUID uuid, String name, int lives, boolean eliminated) {
        this.uuid = uuid;
        this.name = name;
        this.lives = lives;
        this.eliminated = eliminated;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public boolean isInfinite() {
        return infinite;
    }

    public void setInfinite(boolean infinite) {
        this.infinite = infinite;
    }
}
