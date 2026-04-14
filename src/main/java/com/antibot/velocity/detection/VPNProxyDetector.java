package com.antibot.velocity.detection;

import com.antibot.velocity.util.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VPNProxyDetector {

    private static final Logger logger = LoggerFactory.getLogger(VPNProxyDetector.class);
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = TimeUnit.HOURS.toMillis(6);
    private final RateLimiter rateLimiter;
    
    private static final Set<String> KNOWN_VPN_ASN_PREFIXES = new HashSet<>(Arrays.asList(
        "AS9009", "AS20473", "AS14061", "AS16276", "AS24940", "AS60068",
        "AS212238", "AS209", "AS24961", "AS51167", "AS136787", "AS45102",
        "AS6939", "AS15169", "AS8075", "AS16509", "AS14618", "AS396982"
    ));

    private static final Set<String> DATACENTER_ASNS = new HashSet<>(Arrays.asList(
        "AS16276", "AS24940", "AS20473", "AS14061", "AS63949", "AS51167",
        "AS46562", "AS62567", "AS395954", "AS55286", "AS62785", "AS200651",
        "AS60068", "AS59253", "AS9009", "AS51852", "AS204957", "AS136787"
    ));

    private static final Set<String> BLOCKED_IP_RANGES = new HashSet<>();

    public VPNProxyDetector() {
        this(100); // По умолчанию 100 запросов/минуту
    }

    public VPNProxyDetector(int requestsPerMinute) {
        this.rateLimiter = new RateLimiter(requestsPerMinute);
    }

    public static class DetectionResult {
        private final boolean isProxy;
        private final boolean isVPN;
        private final boolean isDatacenter;
        private final boolean isTor;
        private final int riskScore;
        private final String countryCode;
        private final String asn;
        private final String isp;
        private final List<String> flags;

        public DetectionResult(boolean isProxy, boolean isVPN, boolean isDatacenter, boolean isTor,
                               int riskScore, String countryCode, String asn, String isp, List<String> flags) {
            this.isProxy = isProxy;
            this.isVPN = isVPN;
            this.isDatacenter = isDatacenter;
            this.isTor = isTor;
            this.riskScore = riskScore;
            this.countryCode = countryCode;
            this.asn = asn;
            this.isp = isp;
            this.flags = flags;
        }

        public boolean isProxy() { return isProxy; }
        public boolean isVPN() { return isVPN; }
        public boolean isDatacenter() { return isDatacenter; }
        public boolean isTor() { return isTor; }
        public boolean isSuspicious() { return isProxy || isVPN || isDatacenter || isTor; }
        public int getRiskScore() { return riskScore; }
        public String getCountryCode() { return countryCode; }
        public String getAsn() { return asn; }
        public String getIsp() { return isp; }
        public List<String> getFlags() { return flags; }
    }

    private static class CachedResult {
        final DetectionResult result;
        final long timestamp;

        CachedResult(DetectionResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }

    public DetectionResult checkIP(String ip, String apiKey) {
        CachedResult cached = cache.get(ip);
        if (cached != null && !cached.isExpired()) {
            return cached.result;
        }

        DetectionResult result;
        
        if (apiKey != null && !apiKey.isEmpty()) {
            result = checkWithAPI(ip, apiKey);
        } else {
            result = performBasicCheck(ip);
        }

        cache.put(ip, new CachedResult(result));
        return result;
    }

    /**
     * Асинхронная проверка IP с rate limiting
     */
    public CompletableFuture<DetectionResult> checkIPAsync(String ip, String apiKey) {
        return CompletableFuture.supplyAsync(() -> {
            // Проверка кэша
            CachedResult cached = cache.get(ip);
            if (cached != null && !cached.isExpired()) {
                return cached.result;
            }

            DetectionResult result;
            
            if (apiKey != null && !apiKey.isEmpty()) {
                // Rate limiting для API
                if (!rateLimiter.tryAcquire("vpn", 5000)) {
                    logger.warn("VPN API rate limit exceeded for IP: {}", ip);
                    result = performBasicCheck(ip);
                } else {
                    result = checkWithAPI(ip, apiKey);
                }
            } else {
                result = performBasicCheck(ip);
            }

            cache.put(ip, new CachedResult(result));
            return result;
        });
    }

    private DetectionResult checkWithAPI(String ip, String apiKey) {
        try {
            URL url = new URL("https://proxycheck.io/v2/" + ip + "?key=" + apiKey + "&vpn=1&asn=1&risk=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "AntiBot-Pro/2.3.0");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return parseAPIResponse(response.toString(), ip);
            }
        } catch (Exception e) {
            // API failed, use basic check
        }

        return performBasicCheck(ip);
    }

    private DetectionResult parseAPIResponse(String json, String ip) {
        List<String> flags = new ArrayList<>();
        boolean isProxy = json.contains("\"proxy\":\"yes\"");
        boolean isVPN = json.contains("\"type\":\"VPN\"") || json.contains("\"vpn\":\"yes\"");
        boolean isTor = json.contains("\"type\":\"TOR\"");
        boolean isDatacenter = json.contains("\"type\":\"Hosting\"") || json.contains("\"type\":\"Data Center\"");

        int riskScore = 0;
        try {
            int riskStart = json.indexOf("\"risk\":");
            if (riskStart != -1) {
                int riskEnd = json.indexOf(",", riskStart);
                if (riskEnd == -1) riskEnd = json.indexOf("}", riskStart);
                String riskStr = json.substring(riskStart + 7, riskEnd).trim().replace("\"", "");
                riskScore = Integer.parseInt(riskStr);
            }
        } catch (Exception ignored) {}

        String countryCode = extractValue(json, "isocode");
        String asn = extractValue(json, "asn");
        String isp = extractValue(json, "provider");

        if (isProxy) flags.add("PROXY");
        if (isVPN) flags.add("VPN");
        if (isTor) flags.add("TOR");
        if (isDatacenter) flags.add("DATACENTER");

        return new DetectionResult(isProxy, isVPN, isDatacenter, isTor, riskScore, countryCode, asn, isp, flags);
    }

    private String extractValue(String json, String key) {
        int start = json.indexOf("\"" + key + "\":\"");
        if (start == -1) return "Unknown";
        start += key.length() + 4;
        int end = json.indexOf("\"", start);
        if (end == -1) return "Unknown";
        return json.substring(start, end);
    }

    private DetectionResult performBasicCheck(String ip) {
        List<String> flags = new ArrayList<>();
        int riskScore = 0;

        if (ip.startsWith("10.") || ip.startsWith("192.168.") || 
            ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
            ip.startsWith("172.18.") || ip.startsWith("172.19.") ||
            ip.startsWith("172.2") || ip.startsWith("172.30.") || ip.startsWith("172.31.")) {
            return new DetectionResult(false, false, false, false, 0, "LOCAL", "", "Local Network", flags);
        }

        if (ip.equals("127.0.0.1") || ip.startsWith("::1")) {
            return new DetectionResult(false, false, false, false, 0, "LOCAL", "", "Localhost", flags);
        }

        if (isKnownDatacenterIP(ip)) {
            flags.add("DATACENTER_IP_RANGE");
            riskScore += 50;
        }

        if (isBlockedRange(ip)) {
            flags.add("BLOCKED_RANGE");
            riskScore += 80;
        }

        boolean isDatacenter = riskScore >= 50;

        return new DetectionResult(false, false, isDatacenter, false, riskScore, "Unknown", "", "Unknown", flags);
    }

    private boolean isKnownDatacenterIP(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;

        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);

        if (first == 45 && (second >= 32 && second <= 63)) return true;
        if (first == 104 && (second >= 16 && second <= 31)) return true;
        if (first == 172 && (second >= 64 && second <= 127)) return true;
        if (first == 185 && (second >= 193 && second <= 195)) return true;
        if (first == 192 && second == 241) return true;
        if (first == 198 && (second >= 54 && second <= 55)) return true;

        return false;
    }

    private boolean isBlockedRange(String ip) {
        for (String range : BLOCKED_IP_RANGES) {
            if (ipInRange(ip, range)) {
                return true;
            }
        }
        return false;
    }

    private boolean ipInRange(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String rangeIp = parts[0];
            int prefix = Integer.parseInt(parts[1]);

            long ipLong = ipToLong(ip);
            long rangeLong = ipToLong(rangeIp);
            long mask = (-1L) << (32 - prefix);

            return (ipLong & mask) == (rangeLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Integer.parseInt(parts[i]);
        }
        return result;
    }

    public void addBlockedRange(String cidr) {
        BLOCKED_IP_RANGES.add(cidr);
    }

    public void clearCache() {
        cache.clear();
    }

    public void cleanupExpiredCache() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        rateLimiter.cleanup();
    }

    public int getCacheSize() {
        return cache.size();
    }

    public int getCurrentRequestRate() {
        return rateLimiter.getCurrentCount("vpn");
    }
}
