package com.github.thatapplepieguy.packetpatcher;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.thatapplepieguy.packetpatcher.command.BigChatCommand;
import com.github.thatapplepieguy.packetpatcher.fix.ChatLengthFix;
import com.github.thatapplepieguy.packetpatcher.fix.HealthRoundingFix;
import com.github.thatapplepieguy.packetpatcher.fix.KeepAliveFix;
import com.github.thatapplepieguy.packetpatcher.fix.ProjectileDesyncFix;
import com.github.thatapplepieguy.packetpatcher.fix.bucket.BucketLookStore;
import com.github.thatapplepieguy.packetpatcher.fix.bucket.GhostLiquidFix;
import com.github.thatapplepieguy.packetpatcher.fix.bucket.LookAwareBucket;
import com.github.thatapplepieguy.packetpatcher.route.PacketRouter;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.MinecraftKey;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.plugin.java.JavaPlugin;

public class PacketPatcher extends JavaPlugin {

    public static final boolean DEBUG = Boolean.parseBoolean(System.getenv("PACKETPATCHER_DEBUG"));

    private final PacketRouter packetRouter = new PacketRouter();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getConfig().getBoolean("fix-ghost-liquids", true)) {
            BucketLookStore lookStore = new BucketLookStore();
            registerBuckets(lookStore);
            packetRouter.register(new GhostLiquidFix(lookStore));
        }

        if (getConfig().getBoolean("split-oversized-chat-messages", true)) {
            packetRouter.register(new ChatLengthFix());
        }

        if (getConfig().getBoolean("fix-projectile-desync", true)) {
            packetRouter.register(new ProjectileDesyncFix());
        }

        if (getConfig().getBoolean("improve-health-rounding", true)) {
            packetRouter.register(new HealthRoundingFix());
        }

        if (getConfig().getBoolean("spoof-keep-alive-ids", true)) {
            packetRouter.register(new KeepAliveFix());
        }

        PacketEvents.getAPI().getEventManager().registerListener(packetRouter);

        if (DEBUG) {
            Bukkit.getCommandMap().register("packetpatcher", new BigChatCommand("bigchat"));
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(p ->
                packetRouter.uninject(((CraftPlayer) p).getHandle().playerConnection.networkManager.channel));

        PacketEvents.getAPI().getEventManager().unregisterListener(packetRouter);
    }

    private static void registerBuckets(BucketLookStore lookStore) {
        Item emptyBucket = new LookAwareBucket(Blocks.AIR, lookStore).c("bucket").c(16);
        Item.REGISTRY.a(325, new MinecraftKey("bucket"), emptyBucket);

        Item waterBucket = new LookAwareBucket(Blocks.FLOWING_WATER, lookStore).c("bucketWater").c(emptyBucket);
        Item.REGISTRY.a(326, new MinecraftKey("water_bucket"), waterBucket);

        Item lavaBucket = new LookAwareBucket(Blocks.FLOWING_LAVA, lookStore).c("bucketLava").c(emptyBucket);
        Item.REGISTRY.a(327, new MinecraftKey("lava_bucket"), lavaBucket);
    }
}
