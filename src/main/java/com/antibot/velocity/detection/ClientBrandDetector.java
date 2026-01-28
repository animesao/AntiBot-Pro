package com.antibot.velocity.detection;

import java.util.*;
import java.util.regex.Pattern;

public class ClientBrandDetector {

    private static final Set<String> CHEAT_CLIENT_BRANDS = new HashSet<>(Arrays.asList(
        "beerclient", "beer", "beerus",
        "wurst", "wurstclient",
        "impact", "impactclient",
        "aristois", "aristoisclient",
        "inertia", "inertiaclient",
        "meteor", "meteorclient",
        "rusherhack", "rusher",
        "future", "futureclient",
        "konas", "konasclient",
        "phobos", "phobosclient",
        "salhack", "sal",
        "kami", "kamiblue",
        "lambda", "lambdaclient",
        "liquidbounce", "liquid",
        "sigma", "sigmaclient",
        "novoline", "novolineclient",
        "exhibition", "exhibitionclient",
        "remix", "remixclient",
        "rise", "riseclient",
        "moon", "moonclient",
        "tenacity", "tenacityclient",
        "vape", "vapeclient", "vapev4",
        "intent", "intentclient",
        "antic", "anticclient",
        "astolfo", "astolfoclient",
        "drip", "dripclient",
        "azura", "azuraclient",
        "entropy", "entropyclient",
        "hanabi", "hanabiclient",
        "zeroday", "zerodayclient",
        "ares", "aresclient",
        "skilled", "skilledclient",
        "flux", "fluxclient",
        "autumn", "autumnclient",
        "azura", "azuraclient",
        "crystalaura", "crystal",
        "skid", "skidclient",
        "hack", "hacked", "hackclient",
        "cheat", "cheats", "cheater",
        "crack", "cracked", "crackedclient",
        "free", "freeclient",
        "bot", "botclient",
        "mird", "mirdclient"
    ));

    private static final List<Pattern> SUSPICIOUS_BRAND_PATTERNS = Arrays.asList(
        Pattern.compile(".*hack.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*cheat.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*client.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*crack.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*bot.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*exploit.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*inject.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*mod.*menu.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*bypass.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*x-ray.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*killaura.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*aimbot.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*speed.*hack.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*fly.*hack.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*nuker.*", Pattern.CASE_INSENSITIVE)
    );

    private static final Set<String> LEGITIMATE_BRANDS = new HashSet<>(Arrays.asList(
        "vanilla", "forge", "fabric", "optifine", "liteloader",
        "lunarclient", "lunar", "badlion", "badlionclient", "blc",
        "labymod", "feather", "pvplounge", "cheatbreaker",
        "minecraft", "fml,forge", "fml", "quilt"
    ));

    public static class DetectionResult {
        private final boolean detected;
        private final String clientName;
        private final DetectionType type;
        private final String reason;

        public DetectionResult(boolean detected, String clientName, DetectionType type, String reason) {
            this.detected = detected;
            this.clientName = clientName;
            this.type = type;
            this.reason = reason;
        }

        public boolean isDetected() { return detected; }
        public String getClientName() { return clientName; }
        public DetectionType getType() { return type; }
        public String getReason() { return reason; }
    }

    public enum DetectionType {
        KNOWN_CHEAT_CLIENT,
        SUSPICIOUS_PATTERN,
        UNKNOWN_CLIENT,
        MODIFIED_VANILLA,
        CLEAN
    }

    public DetectionResult analyze(String clientBrand) {
        if (clientBrand == null || clientBrand.isEmpty()) {
            return new DetectionResult(true, "Unknown", DetectionType.UNKNOWN_CLIENT, 
                "Клиент не отправил бренд");
        }

        String normalizedBrand = clientBrand.toLowerCase().trim()
            .replaceAll("[^a-z0-9]", "");

        if (isLegitimateClient(normalizedBrand)) {
            return new DetectionResult(false, clientBrand, DetectionType.CLEAN, "Легитимный клиент");
        }

        for (String cheatBrand : CHEAT_CLIENT_BRANDS) {
            if (normalizedBrand.contains(cheatBrand) || cheatBrand.contains(normalizedBrand)) {
                return new DetectionResult(true, clientBrand, DetectionType.KNOWN_CHEAT_CLIENT,
                    "Обнаружен известный читерский клиент: " + clientBrand);
            }
        }

        for (Pattern pattern : SUSPICIOUS_BRAND_PATTERNS) {
            if (pattern.matcher(clientBrand).matches()) {
                return new DetectionResult(true, clientBrand, DetectionType.SUSPICIOUS_PATTERN,
                    "Подозрительный паттерн в названии клиента: " + clientBrand);
            }
        }

        if (!isKnownClient(normalizedBrand)) {
            return new DetectionResult(false, clientBrand, DetectionType.UNKNOWN_CLIENT,
                "Неизвестный клиент (требует проверки): " + clientBrand);
        }

        return new DetectionResult(false, clientBrand, DetectionType.CLEAN, "Клиент прошел проверку");
    }

    private boolean isLegitimateClient(String brand) {
        for (String legit : LEGITIMATE_BRANDS) {
            if (brand.contains(legit) || legit.contains(brand)) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnownClient(String brand) {
        return LEGITIMATE_BRANDS.contains(brand) || brand.equals("vanilla") || brand.isEmpty();
    }

    public void addCheatClientBrand(String brand) {
        CHEAT_CLIENT_BRANDS.add(brand.toLowerCase().trim());
    }

    public void addLegitimateClientBrand(String brand) {
        LEGITIMATE_BRANDS.add(brand.toLowerCase().trim());
    }

    public Set<String> getCheatClientBrands() {
        return Collections.unmodifiableSet(CHEAT_CLIENT_BRANDS);
    }
}
