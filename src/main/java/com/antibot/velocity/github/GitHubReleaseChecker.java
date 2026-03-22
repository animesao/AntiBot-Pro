package com.antibot.velocity.github;

import com.antibot.velocity.AntiBotPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GitHubReleaseChecker {

    private static final Logger logger = LoggerFactory.getLogger(GitHubReleaseChecker.class);
    private static final Gson gson = new GsonBuilder().create();
    private static final String GITHUB_API = "https://api.github.com";
    private static final String OWNER = "animesao";
    private static final String REPO = "AntiBot-Pro";
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "GitHub-Release-Checker");
        t.setDaemon(true);
        return t;
    });

    private volatile String latestVersion;
    private volatile String latestVersionUrl;
    private volatile String latestVersionBody;
    private volatile long lastCheckTime = 0;
    private volatile boolean updateAvailable = false;
    private volatile boolean checking = false;
    private volatile boolean checkedOnce = false;
    private static final long CHECK_INTERVAL = TimeUnit.HOURS.toMillis(6);

    public GitHubReleaseChecker() {
    }

    public String getCurrentVersion() {
        return AntiBotPlugin.getVersion();
    }

    public void checkForUpdates() {
        if (checking) return;
        checking = true;

        CompletableFuture.runAsync(() -> {
            try {
                fetchLatestRelease();
                lastCheckTime = System.currentTimeMillis();
                checking = false;
            } catch (Exception e) {
                logger.warn("Не удалось проверить обновления: {}", e.getMessage());
                checking = false;
            }
        }, executor);
    }

    public void checkForUpdatesAsync(Runnable onComplete) {
        CompletableFuture.runAsync(() -> {
            checkForUpdates();
            if (onComplete != null) {
                onComplete.run();
            }
        }, executor);
    }

    private void fetchLatestRelease() throws IOException {
        String apiUrl = GITHUB_API + "/repos/" + OWNER + "/" + REPO + "/releases/latest";
        
        URL url = new URL(apiUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "AntiBot-Pro/" + getCurrentVersion());
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        int responseCode = connection.getResponseCode();
        
        if (responseCode == 200) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                parseReleaseResponse(response.toString());
            }
        } else if (responseCode == 404) {
            logger.info("Релизы не найдены в репозитории");
        } else {
            logger.warn("GitHub API вернул код: {}", responseCode);
        }
        
        connection.disconnect();
    }

    private void parseReleaseResponse(String json) {
        try {
            JsonObject release = gson.fromJson(json, JsonObject.class);
            
            latestVersion = release.get("tag_name").getAsString();
            if (latestVersion.startsWith("v") || latestVersion.startsWith("V")) {
                latestVersion = latestVersion.substring(1);
            }
            
            latestVersionUrl = release.get("html_url").getAsString();
            
            if (release.has("body")) {
                latestVersionBody = release.get("body").getAsString();
            }
            
            updateAvailable = isNewerVersion(latestVersion, getCurrentVersion());
            
            if (updateAvailable) {
                logger.info("═══════════════════════════════════════════════════════════");
                logger.info("  ДОСТУПНО ОБНОВЛЕНИЕ!");
                logger.info("  Текущая версия: v{}", getCurrentVersion());
                logger.info("  Новая версия:   v{}", latestVersion);
                logger.info("  {}", latestVersionUrl);
                logger.info("═══════════════════════════════════════════════════════════");
            } else {
                logger.info("AntiBot Pro v{} - у вас последняя версия", getCurrentVersion());
            }
        } catch (Exception e) {
            logger.error("Ошибка парсинга ответа GitHub: {}", e.getMessage());
        }
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            
            int maxLength = Math.max(latestParts.length, currentParts.length);
            
            for (int i = 0; i < maxLength; i++) {
                int latestNum = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
                int currentNum = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
                
                if (latestNum > currentNum) return true;
                if (latestNum < currentNum) return false;
            }
        } catch (Exception e) {
            logger.warn("Ошибка сравнения версий: {}", e.getMessage());
        }
        return false;
    }

    private int parseVersionPart(String part) {
        StringBuilder num = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                break;
            }
        }
        return num.length() > 0 ? Integer.parseInt(num.toString()) : 0;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getLatestVersionUrl() {
        return latestVersionUrl;
    }

    public String getLatestVersionBody() {
        return latestVersionBody;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public boolean shouldCheckAgain() {
        return System.currentTimeMillis() - lastCheckTime > CHECK_INTERVAL;
    }

    public String getChangelogSummary() {
        if (latestVersionBody == null || latestVersionBody.isEmpty()) {
            return null;
        }
        
        StringBuilder summary = new StringBuilder();
        String[] lines = latestVersionBody.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#")) continue;
            if (trimmed.startsWith("##")) {
                summary.append("\n");
                summary.append(trimmed.replace("##", "").trim());
                summary.append(":\n");
            } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                summary.append("  ").append(trimmed.substring(1).trim()).append("\n");
            } else if (summary.length() > 0) {
                if (summary.length() > 500) break;
                summary.append(trimmed).append(" ");
            }
        }
        
        return summary.length() > 0 ? summary.toString().trim() : null;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
