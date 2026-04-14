package com.antibot.velocity.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter для контроля частоты API запросов
 */
public class RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequestsPerMinute) {
        this.maxRequests = maxRequestsPerMinute;
        this.windowMillis = TimeUnit.MINUTES.toMillis(1);
    }

    /**
     * Проверяет, можно ли выполнить запрос
     * @param key Ключ (например, "geoip" или "vpn")
     * @return true если запрос разрешен
     */
    public boolean tryAcquire(String key) {
        RequestWindow window = windows.computeIfAbsent(key, k -> new RequestWindow());
        return window.tryAcquire();
    }

    /**
     * Ожидает доступности слота для запроса
     * @param key Ключ
     * @param timeoutMillis Максимальное время ожидания
     * @return true если слот получен
     */
    public boolean tryAcquire(String key, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        
        while (System.currentTimeMillis() < deadline) {
            if (tryAcquire(key)) {
                return true;
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }

    /**
     * Получает текущее количество запросов в окне
     */
    public int getCurrentCount(String key) {
        RequestWindow window = windows.get(key);
        return window != null ? window.getCurrentCount() : 0;
    }

    /**
     * Очищает старые окна
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> 
            now - entry.getValue().windowStart > windowMillis * 2
        );
    }

    private class RequestWindow {
        private volatile long windowStart;
        private final AtomicInteger count;

        RequestWindow() {
            this.windowStart = System.currentTimeMillis();
            this.count = new AtomicInteger(0);
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            
            // Сброс окна если прошло время
            if (now - windowStart >= windowMillis) {
                synchronized (this) {
                    if (now - windowStart >= windowMillis) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            
            // Проверка лимита
            int current = count.get();
            if (current >= maxRequests) {
                return false;
            }
            
            // Атомарное увеличение
            return count.compareAndSet(current, current + 1);
        }

        int getCurrentCount() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMillis) {
                return 0;
            }
            return count.get();
        }
    }
}
