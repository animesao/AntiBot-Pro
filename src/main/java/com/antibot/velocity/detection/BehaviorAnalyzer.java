package com.antibot.velocity.detection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BehaviorAnalyzer {

    private final Map<String, PlayerBehavior> playerBehaviors = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> connectionPatterns = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedVerifications = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPingTimes = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> chatTimestamps = new ConcurrentHashMap<>();

    public static class PlayerBehavior {
        private final String playerName;
        private final String ip;
        private final long firstSeen;
        private final List<Long> connectionTimes = new CopyOnWriteArrayList<>();
        private final List<Long> disconnectionTimes = new CopyOnWriteArrayList<>();
        private final List<String> usedNames = new CopyOnWriteArrayList<>();
        private int totalConnections = 0;
        private int suspiciousActions = 0;
        private long totalOnlineTime = 0;
        private long lastConnectionTime = 0;
        private int fastReconnects = 0;
        private boolean verified = false;

        public PlayerBehavior(String playerName, String ip) {
            this.playerName = playerName;
            this.ip = ip;
            this.firstSeen = System.currentTimeMillis();
        }

        public void recordConnection() {
            long now = System.currentTimeMillis();
            connectionTimes.add(now);
            totalConnections++;
            
            if (lastConnectionTime > 0 && (now - lastConnectionTime) < 5000) {
                fastReconnects++;
            }
            lastConnectionTime = now;
        }

        public void recordDisconnection() {
            long now = System.currentTimeMillis();
            disconnectionTimes.add(now);
            
            if (!connectionTimes.isEmpty()) {
                Long lastConn = connectionTimes.get(connectionTimes.size() - 1);
                totalOnlineTime += (now - lastConn);
            }
        }

        public void addUsedName(String name) {
            if (!usedNames.contains(name)) {
                usedNames.add(name);
            }
        }

        public void incrementSuspiciousActions() {
            suspiciousActions++;
        }

        public void setVerified(boolean verified) {
            this.verified = verified;
        }

        public String getPlayerName() { return playerName; }
        public String getIp() { return ip; }
        public long getFirstSeen() { return firstSeen; }
        public int getTotalConnections() { return totalConnections; }
        public int getSuspiciousActions() { return suspiciousActions; }
        public long getTotalOnlineTime() { return totalOnlineTime; }
        public int getFastReconnects() { return fastReconnects; }
        public List<String> getUsedNames() { return usedNames; }
        public boolean isVerified() { return verified; }
        public long getLastConnectionTime() { return lastConnectionTime; }
    }

    public static class AnalysisResult {
        private final int riskScore;
        private final boolean isSuspicious;
        private final List<String> flags;
        private final BehaviorType behaviorType;

        public AnalysisResult(int riskScore, boolean isSuspicious, List<String> flags, BehaviorType behaviorType) {
            this.riskScore = riskScore;
            this.isSuspicious = isSuspicious;
            this.flags = flags;
            this.behaviorType = behaviorType;
        }

        public int getRiskScore() { return riskScore; }
        public boolean isSuspicious() { return isSuspicious; }
        public List<String> getFlags() { return flags; }
        public BehaviorType getBehaviorType() { return behaviorType; }
    }

    public enum BehaviorType {
        NORMAL,
        BOT_LIKE,
        SPAM_LIKE,
        ATTACK_LIKE,
        SUSPICIOUS
    }

    public PlayerBehavior getOrCreateBehavior(String playerName, String ip) {
        String key = ip + ":" + playerName;
        return playerBehaviors.computeIfAbsent(key, k -> new PlayerBehavior(playerName, ip));
    }

    public void recordConnection(String playerName, String ip) {
        PlayerBehavior behavior = getOrCreateBehavior(playerName, ip);
        behavior.recordConnection();
        behavior.addUsedName(playerName);

        connectionPatterns.computeIfAbsent(ip, k -> new CopyOnWriteArrayList<>())
            .add(System.currentTimeMillis());
    }

    public void recordDisconnection(String playerName, String ip) {
        PlayerBehavior behavior = getOrCreateBehavior(playerName, ip);
        behavior.recordDisconnection();
    }

    public void recordFailedVerification(String ip) {
        failedVerifications.merge(ip, 1, Integer::sum);
    }

    public void recordSuccessfulVerification(String playerName, String ip) {
        PlayerBehavior behavior = getOrCreateBehavior(playerName, ip);
        behavior.setVerified(true);
        failedVerifications.remove(ip);
    }

    public AnalysisResult analyze(String playerName, String ip) {
        List<String> flags = new ArrayList<>();
        int riskScore = 0;

        PlayerBehavior behavior = playerBehaviors.get(ip + ":" + playerName);
        
        List<Long> connections = connectionPatterns.get(ip);
        if (connections != null) {
            int recentConnections = countRecentEvents(connections, 60000);
            if (recentConnections > 5) {
                riskScore += 30;
                flags.add("MANY_CONNECTIONS_PER_MINUTE: " + recentConnections);
            }

            int veryRecentConnections = countRecentEvents(connections, 10000);
            if (veryRecentConnections > 3) {
                riskScore += 40;
                flags.add("RAPID_CONNECTIONS: " + veryRecentConnections + " in 10s");
            }
        }

        Integer failures = failedVerifications.get(ip);
        if (failures != null && failures > 2) {
            riskScore += 25 * failures;
            flags.add("FAILED_VERIFICATIONS: " + failures);
        }

        if (behavior != null) {
            if (behavior.getFastReconnects() > 3) {
                riskScore += 35;
                flags.add("FAST_RECONNECTS: " + behavior.getFastReconnects());
            }

            if (behavior.getUsedNames().size() > 3) {
                riskScore += 40;
                flags.add("MULTIPLE_NAMES: " + behavior.getUsedNames().size());
            }

            if (behavior.getSuspiciousActions() > 5) {
                riskScore += 30;
                flags.add("SUSPICIOUS_ACTIONS: " + behavior.getSuspiciousActions());
            }

            long avgSessionTime = behavior.getTotalConnections() > 0 
                ? behavior.getTotalOnlineTime() / behavior.getTotalConnections() 
                : 0;
            if (avgSessionTime > 0 && avgSessionTime < 5000 && behavior.getTotalConnections() > 3) {
                riskScore += 45;
                flags.add("VERY_SHORT_SESSIONS: avg " + (avgSessionTime / 1000) + "s");
            }

            if (!behavior.isVerified() && behavior.getTotalConnections() > 5) {
                riskScore += 20;
                flags.add("NEVER_VERIFIED");
            }
        }

        if (checkBotPattern(ip)) {
            riskScore += 50;
            flags.add("BOT_PATTERN_DETECTED");
        }

        riskScore = Math.min(100, riskScore);

        BehaviorType behaviorType;
        if (riskScore >= 80) behaviorType = BehaviorType.ATTACK_LIKE;
        else if (riskScore >= 60) behaviorType = BehaviorType.BOT_LIKE;
        else if (riskScore >= 40) behaviorType = BehaviorType.SUSPICIOUS;
        else if (riskScore >= 20) behaviorType = BehaviorType.SPAM_LIKE;
        else behaviorType = BehaviorType.NORMAL;

        return new AnalysisResult(riskScore, riskScore >= 50, flags, behaviorType);
    }

    private int countRecentEvents(List<Long> events, long windowMs) {
        long cutoff = System.currentTimeMillis() - windowMs;
        int count = 0;
        for (Long time : events) {
            if (time > cutoff) count++;
        }
        return count;
    }

    private boolean checkBotPattern(String ip) {
        List<Long> connections = connectionPatterns.get(ip);
        if (connections == null || connections.size() < 5) return false;

        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < connections.size(); i++) {
            intervals.add(connections.get(i) - connections.get(i - 1));
        }

        if (intervals.isEmpty()) return false;

        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = intervals.stream()
            .mapToDouble(i -> Math.pow(i - mean, 2))
            .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        return stdDev < 500 && mean < 2000;
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - (60 * 60 * 1000);

        connectionPatterns.values().forEach(list -> 
            list.removeIf(time -> time < cutoff));
        connectionPatterns.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        playerBehaviors.entrySet().removeIf(entry -> 
            entry.getValue().getLastConnectionTime() < cutoff);

        chatTimestamps.values().forEach(list -> 
            list.removeIf(time -> time < cutoff));
        chatTimestamps.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public Map<String, PlayerBehavior> getAllBehaviors() {
        return new HashMap<>(playerBehaviors);
    }

    public int getTotalTrackedPlayers() {
        return playerBehaviors.size();
    }
}
