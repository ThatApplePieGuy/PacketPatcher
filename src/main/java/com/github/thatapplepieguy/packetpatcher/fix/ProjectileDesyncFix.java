package com.github.thatapplepieguy.packetpatcher.fix;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.github.thatapplepieguy.packetpatcher.route.PacketRoute;
import com.github.thatapplepieguy.packetpatcher.util.FastThreadLocals;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * the server regularly sends teleport and velocity packets to resync projectiles with the client, but these packets end
 * up causing desync by forcing the client projectile to skip a tick or by putting the teleport and velocity out of
 * sync. we can trust the client's simulation to remain in sync with the server as long as both are running at speed and
 * there isn't an unlucky case with lag or packet rounding that causes a collision for one side but not the other
 */
public class ProjectileDesyncFix {

    private static final Set<EntityType> PROJECTILES = Set.of(
            EntityTypes.ARROW,
            EntityTypes.SNOWBALL,
            EntityTypes.EGG,
            EntityTypes.ENDER_PEARL,
            EntityTypes.POTION,
            EntityTypes.THROWN_EXP_BOTTLE,
            EntityTypes.FISHING_BOBBER
    );

    private final FastThreadLocal<Map<User,Map<Integer, Boolean>>> projectiles = FastThreadLocals.withInitial(WeakHashMap::new);

    @PacketRoute
    public void onSpawn(WrapperPlayServerSpawnEntity spawn, PacketSendEvent event) {
        // modern clients can collide with their own projectiles, so we do need resyncing for them
        if (event.getUser().getClientVersion().isNewerThan(ClientVersion.V_1_8)) return;

        if (PROJECTILES.contains(spawn.getEntityType())) {
            projectiles.get().computeIfAbsent(event.getUser(), k -> new HashMap<>())
                    .put(spawn.getEntityId(), false);
        }
    }

    @PacketRoute
    public void onTeleport(WrapperPlayServerEntityTeleport teleport, PacketSendEvent event) {
        cancelIfTracked(teleport.getEntityId(), event);
    }

    @PacketRoute
    public void onRelMove(WrapperPlayServerEntityRelativeMove relMove, PacketSendEvent event) {
        cancelIfTracked(relMove.getEntityId(), event);
    }

    @PacketRoute
    public void onRelMoveLook(WrapperPlayServerEntityRelativeMoveAndRotation relMoveLook, PacketSendEvent event) {
        cancelIfTracked(relMoveLook.getEntityId(), event);
    }

    private void cancelIfTracked(int entityId, PacketSendEvent event) {
        if (event.getUser().getClientVersion().isNewerThan(ClientVersion.V_1_8)) return;

        Map<Integer, Boolean> velocitySent = projectiles.get().get(event.getUser());
        if (velocitySent != null && velocitySent.containsKey(entityId)) {
            event.setCancelled(true);
        }
    }

    @PacketRoute
    public void onVelocity(WrapperPlayServerEntityVelocity velocity, PacketSendEvent event) {
        if (event.getUser().getClientVersion().isNewerThan(ClientVersion.V_1_8)) return;

        Map<Integer, Boolean> velocitySent = projectiles.get().get(event.getUser());
        if (velocitySent == null) return;

        Boolean isVelocitySent = velocitySent.get(velocity.getEntityId());
        if (isVelocitySent == null) return;

        if (isVelocitySent) {
            event.setCancelled(true);
        } else {
            velocitySent.put(velocity.getEntityId(), true);
        }
    }

    @PacketRoute
    public void onDestroy(WrapperPlayServerDestroyEntities destroy, PacketSendEvent event) {
        if (event.getUser().getClientVersion().isNewerThan(ClientVersion.V_1_8)) return;

        Map<Integer, Boolean> velocitySent = projectiles.get().get(event.getUser());
        if (velocitySent != null) {
            for (int id : destroy.getEntityIds()) {
                velocitySent.remove(id);
            }
        }
    }
}
