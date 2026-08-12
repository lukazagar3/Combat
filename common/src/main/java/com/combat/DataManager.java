package com.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class DataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dataFile;
    private static DataManagerState state = new DataManagerState();

    public static class DataManagerState {
        public Map<UUID, PlayerData> playersData = new HashMap<>();
        public Map<String, List<UUID>> worldLimitOwners = new HashMap<>();
    }

    public static void init(File configDir) {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        dataFile = new File(configDir, "combat_data.json");
        load();
    }

    public static void load() {
        try {
            if (dataFile.exists()) {
                try (FileReader reader = new FileReader(dataFile)) {
                    DataManagerState loaded = GSON.fromJson(reader, DataManagerState.class);
                    if (loaded != null) {
                        state = loaded;
                    }
                }
            } else {
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            GSON.toJson(state, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static PlayerData getOrCreatePlayerData(UUID uuid) {
        return state.playersData.computeIfAbsent(uuid, k -> {
            PlayerData data = new PlayerData();
            data.lastSeenTime = System.currentTimeMillis();
            return data;
        });
    }

    public static void onPlayerLogin(UUID uuid) {
        PlayerData data = getOrCreatePlayerData(uuid);
        data.lastLoginTime = System.currentTimeMillis();
        data.lastSeenTime = data.lastLoginTime;
        save();
    }

    public static void onPlayerLogout(UUID uuid) {
        PlayerData data = getOrCreatePlayerData(uuid);
        long now = System.currentTimeMillis();
        data.lastSeenTime = now;
        if (data.lastLoginTime > 0) {
            long sessionTime = now - data.lastLoginTime;
            String today = LocalDate.now().toString();
            long currentPlaytime = data.dailyPlaytimeMs.getOrDefault(today, 0L);
            data.dailyPlaytimeMs.put(today, currentPlaytime + sessionTime);
            data.lastLoginTime = 0; // reset
        }
        save();
    }

    public static void tickOnlinePlayer(UUID uuid) {
        PlayerData data = state.playersData.get(uuid);
        if (data != null && data.lastLoginTime > 0) {
            long now = System.currentTimeMillis();
            data.lastSeenTime = now;
            long delta = now - data.lastLoginTime;
            if (delta >= 60000) { // Update every minute
                String today = LocalDate.now().toString();
                long currentPlaytime = data.dailyPlaytimeMs.getOrDefault(today, 0L);
                data.dailyPlaytimeMs.put(today, currentPlaytime + delta);
                data.lastLoginTime = now; // update base
                save();
            }
        }
    }

    // World Limit Ownership Logic
    public static boolean isOwner(UUID uuid, String itemId) {
        List<UUID> owners = state.worldLimitOwners.get(itemId);
        return owners != null && owners.contains(uuid);
    }

    public static boolean tryAcquire(UUID uuid, String itemId, int maxCount) {
        List<UUID> owners = state.worldLimitOwners.computeIfAbsent(itemId, k -> new ArrayList<>());
        if (owners.contains(uuid)) {
            return true; // Already owns one
        }
        if (owners.size() < maxCount) {
            owners.add(uuid);
            save();
            return true;
        }
        return false;
    }

    public static void releaseOwnership(UUID uuid, String itemId) {
        List<UUID> owners = state.worldLimitOwners.get(itemId);
        if (owners != null && owners.remove(uuid)) {
            save();
        }
    }

    // Cleans up ownership if owners are inactive (no login for 14 days OR < 4 hours playtime in last 14 days)
    public static void updateWorldLimits(long currentTime, Map<String, Integer> configuredLimits, Set<UUID> onlinePlayerUUIDs) {
        boolean changed = false;
        for (Map.Entry<String, List<UUID>> entry : state.worldLimitOwners.entrySet()) {
            String itemId = entry.getKey();
            List<UUID> owners = entry.getValue();
            
            // If the item is no longer world-limited, free everyone
            if (!configuredLimits.containsKey(itemId)) {
                if (!owners.isEmpty()) {
                    owners.clear();
                    changed = true;
                }
                continue;
            }

            Iterator<UUID> iterator = owners.iterator();
            while (iterator.hasNext()) {
                UUID ownerUuid = iterator.next();
                PlayerData ownerData = state.playersData.get(ownerUuid);
                
                if (ownerData == null) {
                    // No data, remove ownership
                    iterator.remove();
                    changed = true;
                    continue;
                }

                boolean isOnline = onlinePlayerUUIDs.contains(ownerUuid);
                long lastActivity = isOnline ? currentTime : ownerData.lastSeenTime;
                
                // 1. Check 14 days offline limit (inactivity)
                boolean offlineTooLong = (currentTime - lastActivity) > (14L * 24 * 60 * 60 * 1000);
                
                // 2. Check 4 hours playtime limit in last 14 days (4 hours = 14,400,000 ms)
                long playtime = ownerData.getPlaytimeInLast14Days(currentTime, isOnline);
                boolean lowPlaytime = playtime < (4L * 60 * 60 * 1000);

                if (offlineTooLong || lowPlaytime) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) {
            save();
        }
    }

    public static Map<UUID, PlayerData> getAllPlayersData() {
        return state.playersData;
    }

    public static Map<String, List<UUID>> getWorldLimitOwners() {
        return state.worldLimitOwners;
    }
}
