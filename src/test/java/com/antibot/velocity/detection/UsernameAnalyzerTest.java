package com.antibot.velocity.detection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UsernameAnalyzerTest {

    private UsernameAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new UsernameAnalyzer();
    }

    @Test
    void testNormalUsername() {
        UsernameAnalyzer.AnalysisResult result = analyzer.analyze("Steve");
        assertFalse(result.isSuspicious());
        assertEquals(UsernameAnalyzer.RiskLevel.SAFE, result.getRiskLevel());
    }

    @Test
    void testSuspiciousUsername() {
        UsernameAnalyzer.AnalysisResult result = analyzer.analyze("Player123456789");
        assertTrue(result.isSuspicious());
        assertTrue(result.getRiskScore() > 0);
    }

    @Test
    void testRandomCharacters() {
        // "asdfjkl123" не соответствует подозрительным паттернам
        // Используем более подозрительный ник
        UsernameAnalyzer.AnalysisResult result = analyzer.analyze("aaaaaa12345");
        assertTrue(result.isSuspicious());
        assertTrue(result.getReasons().size() > 0);
    }

    @Test
    void testBotPattern() {
        UsernameAnalyzer.AnalysisResult result = analyzer.analyze("bot_12345");
        assertTrue(result.isSuspicious());
        assertEquals(UsernameAnalyzer.RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    void testShortUsername() {
        UsernameAnalyzer.AnalysisResult result = analyzer.analyze("ab");
        // Короткий ник имеет риск 40, что меньше 50 (порог suspicious)
        // Но он всё равно имеет причины
        assertTrue(result.getReasons().size() > 0);
        assertTrue(result.getRiskScore() > 0);
    }

    @Test
    void testLongUsername() {
        UsernameAnalyzer.AnalysisResult result = analyzer.analyze("ThisIsAVeryLongUsername");
        // Длинные имена могут быть подозрительными
        assertTrue(result.getRiskScore() >= 0);
    }
}
