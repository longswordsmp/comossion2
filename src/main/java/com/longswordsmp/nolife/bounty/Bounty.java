package com.longswordsmp.nolife.bounty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single bounty: the Life Gems staked on one target, tracked per placer so
 * each placer can reclaim exactly what they contributed.
 */
public class Bounty {

    private final UUID target;
    private String targetName;

    /** Placer UUID -&gt; gems staked. Insertion order is kept for display. */
    private final Map<UUID, Integer> contributions = new LinkedHashMap<>();
    /** Placer UUID -&gt; last-known name, for display. */
    private final Map<UUID, String> placerNames = new LinkedHashMap<>();

    public Bounty(UUID target, String targetName) {
        this.target = target;
        this.targetName = targetName;
    }

    public UUID target() {
        return target;
    }

    public String targetName() {
        return targetName;
    }

    public void setTargetName(String name) {
        if (name != null && !name.isEmpty()) {
            this.targetName = name;
        }
    }

    /** Total Life Gems staked by everyone on this target. */
    public int total() {
        int sum = 0;
        for (int value : contributions.values()) {
            sum += value;
        }
        return sum;
    }

    /** Gems a single placer has staked (0 if none). */
    public int contribution(UUID placer) {
        return contributions.getOrDefault(placer, 0);
    }

    /** Add to a placer's stake (creating it if new). */
    public void add(UUID placer, String placerName, int amount) {
        contributions.merge(placer, amount, Integer::sum);
        if (placerName != null && !placerName.isEmpty()) {
            placerNames.put(placer, placerName);
        }
    }

    /** Remove a placer's entire stake and return how much it was. */
    public int removeContribution(UUID placer) {
        Integer removed = contributions.remove(placer);
        placerNames.remove(placer);
        return removed == null ? 0 : removed;
    }

    public boolean isEmpty() {
        return contributions.isEmpty();
    }

    public Map<UUID, Integer> contributions() {
        return contributions;
    }

    public String placerName(UUID placer) {
        return placerNames.get(placer);
    }
}
