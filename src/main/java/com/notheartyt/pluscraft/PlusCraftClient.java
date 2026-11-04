package com.notheartyt.pluscraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlusCraftClient implements ClientModInitializer {
    public static final String MOD_ID = "pluscraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static PlusCraftConfig cfg;
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        cfg = PlusCraftConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pluscraft.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new PlusCraftScreen());
                }
            }
        });
    }
}
