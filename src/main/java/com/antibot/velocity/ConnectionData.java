package com.antibot.velocity;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectionData {
    
    private final Queue<Long> connectionTimes = new ConcurrentLinkedQueue<>();

    public void addConnection() {
        connectionTimes.add(System.currentTimeMillis());
    }

    public int getConnectionsInWindow(int windowSeconds) {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        
        connectionTimes.removeIf(time -> time < cutoff);
        
        return connectionTimes.size();
    }

    public void clear() {
        connectionTimes.clear();
    }
}
