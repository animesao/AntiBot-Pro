package com.antibot.velocity.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Управление асинхронными операциями с контролем ресурсов
 */
public class AsyncExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AsyncExecutor.class);
    
    private final ExecutorService executor;
    private final int maxConcurrentTasks;
    private final Semaphore semaphore;

    public AsyncExecutor(int threadPoolSize, int maxConcurrentTasks) {
        this.executor = Executors.newFixedThreadPool(
            threadPoolSize,
            r -> {
                Thread t = new Thread(r, "AntiBot-Async-Worker");
                t.setDaemon(true);
                return t;
            }
        );
        this.maxConcurrentTasks = maxConcurrentTasks;
        this.semaphore = new Semaphore(maxConcurrentTasks);
    }

    /**
     * Выполняет задачу асинхронно с контролем количества одновременных задач
     */
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ожидание доступного слота
                if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    throw new RejectedExecutionException("Too many concurrent tasks");
                }
                
                try {
                    return task.get();
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Выполняет задачу с таймаутом
     */
    public <T> CompletableFuture<T> submitWithTimeout(Supplier<T> task, long timeout, TimeUnit unit) {
        CompletableFuture<T> future = submit(task);
        
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                timeoutFuture.completeExceptionally(new TimeoutException("Task timeout"));
            }
        }, timeout, unit);
        
        future.whenComplete((result, error) -> {
            scheduler.shutdown();
            if (error != null) {
                timeoutFuture.completeExceptionally(error);
            } else {
                timeoutFuture.complete(result);
            }
        });
        
        return timeoutFuture;
    }

    /**
     * Получает количество активных задач
     */
    public int getActiveTaskCount() {
        return maxConcurrentTasks - semaphore.availablePermits();
    }

    /**
     * Корректное завершение работы
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
