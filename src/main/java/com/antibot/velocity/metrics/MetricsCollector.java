package com.antibot.velocity.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Сборщик метрик производительности
 */
public class MetricsCollector {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Histogram> histograms = new ConcurrentHashMap<>();

    /**
     * Увеличивает счетчик
     */
    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    /**
     * Увеличивает счетчик на значение
     */
    public void incrementCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(value);
    }

    /**
     * Устанавливает значение gauge
     */
    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    /**
     * Записывает значение в гистограмму
     */
    public void recordHistogram(String name, long value) {
        histograms.computeIfAbsent(name, k -> new Histogram()).record(value);
    }

    /**
     * Получает значение счетчика
     */
    public long getCounter(String name) {
        LongAdder counter = counters.get(name);
        return counter != null ? counter.sum() : 0;
    }

    /**
     * Получает значение gauge
     */
    public long getGauge(String name) {
        AtomicLong gauge = gauges.get(name);
        return gauge != null ? gauge.get() : 0;
    }

    /**
     * Получает статистику гистограммы
     */
    public HistogramSnapshot getHistogramSnapshot(String name) {
        Histogram histogram = histograms.get(name);
        return histogram != null ? histogram.getSnapshot() : new HistogramSnapshot(0, 0, 0, 0, 0);
    }

    /**
     * Сбрасывает все метрики
     */
    public void reset() {
        counters.clear();
        gauges.clear();
        histograms.clear();
    }

    /**
     * Простая гистограмма для отслеживания распределения значений
     */
    private static class Histogram {
        private final LongAdder count = new LongAdder();
        private final LongAdder sum = new LongAdder();
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);

        void record(long value) {
            count.increment();
            sum.add(value);
            
            // Обновление min
            long currentMin = min.get();
            while (value < currentMin && !min.compareAndSet(currentMin, value)) {
                currentMin = min.get();
            }
            
            // Обновление max
            long currentMax = max.get();
            while (value > currentMax && !max.compareAndSet(currentMax, value)) {
                currentMax = max.get();
            }
        }

        HistogramSnapshot getSnapshot() {
            long countValue = count.sum();
            long sumValue = sum.sum();
            long minValue = min.get();
            long maxValue = max.get();
            double avg = countValue > 0 ? (double) sumValue / countValue : 0;
            
            return new HistogramSnapshot(countValue, sumValue, minValue, maxValue, avg);
        }
    }

    /**
     * Снимок статистики гистограммы
     */
    public static class HistogramSnapshot {
        private final long count;
        private final long sum;
        private final long min;
        private final long max;
        private final double average;

        public HistogramSnapshot(long count, long sum, long min, long max, double average) {
            this.count = count;
            this.sum = sum;
            this.min = min;
            this.max = max;
            this.average = average;
        }

        public long getCount() { return count; }
        public long getSum() { return sum; }
        public long getMin() { return min; }
        public long getMax() { return max; }
        public double getAverage() { return average; }

        @Override
        public String toString() {
            return String.format("count=%d, sum=%d, min=%d, max=%d, avg=%.2f", 
                count, sum, min, max, average);
        }
    }
}
