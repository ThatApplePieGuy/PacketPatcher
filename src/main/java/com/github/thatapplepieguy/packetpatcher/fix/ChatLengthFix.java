package com.github.thatapplepieguy.packetpatcher.fix;

import com.github.thatapplepieguy.packetpatcher.route.ChannelInjector;
import com.github.thatapplepieguy.packetpatcher.util.ComponentSplitter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.server.v1_8_R3.ChatMessage;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import org.bukkit.Bukkit;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * with enough components it's possible for plugins to send chat message longer than the vanilla limit, which break on
 * encoding and kick receiving clients. the fix is to listen for oversized packets and split them up ourselves, but
 * because the issue happens in the encoding phase we can't resolve it using packetevents and need to inject our own
 * listener into the pipeline
 */
public class ChatLengthFix implements ChannelInjector {

    private static final String HANDLER_NAME = "packetpatcher-chat-encoder";

    @Override
    public void inject(Channel channel) {
        channel.pipeline().addAfter("encoder", HANDLER_NAME, new ChatLengthEncoder());
    }

    @Override
    public void uninject(Channel channel) {
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            channel.pipeline().remove(HANDLER_NAME);
        }
    }

    private static class ChatLengthEncoder extends ChannelOutboundHandlerAdapter {

        private static final int CLIENT_JSON_LIMIT = Short.MAX_VALUE;

        private static final VarHandle PACKET_CHAT_COMPONENT;
        private static final VarHandle PACKET_CHAT_POSITION;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayOutChat.class, MethodHandles.lookup());
                PACKET_CHAT_COMPONENT = lookup.findVarHandle(PacketPlayOutChat.class, "a", IChatBaseComponent.class);
                PACKET_CHAT_POSITION = lookup.findVarHandle(PacketPlayOutChat.class, "b", byte.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!(msg instanceof PacketPlayOutChat chat)) {
                super.write(ctx, msg, promise);
                return;
            }

            String encodedJson;
            if (chat.components != null) {
                encodedJson = ComponentSerializer.toString(chat.components);
            } else {
                IChatBaseComponent component = (IChatBaseComponent) PACKET_CHAT_COMPONENT.get(chat);
                encodedJson = component != null ? IChatBaseComponent.ChatSerializer.a(component) : null;
            }
            if (encodedJson == null || encodedJson.getBytes(StandardCharsets.UTF_8).length <= CLIENT_JSON_LIMIT) {
                super.write(ctx, msg, promise);
                return;
            }

            List<PacketPlayOutChat> packets;
            try {
                packets = split(chat);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to split an oversized chat packet!", e);
                var warning = new PacketPlayOutChat(new ChatMessage("§8§oAn oversized chat message could not be sent"));
                packets = List.of(warning);
            }

            int last = packets.size() - 1;
            for (int i = 0; i <= last; i++) {
                ctx.write(packets.get(i), i == last ? promise : ctx.voidPromise());
            }
        }

        private static List<PacketPlayOutChat> split(PacketPlayOutChat chat) {
            IChatBaseComponent component = chat.components != null
                    ? IChatBaseComponent.ChatSerializer.a(ComponentSerializer.toString(chat.components))
                    : (IChatBaseComponent) PACKET_CHAT_COMPONENT.get(chat);
            List<IChatBaseComponent> parts = ComponentSplitter.split(component, CLIENT_JSON_LIMIT);

            byte position = (byte) PACKET_CHAT_POSITION.get(chat);
            List<PacketPlayOutChat> packets = new ArrayList<>(parts.size());
            for (IChatBaseComponent part : parts) {
                int partLength = IChatBaseComponent.ChatSerializer.a(part).getBytes(StandardCharsets.UTF_8).length;
                if (partLength > CLIENT_JSON_LIMIT) {
                    throw new IllegalStateException("Chat part exceeded client limit after splitting!");
                }
                packets.add(new PacketPlayOutChat(part, position));
            }
            return packets;
        }
    }
}
