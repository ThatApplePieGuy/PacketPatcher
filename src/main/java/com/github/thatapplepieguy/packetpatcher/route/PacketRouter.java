package com.github.thatapplepieguy.packetpatcher.route;

import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PacketRouter extends PacketListenerAbstract {

    private final Map<Class<?>, List<Handler>> handlers = new HashMap<>();
    private final List<ChannelInjector> injectors = new ArrayList<>();

    public void register(Object target) {
        if (target instanceof ChannelInjector injector) {
            injectors.add(injector);
        }

        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(PacketRoute.class)) continue;

            try {
                Class<?> wrapperType = method.getParameterTypes()[0];
                Constructor<?> wrapperConstructor = wrapperType.getConstructor(method.getParameterTypes()[1]);
                method.setAccessible(true);
                handlers.computeIfAbsent(wrapperType, k -> new ArrayList<>()).add(new Handler(target, method, wrapperConstructor));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onUserConnect(@NotNull UserConnectEvent event) {
        Channel channel = (Channel) event.getUser().getChannel();
        injectors.forEach(injector -> injector.inject(channel));
    }

    public void uninject(Channel channel) {
        injectors.forEach(injector -> injector.uninject(channel));
    }

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        dispatch(event);
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        dispatch(event);
    }

    private void dispatch(ProtocolPacketEvent event) {
        // handle all flying packets together
        Class<?> wrapperClass = WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())
                ? WrapperPlayClientPlayerFlying.class
                : event.getPacketType().getWrapperClass();

        handlers.getOrDefault(wrapperClass, List.of())
                .forEach(handler -> handler.invoke(event));
    }

    private record Handler(Object target, Method method, Constructor<?> wrapperConstructor) {

        private void invoke(ProtocolPacketEvent event) {
            try {
                method.invoke(target, wrapperConstructor.newInstance(event), event);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
