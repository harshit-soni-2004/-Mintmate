package com.spendsense.auth.otp;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class OtpRateLimiter {

    private static final int MAX_REQUESTS_PER_HOUR = 20;
    private static final long ONE_HOUR_SECONDS = 3600;

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Instant>> ipRequestLog = new ConcurrentHashMap<>();

    public boolean isAllowed(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return true;
        }
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(ONE_HOUR_SECONDS);

        ConcurrentLinkedQueue<Instant> queue = ipRequestLog.computeIfAbsent(clientIp, k -> new ConcurrentLinkedQueue<>());

        // Evict expired timestamps
        while (!queue.isEmpty() && queue.peek().isBefore(cutoff)) {
            queue.poll();
        }

        if (queue.size() >= MAX_REQUESTS_PER_HOUR) {
            return false;
        }

        queue.add(now);
        return true;
    }

    public void clear() {
        ipRequestLog.clear();
    }
}
