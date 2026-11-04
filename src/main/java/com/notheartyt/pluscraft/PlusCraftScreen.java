package com.notheartyt.pluscraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PlusCraftScreen extends Screen {
    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {}

    private static final int PW = 360, PH = 230;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFF9A9AB0, GREEN = 0xFF55FF55, RED = 0xFFFF5555,
            CYAN = 0xFF00E5FF, MAGENTA = 0xFFFF2BD6, YELLOW = 0xFFFFEE55;
    private static final int BG = 0xFF0B0B14, PANEL = 0xFF12121F, CARD = 0xFF1B1B2E, CARD_SEL = 0xFF2A1F4D;
    private static final String[] TABS = {"HOST", "FRIENDS", "ABOUT"};

    private final List<Hit> hits = new ArrayList<>();
    private int tab = 0;
    private String input = "";
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

    private void openWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlusCraftConfig c = PlusCraftClient.cfg;
        if (mc.player == null || !mc.isInSingleplayer()) {
            setStatus("Open a single-player world first.", RED);
            return;
        }
        String cmd = "publish " + c.allowCommands + " " + c.hostGamemode + " " + c.hostPort;
        mc.setScreen(null);
        mc.player.networkHandler.sendChatCommand(cmd);
    }

    private void copyAddress() {
        String ip = Stun.publicIp;
        if (ip == null) {
            setStatus("Look up your public IP first.", RED);
            return;
        }
        client.keyboard.setClipboard(ip + ":" + PlusCraftClient.cfg.hostPort);
        setStatus("Copied " + ip + ":" + PlusCraftClient.cfg.hostPort + " - send it to your friend.", GREEN);
    }

    private void addFriend() {
        String n = input.trim();
        if (n.isEmpty()) return;
        List<String> f = PlusCraftClient.cfg.friends;
        if (f.contains(n)) {
            setStatus(n + " is already in your list.", YELLOW);
        } else {
            f.add(n);
            PlusCraftClient.cfg.save();
            setStatus("Added " + n, GREEN);
        }
        input = "";
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

        // tabs
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            int tx = px + 90 + i * 84;
            boolean sel = tab == i;
            boolean hover = mx >= tx && mx < tx + 80 && my >= py + 5 && my < py + 21;
            ctx.fill(tx, py + 5, tx + 80, py + 21, sel ? CARD_SEL : (hover ? 0xFF1F1F36 : CARD));
            if (sel) ctx.fill(tx, py + 19, tx + 80, py + 21, MAGENTA);
            drawCentered(ctx, TABS[i], tx, py + 9, 80, sel ? WHITE : GRAY);
            addHit(tx, py + 5, tx + 80, py + 21, () -> tab = idx);
        }

        int bx = px + 12, by = py + 32;
        if (tab == 0) renderHost(ctx, cfg, bx, by, mx, my);
        else if (tab == 1) renderFriends(ctx, cfg, bx, by, mx, my, now);
        else renderAbout(ctx, bx, by);

        ctx.drawText(textRenderer, status, px + 10, py + PH - 14, statusColor, true);
    }

    private void renderHost(DrawContext ctx, PlusCraftConfig cfg, int x, int y, int mx, int my) {
        ctx.drawText(textRenderer, "Share your single-player world with friends", x, y, WHITE, true);
        ctx.drawText(textRenderer, "Your friend joins with Direct Connection using your address.", x, y + 11, GRAY, false);

        // port
        ctx.drawText(textRenderer, "Port: " + cfg.hostPort, x, y + 32, WHITE, true);
        button(ctx, x + 80, y + 28, 18, 16, "-", 0xFF2A2A3A, GRAY, mx, my, () -> {
            cfg.hostPort = Math.max(1025, cfg.hostPort - 1);
            cfg.save();
        });
        button(ctx, x + 102, y + 28, 18, 16, "+", 0xFF2A2A3A, GRAY, mx, my, () -> {
            cfg.hostPort = Math.min(65535, cfg.hostPort + 1);
            cfg.save();
        });

        // gamemode
        ctx.drawText(textRenderer, "Mode: " + cfg.hostGamemode, x + 140, y + 32, WHITE, true);
        button(ctx, x + 230, y + 28, 70, 16, "CHANGE", 0xFF2A2A3A, GRAY, mx, my, () -> {
            cfg.hostGamemode = switch (cfg.hostGamemode) {
                case "survival" -> "creative";
                case "creative" -> "adventure";
                case "adventure" -> "spectator";
                default -> "survival";
            };
            cfg.save();
        });

        button(ctx, x, y + 52, 150, 16, "ALLOW CHEATS: " + (cfg.allowCommands ? "ON" : "OFF"),
                cfg.allowCommands ? 0xFF5E1B1B : 0xFF2A2A3A, cfg.allowCommands ? RED : GRAY, mx, my, () -> {
                    cfg.allowCommands = !cfg.allowCommands;
                    cfg.save();
                });

        button(ctx, x, y + 76, 150, 18, "OPEN WORLD TO FRIENDS", 0xFF1B5E3A, GREEN, mx, my, this::openWorld);
        button(ctx, x, y + 100, 150, 18, Stun.busy ? "LOOKING UP..." : "LOOKUP PUBLIC IP", 0xFF1B3A5E, CYAN, mx, my, Stun::lookupAsync);
        button(ctx, x, y + 124, 150, 18, "COPY JOIN ADDRESS", 0xFF4A1B4A, MAGENTA, mx, my, this::copyAddress);

        String ip = Stun.publicIp;
        String err = Stun.error;
        if (ip != null) {
            ctx.drawText(textRenderer, "Public IP: " + ip, x + 165, y + 105, GREEN, true);
        } else if (err != null) {
            ctx.drawText(textRenderer, "STUN failed: " + err, x + 165, y + 105, RED, false);
        }
        ctx.drawText(textRenderer, "Note: your router must forward TCP port " + cfg.hostPort + " to this PC.", x, y + 152, YELLOW, false);
        ctx.drawText(textRenderer, "Automatic NAT traversal comes in a later phase.", x, y + 163, GRAY, false);
    }

    private void renderFriends(DrawContext ctx, PlusCraftConfig cfg, int x, int y, int mx, int my, long now) {
        ctx.drawText(textRenderer, "Friends (" + cfg.friends.size() + ")", x, y, WHITE, true);

        // input row
        int iw = 200;
        ctx.fill(x, y + 14, x + iw, y + 30, CYAN);
        ctx.fill(x + 1, y + 15, x + iw - 1, y + 29, CARD);
        boolean blink = (now / 500) % 2 == 0;
        if (input.isEmpty()) ctx.drawText(textRenderer, "Type a name, press Enter...", x + 5, y + 19, 0xFF5A5A75, true);
        else ctx.drawText(textRenderer, input + (blink ? "_" : ""), x + 5, y + 19, WHITE, true);
        button(ctx, x + iw + 6, y + 14, 60, 16, "ADD", 0xFF1B5E3A, GREEN, mx, my, this::addFriend);

        // list
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
                "- Automatic NAT traversal / relay hosting",
                "- Cross-world chat, DMs and groups",
                "- Account manager (Microsoft + offline)",
                "- Animated capes, models and emotes",
                "- Media editor and Discord Rich Presence"
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
        if (tab == 1) {
            int code = keyInput.getKeycode();
            if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {
                addFriend();
                return true;
            }
            if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
                return true;
            }
            if (input.length() < 16) {
                if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
                    input += (char) ('a' + (code - GLFW.GLFW_KEY_A));
                    return true;
                }
                if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
                    input += (char) ('0' + (code - GLFW.GLFW_KEY_0));
                    return true;
                }
                if (code == GLFW.GLFW_KEY_MINUS) {
                    input += '_';
                    return true;
                }
            }
        }
        return super.keyPressed(keyInput);
    }
}
