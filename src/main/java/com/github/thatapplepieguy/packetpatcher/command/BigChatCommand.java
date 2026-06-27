package com.github.thatapplepieguy.packetpatcher.command;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.concurrent.ThreadLocalRandom;

public class BigChatCommand extends Command {

    public BigChatCommand(String name) {
        super(name);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!sender.isOp()) return true;

        ChatColor[] colors = {
                ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW,
                ChatColor.GREEN, ChatColor.AQUA, ChatColor.LIGHT_PURPLE
        };

        ComponentBuilder builder = new ComponentBuilder("");

        for (int i = 1; i <= 3000; i++) {
            String number = String.valueOf(i);
            ChatColor color = colors[i % colors.length];

            builder.append(number)
                    .color(color);

            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                builder.bold(true);
            }

            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                builder.italic(true);
            }

            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                builder.strikethrough(true);
            }

            builder.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(color + number)));

            builder.append(" ")
                    .reset();
        }

        sender.sendMessage(builder.create());

        return true;
    }
}
