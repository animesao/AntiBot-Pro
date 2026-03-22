package com.antibot.velocity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class GeoIPChecker {

    private final Map<String, CachedGeoData> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = TimeUnit.HOURS.toMillis(24);

    private Set<String> allowedCountries = new HashSet<>();
    private Set<String> blockedCountries = new HashSet<>();
    private boolean whitelistMode = false;

    public static class GeoData {
        private final String countryCode;
        private final String countryName;
        private final String city;
        private final String isp;
        private final String asn;
        private final double latitude;
        private final double longitude;
        private final String timezone;

        public GeoData(String countryCode, String countryName, String city, 
                       String isp, String asn, double latitude, double longitude, String timezone) {
            this.countryCode = countryCode;
            this.countryName = countryName;
            this.city = city;
            this.isp = isp;
            this.asn = asn;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timezone = timezone;
        }

        public String getCountryCode() { return countryCode; }
        public String getCountryName() { return countryName; }
        public String getCity() { return city; }
        public String getIsp() { return isp; }
        public String getAsn() { return asn; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getTimezone() { return timezone; }
    }

    private static class CachedGeoData {
        final GeoData data;
        final long timestamp;

        CachedGeoData(GeoData data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }

    public GeoData lookup(String ip) {
        CachedGeoData cached = cache.get(ip);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }

        GeoData data = fetchFromAPI(ip);
        if (data != null) {
            cache.put(ip, new CachedGeoData(data));
        }
        return data;
    }

    private GeoData fetchFromAPI(String ip) {
        try {
            URL url = new URL("https://ip-api.com/json/" + ip + "?fields=status,country,countryCode,city,isp,as,lat,lon,timezone");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                return parseResponse(response.toString());
            }
        } catch (Exception e) {
            // API failed
        }

        return new GeoData("XX", "Unknown", "Unknown", "Unknown", "Unknown", 0, 0, "Unknown");
    }

    private GeoData parseResponse(String json) {
        String countryCode = extractValue(json, "countryCode");
        String countryName = extractValue(json, "country");
        String city = extractValue(json, "city");
        String isp = extractValue(json, "isp");
        String asn = extractValue(json, "as");
        String timezone = extractValue(json, "timezone");
        
        double lat = 0, lon = 0;
        try {
            lat = Double.parseDouble(extractNumericValue(json, "lat"));
            lon = Double.parseDouble(extractNumericValue(json, "lon"));
        } catch (Exception ignored) {}

        return new GeoData(countryCode, countryName, city, isp, asn, lat, lon, timezone);
    }

    private String extractValue(String json, String key) {
        int start = json.indexOf("\"" + key + "\":\"");
        if (start == -1) return "Unknown";
        start += key.length() + 4;
        int end = json.indexOf("\"", start);
        if (end == -1) return "Unknown";
        return json.substring(start, end);
    }

    private String extractNumericValue(String json, String key) {
        int start = json.indexOf("\"" + key + "\":");
        if (start == -1) return "0";
        start += key.length() + 3;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        return json.substring(start, end);
    }

    public boolean isCountryAllowed(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) return false;
        
        String code = countryCode.toUpperCase();

        if (blockedCountries.contains(code)) {
            return false;
        }

        if (whitelistMode) {
            return allowedCountries.contains(code);
        }

        return true;
    }

    public void setAllowedCountries(Collection<String> countries) {
        allowedCountries.clear();
        for (String country : countries) {
            allowedCountries.add(country.toUpperCase());
        }
    }

    public void setBlockedCountries(Collection<String> countries) {
        blockedCountries.clear();
        for (String country : countries) {
            blockedCountries.add(country.toUpperCase());
        }
    }

    public void setWhitelistMode(boolean whitelistMode) {
        this.whitelistMode = whitelistMode;
    }

    public boolean isWhitelistMode() {
        return whitelistMode;
    }

    public void addAllowedCountry(String countryCode) {
        allowedCountries.add(countryCode.toUpperCase());
    }

    public void addBlockedCountry(String countryCode) {
        blockedCountries.add(countryCode.toUpperCase());
    }

    public void removeAllowedCountry(String countryCode) {
        allowedCountries.remove(countryCode.toUpperCase());
    }

    public void removeBlockedCountry(String countryCode) {
        blockedCountries.remove(countryCode.toUpperCase());
    }

    public Set<String> getAllowedCountries() {
        return Collections.unmodifiableSet(allowedCountries);
    }

    public Set<String> getBlockedCountries() {
        return Collections.unmodifiableSet(blockedCountries);
    }

    public void clearCache() {
        cache.clear();
    }

    public void cleanupExpiredCache() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
