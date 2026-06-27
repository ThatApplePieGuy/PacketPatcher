package com.github.thatapplepieguy.packetpatcher.util;

import net.minecraft.server.v1_8_R3.ChatComponentText;
import net.minecraft.server.v1_8_R3.ChatModifier;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ComponentSplitter {

    public static List<IChatBaseComponent> split(IChatBaseComponent root, int maxJsonLength) {
        if (jsonByteLength(root) <= maxJsonLength) {
            return List.of(root);
        }

        int base = baseOverhead();
        int separator = separatorOverhead(base);

        // flatten into leaves with standalone style
        List<IChatBaseComponent> leaves = new ArrayList<>();
        flatten(root, null, leaves, maxJsonLength, base);

        // pack leaves into parts that fit within the json limit
        List<IChatBaseComponent> parts = new ArrayList<>();
        List<IChatBaseComponent> current = new ArrayList<>();
        int currentLength = base;

        for (IChatBaseComponent leaf : leaves) {
            int leafLength = jsonByteLength(leaf);
            int cost = current.isEmpty() ? leafLength : separator + leafLength;

            if (!current.isEmpty() && currentLength + cost > maxJsonLength) {
                parts.add(wrap(current));
                current = new ArrayList<>();
                currentLength = base;
                cost = leafLength;
            }

            current.add(leaf);
            currentLength += cost;
        }

        parts.add(wrap(current));
        return parts;
    }

    private static void flatten(IChatBaseComponent component, ChatModifier inherited, List<IChatBaseComponent> out, int maxJsonLength, int base) {
        ChatModifier own = component.getChatModifier();
        ChatModifier effective = own != null ? own.clone() : new ChatModifier();
        effective.setChatModifier(inherited);

        if (component instanceof ChatComponentText text) {
            emitText(text.getText(), effective, out, maxJsonLength, base);
        } else { // we can't split up non-text
            IChatBaseComponent leaf = clone(component);
            leaf.a().clear();
            leaf.setChatModifier(effective);
            out.add(leaf);
        }

        for (IChatBaseComponent child : component.a()) {
            flatten(child, effective, out, maxJsonLength, base);
        }
    }

    private static void emitText(String content, ChatModifier style, List<IChatBaseComponent> out, int maxJsonLength, int base) {
        IChatBaseComponent whole = text(content, style);
        if (jsonByteLength(whole) <= maxJsonLength) {
            out.add(whole);
            return;
        }

        int styleOverhead = jsonByteLength(text("", style));
        int budget = Math.max(1, (maxJsonLength - styleOverhead - base) / 6); // a char could be up to 6 json bytes
        for (int i = 0; i < content.length(); i += budget) {
            int end = Math.min(content.length(), i + budget);
            out.add(text(content.substring(i, end), style));
        }
    }

    private static int baseOverhead() {
        IChatBaseComponent probe = new ChatComponentText("");
        IChatBaseComponent oneLeaf = new ChatComponentText("");
        oneLeaf.addSibling(new ChatComponentText(""));
        return jsonByteLength(oneLeaf) - jsonByteLength(probe);
    }

    private static int separatorOverhead(int base) {
        IChatBaseComponent probe = new ChatComponentText("");
        IChatBaseComponent twoLeaves = new ChatComponentText("");
        twoLeaves.addSibling(new ChatComponentText(""));
        twoLeaves.addSibling(new ChatComponentText(""));
        return jsonByteLength(twoLeaves) - 2 * jsonByteLength(probe) - base;
    }

    private static IChatBaseComponent text(String content, ChatModifier style) {
        IChatBaseComponent component = new ChatComponentText(content);
        component.setChatModifier(style);
        return component;
    }

    private static IChatBaseComponent wrap(List<IChatBaseComponent> leaves) {
        if (leaves.size() == 1) {
            return leaves.getFirst();
        }
        IChatBaseComponent root = new ChatComponentText("");
        for (IChatBaseComponent leaf : leaves) {
            root.addSibling(leaf);
        }
        return root;
    }

    private static IChatBaseComponent clone(IChatBaseComponent component) {
        return IChatBaseComponent.ChatSerializer.a(IChatBaseComponent.ChatSerializer.a(component));
    }

    private static int jsonByteLength(IChatBaseComponent component) {
        return IChatBaseComponent.ChatSerializer.a(component).getBytes(StandardCharsets.UTF_8).length;
    }
}
