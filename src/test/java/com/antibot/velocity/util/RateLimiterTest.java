package com.antibot.velocity.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(10); // 10 запросов в минуту
    }

    @Test
    void testAcquireWithinLimit() {
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryAcquire("test"));
        }
    }

    @Test
    void testAcquireExceedsLimit() {
        // Заполняем лимит
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryAcquire("test");
        }
        
        // Следующий запрос должен быть отклонён
        assertFalse(rateLimiter.tryAcquire("test"));
    }

    @Test
    void testMultipleKeys() {
        assertTrue(rateLimiter.tryAcquire("key1"));
        assertTrue(rateLimiter.tryAcquire("key2"));
        
        assertEquals(1, rateLimiter.getCurrentCount("key1"));
        assertEquals(1, rateLimiter.getCurrentCount("key2"));
    }

    @Test
    void testCleanup() {
        rateLimiter.tryAcquire("test");
        rateLimiter.cleanup();
        
        // После cleanup лимитер должен продолжать работать
        assertTrue(rateLimiter.tryAcquire("test"));
    }
}
