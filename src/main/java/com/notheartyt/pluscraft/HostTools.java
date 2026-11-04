package com.notheartyt.pluscraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;

/**
 * Host-side moderation. Commands run directly on the host's own integrated server with full
 * permission, so they work even when the world was created with cheats off.
 */
public final class HostTools {
    public static final String[] ROLES = {"SPECTATOR", "VISITOR", "BUILDER", "OPERATOR"};

    private HostTools() {}

    public static boolean isHost() {
        return MinecraftClient.getInstance().getServer() != null;
    }

    public static boolean run(String command) {
        MinecraftServer s = MinecraftClient.getInstance().getServer();
        if (s == null) return false;
        s.execute(() -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
        return true;
    }

    public static boolean validName(String n) {
        return n != null && n.matches("[A-Za-z0-9_]{1,16}");
    }

    public static void setRole(String name, int role) {
        if (!validName(name)) return;
        switch (role) {
            case 0 -> {
                run("deop " + name);
                run("gamemode spectator " + name);
            }
            case 1 -> {
                run("deop " + name);
                run("gamemode adventure " + name);
            }
            case 2 -> {
                run("deop " + name);
                run("gamemode survival " + name);
            }
            default -> run("op " + name);
        }
    }

    public static void kick(String name) {
        if (validName(name)) run("kick " + name);
    }

    public static void ban(String name) {
        if (validName(name)) run("ban " + name);
    }

    public static void whitelistAdd(String name) {
        if (validName(name)) run("whitelist add " + name);
    }
}
