package com.github.thatapplepieguy.packetpatcher.fix;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth;
import com.github.thatapplepieguy.packetpatcher.route.PacketRoute;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * the client always rounds up when displaying hearts, which means for example 14.01 hp will display as 15 half-hearts.
 * to make the rounding more accurate we can round the health ourselves at the packet level
 */
public class HealthRoundingFix {

    private static final int HEALTH_INDEX = 6;
    private static final int ABSORPTION_INDEX = 17;

    private final ThreadLocal<Map<User, Float>> lastSentHealth = ThreadLocal.withInitial(WeakHashMap::new);
    private final ThreadLocal<Map<User, Float>> lastSentAbsorption = ThreadLocal.withInitial(WeakHashMap::new);

    @PacketRoute
    public void onMetadata(WrapperPlayServerEntityMetadata metadata, PacketSendEvent event) {
        if (metadata.getEntityId() != event.getUser().getEntityId()) return;

        for (EntityData<?> data : metadata.getEntityMetadata()) {
            if (data.getType() != EntityDataTypes.FLOAT) continue;

            Map<User, Float> lastSent = switch (data.getIndex()) {
                case HEALTH_INDEX -> lastSentHealth.get();
                case ABSORPTION_INDEX -> lastSentAbsorption.get();
                default -> null;
            };
            if (lastSent == null) continue;

            @SuppressWarnings("unchecked")
            EntityData<@NotNull Float> floatData = (EntityData<@NotNull Float>) data;
            floatData.setValue(fixHealth(floatData.getValue(), event.getUser(), lastSent));
            event.markForReEncode(true);
        }
    }

    @PacketRoute
    public void onHealth(WrapperPlayServerUpdateHealth health, PacketSendEvent event) {
        health.setHealth(fixHealth(health.getHealth(), event.getUser(), lastSentHealth.get()));
        event.markForReEncode(true);
    }

    private float fixHealth(float value, User user, Map<User, Float> lastSent) {
        float previous = lastSent.getOrDefault(user, Float.NaN);
        float rounded = roundHealth(previous, value);

        lastSent.put(user, value);
        return rounded;
    }

    private static float roundHealth(float previousHealth, float currentHealth) {
        if (currentHealth <= 0) return 0;

        float rounded = Math.max(1, Math.round(currentHealth));

        boolean sameHeartDamage = !Float.isNaN(previousHealth)
                && currentHealth < previousHealth
                && Math.round(previousHealth) == rounded;

        if (sameHeartDamage) {
            rounded = Math.nextAfter(rounded, 0);
        }

        return rounded;
    }
}
