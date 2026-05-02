package com.urlshortener.url_shortener.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class SnowflakeIdGenerator {

    // Custom Epoch (e.g., 2026-01-01)
    private final long epoch = 1735689600000L;

    private final long machineIdBits = 10L;
    private final long sequenceBits = 12L;

    private final long maxMachineId = -1L ^ (-1L << machineIdBits);
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    private final long machineIdShift = sequenceBits;
    private final long timestampLeftShift = sequenceBits + machineIdBits;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    @Value("${server.machine-id:1}")
    private long machineId;

    @PostConstruct
    public void init() {
        if (machineId > maxMachineId || machineId < 0) {
            throw new IllegalArgumentException(String.format("Machine ID must be between 0 and %d", maxMachineId));
        }
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - epoch) << timestampLeftShift) |
                (machineId << machineIdShift) |
                sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}