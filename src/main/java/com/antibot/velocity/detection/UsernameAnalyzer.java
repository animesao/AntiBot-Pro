package com.antibot.velocity.detection;

import java.util.*;
import java.util.regex.Pattern;

public class UsernameAnalyzer {

    private static final Set<String> BLOCKED_NAMES = new HashSet<>(Arrays.asList(
        "bot", "bots", "player", "players", "user", "users",
        "test", "testing", "admin", "administrator", "moderator",
        "owner", "console", "server", "hacker", "hack", "cheat",
        "cheater", "exploit", "exploiter", "crash", "crasher",
        "grief", "griefer", "spam", "spammer", "lag", "lagger",
        "ddos", "ddoser", "attack", "attacker", "nuke", "nuker",
        "destroy", "destroyer", "kill", "killer", "troll", "trolling"
    ));

    private static final List<Pattern> BOT_NAME_PATTERNS = Arrays.asList(
        Pattern.compile("^[A-Z][a-z]+\\d{3,}$"),
        Pattern.compile("^[a-zA-Z]+_\\d{4,}$"),
        Pattern.compile("^Player\\d+$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^User\\d+$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^Bot\\d*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^[a-z]{3,5}\\d{5,}$"),
        Pattern.compile("^\\d+[a-zA-Z]+\\d+$"),
        Pattern.compile("^[A-Z]{2,}\\d{2,}[A-Z]{2,}$"),
        Pattern.compile("^[a-z]+[A-Z]+[a-z]+\\d{3,}$"),
        Pattern.compile("^[a-zA-Z]\\d{6,}$"),
        Pattern.compile("^\\w*[xX]{2,}\\w*$"),
        Pattern.compile("^_+[a-zA-Z0-9]+_+$"),
        Pattern.compile("^[a-zA-Z]{1,2}\\d{4,}[a-zA-Z]{1,2}$"),
        Pattern.compile("^(aa|bb|cc|dd|ee|ff|gg|hh|ii|jj|kk|ll|mm|nn|oo|pp|qq|rr|ss|tt|uu|vv|ww|xx|yy|zz)+\\d*$", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> SUSPICIOUS_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\d{5,}.*"),
        Pattern.compile("^[a-z]{10,}$"),
        Pattern.compile("^[A-Z]{10,}$"),
        Pattern.compile("^[0-9]+$"),
        Pattern.compile("^_+.*_+$"),
        Pattern.compile(".*_{3,}.*"),
        Pattern.compile("^(.)\\1{4,}.*"),
        Pattern.compile(".*hack.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*cheat.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*bot.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*crash.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*grief.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*ddos.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*exploit.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*nuke.*", Pattern.CASE_INSENSITIVE)
    );

    public static class AnalysisResult {
        private final boolean suspicious;
        private final int riskScore;
        private final List<String> reasons;
        private final RiskLevel riskLevel;

        public AnalysisResult(boolean suspicious, int riskScore, List<String> reasons, RiskLevel riskLevel) {
            this.suspicious = suspicious;
            this.riskScore = riskScore;
            this.reasons = reasons;
            this.riskLevel = riskLevel;
        }

        public boolean isSuspicious() { return suspicious; }
        public int getRiskScore() { return riskScore; }
        public List<String> getReasons() { return reasons; }
        public RiskLevel getRiskLevel() { return riskLevel; }
    }

    public enum RiskLevel {
        SAFE(0),
        LOW(25),
        MEDIUM(50),
        HIGH(75),
        CRITICAL(100);

        private final int threshold;

        RiskLevel(int threshold) {
            this.threshold = threshold;
        }

        public int getThreshold() { return threshold; }
    }

    public AnalysisResult analyze(String username) {
        if (username == null || username.isEmpty()) {
            return new AnalysisResult(true, 100, 
                Collections.singletonList("Пустой никнейм"), RiskLevel.CRITICAL);
        }

        List<String> reasons = new ArrayList<>();
        int riskScore = 0;

        if (username.length() < 3) {
            riskScore += 40;
            reasons.add("Слишком короткий никнейм");
        }

        if (username.length() > 16) {
            riskScore += 50;
            reasons.add("Слишком длинный никнейм");
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            riskScore += 60;
            reasons.add("Недопустимые символы в никнейме");
        }

        String lowerName = username.toLowerCase();
        for (String blocked : BLOCKED_NAMES) {
            if (lowerName.contains(blocked)) {
                riskScore += 30;
                reasons.add("Содержит запрещенное слово: " + blocked);
            }
        }

        for (Pattern pattern : BOT_NAME_PATTERNS) {
            if (pattern.matcher(username).matches()) {
                riskScore += 50;
                reasons.add("Соответствует паттерну бот-ника");
                break;
            }
        }

        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(username).matches()) {
                riskScore += 25;
                reasons.add("Подозрительный паттерн");
            }
        }

        int digitCount = 0;
        for (char c : username.toCharArray()) {
            if (Character.isDigit(c)) digitCount++;
        }
        float digitRatio = (float) digitCount / username.length();
        if (digitRatio > 0.5) {
            riskScore += 35;
            reasons.add("Слишком много цифр в никнейме");
        }

        int underscoreCount = 0;
        for (char c : username.toCharArray()) {
            if (c == '_') underscoreCount++;
        }
        if (underscoreCount > 3) {
            riskScore += 20;
            reasons.add("Слишком много подчеркиваний");
        }

        if (hasRepetitivePattern(username)) {
            riskScore += 25;
            reasons.add("Повторяющийся паттерн");
        }

        double entropy = calculateEntropy(username);
        if (entropy < 2.0 && username.length() > 6) {
            riskScore += 20;
            reasons.add("Низкая энтропия (автогенерированный ник)");
        }

        riskScore = Math.min(100, riskScore);

        RiskLevel riskLevel;
        if (riskScore >= 75) riskLevel = RiskLevel.CRITICAL;
        else if (riskScore >= 50) riskLevel = RiskLevel.HIGH;
        else if (riskScore >= 25) riskLevel = RiskLevel.MEDIUM;
        else if (riskScore > 0) riskLevel = RiskLevel.LOW;
        else riskLevel = RiskLevel.SAFE;

        return new AnalysisResult(riskScore >= 50, riskScore, reasons, riskLevel);
    }

    private boolean hasRepetitivePattern(String str) {
        if (str.length() < 4) return false;
        
        for (int len = 1; len <= str.length() / 2; len++) {
            String pattern = str.substring(0, len);
            StringBuilder repeated = new StringBuilder();
            for (int i = 0; i < str.length() / len; i++) {
                repeated.append(pattern);
            }
            if (str.startsWith(repeated.toString()) && repeated.length() >= str.length() - len) {
                return true;
            }
        }
        return false;
    }

    private double calculateEntropy(String str) {
        if (str == null || str.isEmpty()) return 0;
        
        Map<Character, Integer> charCount = new HashMap<>();
        for (char c : str.toCharArray()) {
            charCount.merge(c, 1, Integer::sum);
        }
        
        double entropy = 0;
        int length = str.length();
        for (int count : charCount.values()) {
            double probability = (double) count / length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        return entropy;
    }
}
