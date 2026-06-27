package com.github.thatapplepieguy.packetpatcher.fix.bucket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.thatapplepieguy.packetpatcher.route.PacketRoute;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * liquids are placed by the client and server performing their own raytrace. in the client tick order sending your
 * block placement packet comes before sending your location, so the server ends up performing the raytrace using
 * outdated rotation. the position the server uses is already correct, however, because the client processes the right
 * click before it processes movement for that tick. we can't frankenstein the client packets ourselves because every
 * received flying packet will cause the server to tick the player, so we need to use nms
 */
public class GhostLiquidFix {

    private static final Set<ItemType> BUCKETS = Set.of(
            ItemTypes.BUCKET,
            ItemTypes.WATER_BUCKET,
            ItemTypes.LAVA_BUCKET
    );

    private final ThreadLocal<Map<User, PacketWrapper<?>>> delayedPackets = ThreadLocal.withInitial(WeakHashMap::new);

    private final BucketLookStore lookStore;

    public GhostLiquidFix(BucketLookStore lookStore) {
        this.lookStore = lookStore;
    }

    @PacketRoute
    public void onBlockPlace(WrapperPlayClientPlayerBlockPlacement blockPlace, PacketReceiveEvent event) {
        User user = event.getUser();
        if (user.getClientVersion().isNewerThan(ClientVersion.V_1_8)) return; // this is fixed on modern clients

        if (blockPlace.getItemStack().isEmpty()) return;

        ItemType itemType = blockPlace.getItemStack().get().getType();
        if (!BUCKETS.contains(itemType)) return;

        blockPlace.setBuffer(null);
        delayedPackets.get().put(user, blockPlace);

        event.setCancelled(true);
    }

    @PacketRoute
    public void onFlying(WrapperPlayClientPlayerFlying flying, PacketReceiveEvent event) {
        User user = event.getUser();
        if (user.getClientVersion().isNewerThan(ClientVersion.V_1_8)) return;

        PacketWrapper<?> place = delayedPackets.get().remove(user);
        if (place == null) return;

        if (flying.hasRotationChanged()) {
            Location location = flying.getLocation();
            lookStore.set(user.getUUID(), location.getYaw(), location.getPitch());
        }

        user.receivePacketSilently(place);
    }
}
