package com.github.thatapplepieguy.packetpatcher.fix;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import com.github.thatapplepieguy.packetpatcher.route.PacketRoute;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * a standard server generates keepalive ids using the system clock, which can be predicted and sent in advance to spoof
 * your ping. we resolve this by rerolling our own random ids and replacing them before they get back to the server. has
 * the added benefit of not revealing your system time if you want to hide that for some reason
 */
public class KeepAliveFix {

    private record IdPair(long real, int spoofed) {}

    private final ThreadLocal<Map<User, Queue<IdPair>>> keepAlives = ThreadLocal.withInitial(WeakHashMap::new);

    @PacketRoute
    public void onKeepAlivePing(WrapperPlayServerKeepAlive keepAlive, PacketSendEvent event) {
        long real = keepAlive.getId();
        int spoofed = ThreadLocalRandom.current().nextInt();
        IdPair pair = new IdPair(real, spoofed);

        Queue<IdPair> pairs = keepAlives.get().computeIfAbsent(event.getUser(), k -> new ArrayDeque<>());
        if (pairs.size() > 100) {
            event.getUser().closeConnection(); // client must be misbehaving
            return;
        }

        pairs.add(pair);
        keepAlive.setId(spoofed);
        event.markForReEncode(true);
    }

    @PacketRoute
    public void onKeepAlivePong(WrapperPlayClientKeepAlive keepAlive, PacketReceiveEvent event) {
        Queue<IdPair> pairs = keepAlives.get().get(event.getUser());
        if (pairs == null) return;

        IdPair pair = pairs.poll();
        if (pair == null) return;

        if (keepAlive.getId() == pair.spoofed()) {
            event.setCancelled(true);
            event.getUser().receivePacketSilently(new WrapperPlayClientKeepAlive(pair.real()));
        }
    }
}
