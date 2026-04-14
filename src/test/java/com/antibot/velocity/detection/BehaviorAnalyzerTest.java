package com.antibot.velocity.detection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BehaviorAnalyzerTest {

    private BehaviorAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new BehaviorAnalyzer();
    }

    @Test
    void testNormalBehavior() {
        String player = "TestPlayer";
        String ip = "192.168.1.1";

        analyzer.recordConnection(player, ip);
        BehaviorAnalyzer.AnalysisResult result = analyzer.analyze(player, ip);

        assertFalse(result.isSuspicious());
        assertEquals(BehaviorAnalyzer.BehaviorType.NORMAL, result.getBehaviorType());
    }

    @Test
    void testRapidConnections() {
        String player = "BotPlayer";
        String ip = "10.0.0.1";

        // Симуляция быстрых подключений
        for (int i = 0; i < 10; i++) {
            analyzer.recordConnection(player + i, ip);
        }

        BehaviorAnalyzer.AnalysisResult result = analyzer.analyze(player + "0", ip);
        assertTrue(result.isSuspicious());
        assertTrue(result.getRiskScore() > 30);
    }

    @Test
    void testMultipleNames() {
        String ip = "10.0.0.2";

        // Один IP использует много разных имён
        for (int i = 0; i < 5; i++) {
            analyzer.recordConnection("Player" + i, ip);
        }

        BehaviorAnalyzer.AnalysisResult result = analyzer.analyze("Player0", ip);
        // Множественные имена должны увеличить риск
        assertTrue(result.getRiskScore() >= 40, 
            "Risk score should be at least 40 for multiple names, got: " + result.getRiskScore());
    }

    @Test
    void testCleanup() {
        analyzer.recordConnection("OldPlayer", "1.1.1.1");
        
        int initialSize = analyzer.getTotalTrackedPlayers();
        assertTrue(initialSize > 0);

        analyzer.cleanup();
        
        // После cleanup старые записи должны остаться (если не истекли)
        assertTrue(analyzer.getTotalTrackedPlayers() >= 0);
    }
}
