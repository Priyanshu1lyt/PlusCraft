package com.notheartyt.pluscraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Simple JSON config stored in config/pluscraft.json. */
public class PlusCraftConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public List<String> friends = new ArrayList<>();
    public int hostPort = 25565;
    public String hostGamemode = "survival";
    public boolean allowCommands = false;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("pluscraft.json");
    }

    public static PlusCraftConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                PlusCraftConfig c = GSON.fromJson(Files.readString(p), PlusCraftConfig.class);
                if (c != null) {
                    if (c.friends == null) c.friends = new ArrayList<>();
                    if (c.hostGamemode == null) c.hostGamemode = "survival";
                    return c;
                }
            }
        } catch (Exception e) {
            PlusCraftClient.LOGGER.warn("Could not read config, using defaults", e);
        }
        return new PlusCraftConfig();
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            PlusCraftClient.LOGGER.warn("Could not save config", e);
        }
    }
}
