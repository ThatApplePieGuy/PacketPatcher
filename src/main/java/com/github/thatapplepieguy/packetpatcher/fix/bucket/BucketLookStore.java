package com.github.thatapplepieguy.packetpatcher.fix.bucket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BucketLookStore {

    private final Map<UUID, Look> looks = new ConcurrentHashMap<>();

    public void set(UUID uuid, float yaw, float pitch) {
        looks.put(uuid, new Look(yaw, pitch));
    }

    public Look consume(UUID uuid) {
        return looks.remove(uuid);
    }

    public record Look(float yaw, float pitch) {}
}
