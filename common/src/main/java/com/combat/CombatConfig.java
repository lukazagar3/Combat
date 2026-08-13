package com.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatConfig {
    public int combatLogSeconds = 15;
    public int immunitySeconds = 60;
    public boolean allowEnderchestInCombat = false;
    public boolean limitsOnlyInCombat = false; // false = ALWAYS ON, true = ONLY IN COMBAT
    public boolean rankedSystemEnabled = true;
    public Map<String, Integer> itemLimits = new HashMap<>(); // item_id -> limit
    public Map<String, Double> itemCooldowns = new HashMap<>(); // item_id -> seconds
    public Map<String, Integer> enchantLimits = new HashMap<>(); // enchant_id -> max level
    public Map<String, Integer> potionLimits = new HashMap<>(); // potion_id -> max level (-1 for disabled)
    public Map<String, WorldLimitedItem> worldLimits = new HashMap<>(); // item_id -> tracking data
    
    public Map<Integer, Integer> rankHealthBoosts = new HashMap<>(); // 1-indexed (1 to 10) -> extra max health (half hearts)
    public Map<Integer, java.util.List<String>> rankPotionEffects = new HashMap<>(); // 1-indexed (1 to 10) -> list of potion effects ("speed", "strength")

    public CombatConfig() {
        // Initialize default health boosts for top 10
        for (int i = 1; i <= 10; i++) {
            rankHealthBoosts.put(i, (11 - i) * 4); // #1 gets 40 (+20 hearts), #10 gets 4 (+2 hearts)
        }
        // Initialize default potion effects for top 3
        rankPotionEffects.put(1, java.util.Arrays.asList("speed", "strength"));
        rankPotionEffects.put(2, java.util.Arrays.asList("speed", "strength"));
        rankPotionEffects.put(3, java.util.Arrays.asList("speed"));
    }

    public static class WorldLimitedItem {
        public int maxCount = 1;
        public UUID currentOwner = null;
        public long ownerAcquiredTime = 0;
    }
}
