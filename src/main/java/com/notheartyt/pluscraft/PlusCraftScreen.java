package com.notheartyt.pluscraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlusCraftScreen extends Screen {
    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {}

    private static final int PW = 380, PH = 230;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFF9A9AB0, GREEN = 0xFF55FF55, RED = 0xFFFF5555,
            CYAN = 0xFF00E5FF, MAGENTA = 0xFFFF2BD6, YELLOW = 0xFFFFEE55;
    private static final int BG = 0xFF0B0B14, PANEL = 0xFF12121F, CARD = 0xFF1B1B2E, CARD_SEL = 0xFF2A1F4D;
    private static final String[] TABS = {"HOST", "PLAYERS", "JOIN", "FRIENDS", "ABOUT"};
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Integer> roles = new HashMap<>();
    private int tab = 0;
    private String friendInput = "";
    private String codeInput = "";
    private String status = "Welcome to PlusCraft.";
    private int statusColor = GRAY;

    public PlusCraftScreen() {
        super(Text.literal("PlusCraft"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
    }

    private void addHit(int x1, int y1, int x2, int y2, Runnable r) {
        hits.add(new Hit(x1, y1, x2, y2, r));
    }

    private void drawCentered(DrawContext ctx, String s, int x, int y, int w, int color) {
        ctx.drawText(textRenderer, s, x + (w - textRenderer.getWidth(s)) / 2, y, color, true);
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 28);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 28);
        int b = Math.min(255, (c & 0xFF) + 28);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, int bg, int accent, int mx, int my, Runnable r) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        ctx.fill(x, y, x + w, y + h, accent);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? lighten(bg) : bg);
        drawCentered(ctx, label, x, y + (h - 8) / 2, w, WHITE);
        addHit(x, y, x + w, y + h, r);
    }

    // ------------------------------------------------------------ actions

    /** One click: open the world to LAN/online guests and ask the router to forward the port. */
    private void startHosting() {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlusCraftConfig c = PlusCraftClient.cfg;
        if (mc.player == null || !mc.isInSingleplayer() || !HostTools.isHost()) {
            setStatus("Open a single-player world first.", RED);
            return;
        }
        HostTools.run("publish " + c.allowCommands + " " + c.hostGamemode + " " + c.hostPort);
        Upnp.openAsync(c.hostPort);
        Stun.lookupAsync();
        setStatus("World opened. Setting up router port...", GREEN);
    }

    private String currentCode() {
        String ip = Upnp.externalIp != null ? Upnp.externalIp : Stun.publicIp;
        if (ip == null) return null;
        int port = Upnp.mappedPort > 0 ? Upnp.mappedPort : PlusCraftClient.cfg.hostPort;
        try {
            return JoinCode.encode(ip, port);
        } catch (Exception e) {
            return null;
        }
    }

    private void copyCode() {
        String code = currentCode();
        if (code == null) {
            setStatus("No public IP yet - press START HOSTING or LOOKUP IP.", RED);
            return;
        }
        client.keyboard.setClipboard(code);
        setStatus("Copied " + code + " - send it to your friend.", GREEN);
    }

    private void addFriend() {
        String n = friendInput.trim();
        if (n.isEmpty()) return;
        List<String> f = PlusCraftClient.cfg.friends;
        if (f.contains(n)) {
            setStatus(n + " is already in your list.", YELLOW);
        } else {
            f.add(n);
            PlusCraftClient.cfg.save();
            setStatus("Added " + n, GREEN);
        }
        friendInput = "";
    }

    private void joinByCode() {
        String[] d = JoinCode.decode(codeInput);
        if (d == null) {
            setStatus("That join code is not valid.", RED);
            return;
        }
        String addr = d[0] + ":" + d[1];
        try {
            ServerList list = new ServerList(client);
            list.loadFile();
            list.add(new ServerInfo("PlusCraft Friend", addr, ServerInfo.ServerType.OTHER), false);
            list.saveFile();
            client.keyboard.setClipboard(addr);
            setStatus("Added to Multiplayer list: " + addr, GREEN);
        } catch (Exception e) {
            client.keyboard.setClipboard(addr);
            setStatus("Copied " + addr + " - use Direct Connection.", YELLOW);
        }
    }

    // ------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hits.clear();
        PlusCraftConfig cfg = PlusCraftClient.cfg;
        long now = System.currentTimeMillis();

        ctx.fill(0, 0, width, height, 0xA0000000);
        int px = (width - PW) / 2;
        int py = (height - PH) / 2;
        ctx.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, CYAN);
        ctx.fill(px, py, px + PW, py + PH, BG);
        ctx.fill(px + 2, py + 2, px + PW - 2, py + PH - 2, PANEL);

        ctx.drawText(textRenderer, "PLUSCRAFT", px + 10, py + 8, CYAN, true);

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            int tx = px + 70 + i * 61;
            boolean sel = tab == i;
            boolean hover = mx >= tx && mx < tx + 58 && my >= py + 5 && my < py + 21;
            ctx.fill(tx, py + 5, tx + 58, py + 21, sel ? CARD_SEL : (hover ? 0xFF1F1F36 : CARD));
            if (sel) ctx.fill(tx, py + 19, tx + 58, py + 21, MAGENTA);
            drawCentered(ctx, TABS[i], tx, py + 9, 58, sel ? WHITE : GRAY);
            addHit(tx, py + 5, tx + 58, py + 21, () -> tab = idx);
        }

        int bx = px + 12, by = py + 32;
        switch (tab) {
            case 0 -> renderHost(ctx, cfg, bx, by, mx, my);
            case 1 -> renderPlayers(ctx, cfg, bx, by, mx, my);
            case 2 -> renderJoin(ctx, bx, by, mx, my, now);
            case 3 -> renderFriends(ctx, cfg, bx, by, mx, my, now);
            default -> renderAbout(ctx, bx, by);
        }

        ctx.drawText(textRenderer, status, px + 10, py + PH - 14, statusColor, true);
    }

    private void renderHost(DrawContext ctx, PlusCraftConfig cfg, int x, int y, int mx, int my) {
        ctx.drawText(textRenderer, "Your PC is the server. Friends join your single-player world.", x, y, WHITE, true);

        ctx.drawText(textRenderer, "Port: " + cfg.hostPort, x, y + 22, WHITE, true);
        button(ctx, x + 80, y + 18, 18, 16, "-", 0xFF2A2A3A, GRAY, mx, my, () -> {
            cfg.hostPort = Math.max(1025, cfg.hostPort - 1);
            cfg.save();
        });
        button(ctx, x + 102, y + 18, 18, 16, "+", 0xFF2A2A3A, GRAY, mx, my, () -> {
            cfg.hostPort = Math.min(65535, cfg.hostPort + 1);
            cfg.save();
        });

        ctx.drawText(textRenderer, "Mode: " + cfg.hostGamemode, x + 140, y + 22, WHITE, true);
        button(ctx, x + 235, y + 18, 70, 16, "CHANGE", 0xFF2A2A3A, GRAY, mx, my, () -> {
            cfg.hostGamemode = switch (cfg.hostGamemode) {
                case "survival" -> "creative";
                case "creative" -> "adventure";
                case "adventure" -> "spectator";
                default -> "survival";
            };
            cfg.save();
        });

        button(ctx, x, y + 40, 150, 16, "GUEST CHEATS: " + (cfg.allowCommands ? "ON" : "OFF"),
                cfg.allowCommands ? 0xFF5E1B1B : 0xFF2A2A3A, cfg.allowCommands ? RED : GRAY, mx, my, () -> {
                    cfg.allowCommands = !cfg.allowCommands;
                    cfg.save();
                });

        button(ctx, x, y + 62, 150, 20, "START HOSTING", 0xFF1B5E3A, GREEN, mx, my, this::startHosting);
        button(ctx, x, y + 86, 150, 16, Upnp.busy ? "WORKING..." : "CLOSE PORT", 0xFF5E1B1B, RED, mx, my, Upnp::closeAsync);
        button(ctx, x, y + 106, 150, 16, "COPY JOIN CODE", 0xFF4A1B4A, MAGENTA, mx, my, this::copyCode);
        button(ctx, x, y + 126, 150, 16, Stun.busy ? "LOOKING UP..." : "LOOKUP PUBLIC IP", 0xFF1B3A5E, CYAN, mx, my, Stun::lookupAsync);

        int rx = x + 165;
        ctx.drawText(textRenderer, "Router:", rx, y + 66, GRAY, false);
        String us = Upnp.status;
        int uc = us.startsWith("UPnP failed") ? RED : (us.startsWith("Port open, but") ? YELLOW : GREEN);
        ctx.drawText(textRenderer, us, rx, y + 77, uc, false);

        String ip = Upnp.externalIp != null ? Upnp.externalIp : Stun.publicIp;
        ctx.drawText(textRenderer, "Public IP: " + (ip != null ? ip : "unknown"), rx, y + 92, ip != null ? GREEN : GRAY, true);
        String code = currentCode();
        ctx.drawText(textRenderer, "Join code:", rx, y + 107, GRAY, false);
        ctx.drawText(textRenderer, code != null ? code : "-", rx, y + 118, code != null ? CYAN : GRAY, true);
        if (Stun.error != null && Stun.publicIp == null) {
            ctx.drawText(textRenderer, "STUN: " + Stun.error, rx, y + 131, RED, false);
        }

        ctx.drawText(textRenderer, "If the router has no UPnP, forward TCP port " + cfg.hostPort + " by hand.", x, y + 150, YELLOW, false);
    }

    private void renderPlayers(DrawContext ctx, PlusCraftConfig cfg, int x, int y, int mx, int my) {
        if (!HostTools.isHost()) {
            ctx.drawText(textRenderer, "Host controls only work in your own single-player world.", x, y, GRAY, false);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.drawText(textRenderer, "Host controls", x, y, WHITE, true);

        button(ctx, x, y + 14, 100, 16, "WHITELIST ON", 0xFF1B5E3A, GREEN, mx, my, () -> {
            HostTools.run("whitelist on");
            setStatus("Whitelist enabled - only listed players can join.", GREEN);
        });
        button(ctx, x + 106, y + 14, 100, 16, "WHITELIST OFF", 0xFF2A2A3A, GRAY, mx, my, () -> {
            HostTools.run("whitelist off");
            setStatus("Whitelist disabled.", YELLOW);
        });
        button(ctx, x + 212, y + 14, 130, 16, "WHITELIST FRIENDS", 0xFF1B3A5E, CYAN, mx, my, () -> {
            int n = 0;
            for (String f : cfg.friends) {
                if (HostTools.validName(f)) {
                    HostTools.whitelistAdd(f);
                    n++;
                }
            }
            setStatus("Whitelisted " + n + " friend(s).", GREEN);
        });

        List<String> names = new ArrayList<>();
        if (mc.world != null && mc.player != null) {
            String self = mc.player.getName().getString();
            for (PlayerEntity p : mc.world.getPlayers()) {
                String n = p.getName().getString();
                if (!n.equals(self)) names.add(n);
            }
        }
        ctx.drawText(textRenderer, "Players in your world (" + names.size() + ")", x, y + 38, WHITE, true);
        if (names.isEmpty()) {
            ctx.drawText(textRenderer, "Nobody has joined yet.", x, y + 52, GRAY, false);
        }
        for (int i = 0; i < names.size() && i < 6; i++) {
            final String name = names.get(i);
            int ry = y + 52 + i * 17;
            int role = roles.getOrDefault(name, 2);
            ctx.fill(x, ry, x + 342, ry + 15, CARD);
            ctx.drawText(textRenderer, name, x + 6, ry + 4, WHITE, true);
            button(ctx, x + 150, ry, 90, 15, HostTools.ROLES[role], 0xFF2A1F4D, MAGENTA, mx, my, () -> {
                int next = (roles.getOrDefault(name, 2) + 1) % HostTools.ROLES.length;
                roles.put(name, next);
                HostTools.setRole(name, next);
                setStatus(name + " is now " + HostTools.ROLES[next], GREEN);
            });
            button(ctx, x + 246, ry, 46, 15, "KICK", 0xFF5E4A1B, YELLOW, mx, my, () -> {
                HostTools.kick(name);
                setStatus("Kicked " + name, YELLOW);
            });
            button(ctx, x + 296, ry, 42, 15, "BAN", 0xFF5E1B1B, RED, mx, my, () -> {
                HostTools.ban(name);
                setStatus("Banned " + name, RED);
            });
        }
    }

    private void renderJoin(DrawContext ctx, int x, int y, int mx, int my, long now) {
        ctx.drawText(textRenderer, "Join a friend's world", x, y, WHITE, true);
        ctx.drawText(textRenderer, "Paste or type the join code your friend sent you.", x, y + 11, GRAY, false);

        int iw = 220;
        ctx.fill(x, y + 28, x + iw, y + 44, CYAN);
        ctx.fill(x + 1, y + 29, x + iw - 1, y + 43, CARD);
        boolean blink = (now / 500) % 2 == 0;
        if (codeInput.isEmpty()) ctx.drawText(textRenderer, "PLUS-XXXXX-XXXXX", x + 5, y + 33, 0xFF5A5A75, true);
        else ctx.drawText(textRenderer, codeInput + (blink ? "_" : ""), x + 5, y + 33, WHITE, true);
        button(ctx, x + iw + 6, y + 28, 60, 16, "PASTE", 0xFF2A2A3A, GRAY, mx, my, () -> {
            String clip = client.keyboard.getClipboard();
            codeInput = clip == null ? "" : clip.trim();
            if (codeInput.length() > 20) codeInput = codeInput.substring(0, 20);
        });

        String[] d = JoinCode.decode(codeInput);
        if (!codeInput.isEmpty()) {
            if (d != null) ctx.drawText(textRenderer, "Address: " + d[0] + ":" + d[1], x, y + 52, GREEN, true);
            else ctx.drawText(textRenderer, "Code not complete / not valid yet", x, y + 52, GRAY, false);
        }
        button(ctx, x, y + 70, 180, 20, "ADD TO MULTIPLAYER LIST", 0xFF1B5E3A, GREEN, mx, my, this::joinByCode);
        ctx.drawText(textRenderer, "Then open Multiplayer and join \"PlusCraft Friend\".", x, y + 98, GRAY, false);
    }

    private void renderFriends(DrawContext ctx, PlusCraftConfig cfg, int x, int y, int mx, int my, long now) {
        ctx.drawText(textRenderer, "Friends (" + cfg.friends.size() + ")", x, y, WHITE, true);

        int iw = 200;
        ctx.fill(x, y + 14, x + iw, y + 30, CYAN);
        ctx.fill(x + 1, y + 15, x + iw - 1, y + 29, CARD);
        boolean blink = (now / 500) % 2 == 0;
        if (friendInput.isEmpty()) ctx.drawText(textRenderer, "Type a name, press Enter...", x + 5, y + 19, 0xFF5A5A75, true);
        else ctx.drawText(textRenderer, friendInput + (blink ? "_" : ""), x + 5, y + 19, WHITE, true);
        button(ctx, x + iw + 6, y + 14, 60, 16, "ADD", 0xFF1B5E3A, GREEN, mx, my, this::addFriend);

        int ly = y + 38;
        List<String> f = cfg.friends;
        if (f.isEmpty()) {
            ctx.drawText(textRenderer, "No friends added yet.", x, ly, GRAY, false);
        }
        for (int i = 0; i < f.size() && i < 8; i++) {
            final String name = f.get(i);
            int ry = ly + i * 17;
            ctx.fill(x, ry, x + 266, ry + 15, CARD);
            ctx.drawText(textRenderer, name, x + 6, ry + 4, WHITE, true);
            button(ctx, x + 206, ry, 60, 15, "REMOVE", 0xFF5E1B1B, RED, mx, my, () -> {
                cfg.friends.remove(name);
                cfg.save();
                setStatus("Removed " + name, YELLOW);
            });
        }
    }

    private void renderAbout(DrawContext ctx, int x, int y) {
        ctx.drawText(textRenderer, "PlusCraft - open-source multiplayer & social mod", x, y, CYAN, true);
        ctx.drawText(textRenderer, "By LOVE-YT (@Loveyt224) & NotHeartYT", x, y + 12, WHITE, false);
        ctx.drawText(textRenderer, "Planned next:", x, y + 34, MAGENTA, true);
        String[] todo = {
                "- Cosmetics: capes, models, emotes (visible to PlusCraft users)",
                "- Account manager",
                "- Media editor and Discord Rich Presence",
                "- Chat / DMs / presence (needs a messaging backend)"
        };
        for (int i = 0; i < todo.length; i++) ctx.drawText(textRenderer, todo[i], x, y + 48 + i * 11, GRAY, false);
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (click.x() >= h.x1() && click.x() < h.x2() && click.y() >= h.y1() && click.y() < h.y2()) {
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int code = keyInput.getKeycode();

        if (tab == 3) { // friend name
            if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {
                addFriend();
                return true;
            }
            if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (!friendInput.isEmpty()) friendInput = friendInput.substring(0, friendInput.length() - 1);
                return true;
            }
            if (friendInput.length() < 16) {
                if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
                    friendInput += (char) ('a' + (code - GLFW.GLFW_KEY_A));
                    return true;
                }
                if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
                    friendInput += (char) ('0' + (code - GLFW.GLFW_KEY_0));
                    return true;
                }
                if (code == GLFW.GLFW_KEY_MINUS) {
                    friendInput += '_';
                    return true;
                }
            }
        } else if (tab == 2) { // join code
            if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {
                joinByCode();
                return true;
            }
            if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (!codeInput.isEmpty()) codeInput = codeInput.substring(0, codeInput.length() - 1);
                return true;
            }
            if (codeInput.length() < 20) {
                if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
                    codeInput += (char) ('A' + (code - GLFW.GLFW_KEY_A));
                    return true;
                }
                if (code >= GLFW.GLFW_KEY_2 && code <= GLFW.GLFW_KEY_7) {
                    codeInput += (char) ('2' + (code - GLFW.GLFW_KEY_2));
                    return true;
                }
            }
        }
        return super.keyPressed(keyInput);
    }
}
