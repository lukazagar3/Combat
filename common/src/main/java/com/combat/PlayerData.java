package com.combat;

import java.util.HashMap;
import java.util.Map;

public class PlayerData {
    public String rank = "unranked";
    public int rankPosition = -1; // -1 for unranked, 1 for #1, etc.
    public Map<String, Long> dailyPlaytimeMs = new HashMap<>(); // "yyyy-MM-dd" -> playtime in ms
    public long lastLoginTime = 0;
    public long lastSeenTime = 0; // Epoch timestamp of last login/logout/tick
    
    // Check if player has at least 4 hours (14,400,000 ms) of playtime in the last 14 days.
    // If they are currently online, current session playtime is also added to the count.
    public long getPlaytimeInLast14Days(long currentTime, boolean isOnline) {
        long totalPlaytime = 0;
        long fourteenDaysAgo = currentTime - (14L * 24 * 60 * 60 * 1000);
        
        // Sum from daily history
        for (Map.Entry<String, Long> entry : dailyPlaytimeMs.entrySet()) {
            try {
                long dateMillis = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(entry.getKey()).getTime();
                if (dateMillis >= fourteenDaysAgo) {
                    totalPlaytime += entry.getValue();
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Include current session if online
        if (isOnline && lastLoginTime > 0 && currentTime > lastLoginTime) {
            totalPlaytime += (currentTime - lastLoginTime);
        }
        
        return totalPlaytime;
    }
}
