package com.antibot.velocity.verification;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CaptchaVerification {

    private final Map<String, VerificationSession> pendingSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> verifiedPlayers = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();

    private static final long SESSION_TIMEOUT = TimeUnit.MINUTES.toMillis(5);
    private static final long VERIFIED_DURATION = TimeUnit.HOURS.toMillis(24);
    private static final int MAX_ATTEMPTS = 3;

    public static class VerificationSession {
        private final String playerName;
        private final String ip;
        private final String correctAnswer;
        private final String question;
        private final long createdAt;
        private int attempts;

        public VerificationSession(String playerName, String ip, String correctAnswer, String question) {
            this.playerName = playerName;
            this.ip = ip;
            this.correctAnswer = correctAnswer;
            this.question = question;
            this.createdAt = System.currentTimeMillis();
            this.attempts = 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > SESSION_TIMEOUT;
        }

        public String getPlayerName() { return playerName; }
        public String getIp() { return ip; }
        public String getCorrectAnswer() { return correctAnswer; }
        public String getQuestion() { return question; }
        public int getAttempts() { return attempts; }
        public void incrementAttempts() { attempts++; }
    }

    public enum VerificationType {
        MATH_SIMPLE,
        MATH_COMPLEX,
        COLOR_WORD,
        REVERSE_TEXT
    }

    private final Random random = new Random();

    public VerificationSession createSession(String playerName, String ip, VerificationType type) {
        String question;
        String answer;

        switch (type) {
            case MATH_SIMPLE:
                int a = random.nextInt(10) + 1;
                int b = random.nextInt(10) + 1;
                question = "Решите: " + a + " + " + b + " = ?";
                answer = String.valueOf(a + b);
                break;

            case MATH_COMPLEX:
                int x = random.nextInt(20) + 1;
                int y = random.nextInt(10) + 1;
                boolean multiply = random.nextBoolean();
                if (multiply) {
                    question = "Решите: " + x + " * " + y + " = ?";
                    answer = String.valueOf(x * y);
                } else {
                    question = "Решите: " + (x + y) + " - " + y + " = ?";
                    answer = String.valueOf(x);
                }
                break;

            case COLOR_WORD:
                String[] colors = {"красный", "синий", "зеленый", "желтый", "белый", "черный"};
                String[] colorCodes = {"red", "blue", "green", "yellow", "white", "black"};
                int colorIdx = random.nextInt(colors.length);
                question = "Введите слово '" + colors[colorIdx] + "' на английском:";
                answer = colorCodes[colorIdx];
                break;

            case REVERSE_TEXT:
            default:
                String[] words = {"minecraft", "server", "player", "game", "world"};
                String word = words[random.nextInt(words.length)];
                question = "Напишите слово '" + new StringBuilder(word).reverse().toString() + "' наоборот:";
                answer = word;
                break;
        }

        VerificationSession session = new VerificationSession(playerName, ip, answer, question);
        String key = ip + ":" + playerName;
        pendingSessions.put(key, session);

        return session;
    }

    public boolean verify(String playerName, String ip, String providedAnswer) {
        String key = ip + ":" + playerName;
        VerificationSession session = pendingSessions.get(key);

        if (session == null || session.isExpired()) {
            pendingSessions.remove(key);
            return false;
        }

        session.incrementAttempts();

        if (session.getCorrectAnswer().equalsIgnoreCase(providedAnswer.trim())) {
            pendingSessions.remove(key);
            verifiedPlayers.put(key, System.currentTimeMillis() + VERIFIED_DURATION);
            failedAttempts.remove(ip);
            return true;
        }

        if (session.getAttempts() >= MAX_ATTEMPTS) {
            pendingSessions.remove(key);
            failedAttempts.merge(ip, 1, Integer::sum);
        }

        return false;
    }

    public boolean isVerified(String playerName, String ip) {
        String key = ip + ":" + playerName;
        Long verifiedUntil = verifiedPlayers.get(key);
        
        if (verifiedUntil == null) return false;
        
        if (System.currentTimeMillis() > verifiedUntil) {
            verifiedPlayers.remove(key);
            return false;
        }
        
        return true;
    }

    public boolean hasPendingSession(String playerName, String ip) {
        String key = ip + ":" + playerName;
        VerificationSession session = pendingSessions.get(key);
        
        if (session != null && session.isExpired()) {
            pendingSessions.remove(key);
            return false;
        }
        
        return session != null;
    }

    public VerificationSession getSession(String playerName, String ip) {
        String key = ip + ":" + playerName;
        VerificationSession session = pendingSessions.get(key);
        
        if (session != null && session.isExpired()) {
            pendingSessions.remove(key);
            return null;
        }
        
        return session;
    }

    public int getFailedAttempts(String ip) {
        return failedAttempts.getOrDefault(ip, 0);
    }

    public boolean isBlocked(String ip) {
        return failedAttempts.getOrDefault(ip, 0) >= 5;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        
        pendingSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
        verifiedPlayers.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    public void revokeVerification(String playerName, String ip) {
        String key = ip + ":" + playerName;
        verifiedPlayers.remove(key);
    }

    public int getPendingSessionsCount() {
        return pendingSessions.size();
    }

    public int getVerifiedPlayersCount() {
        return verifiedPlayers.size();
    }
}
