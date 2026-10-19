package net.dropguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Persistente Konfiguration (config/dropguard.json).
 * Items: Registry-ID ("minecraft:diamond"). Slots: PlayerInventory-Index
 * (0-8 Hotbar, 9-35 Inventar, 36 Stiefel, 37 Hose, 38 Brust, 39 Helm, 40 Offhand).
 */
public final class DropGuardConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dropguard.json");
    private static DropGuardConfig instance;

    public boolean itemBlacklistEnabled = true;
    public boolean slotBlacklistEnabled = true;
    public Set<String> items = new LinkedHashSet<>();
    public Set<Integer> slots = new TreeSet<>();

    public static synchronized DropGuardConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static DropGuardConfig load() {
        DropGuardConfig cfg = null;
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, DropGuardConfig.class);
            } catch (Exception e) {
                DropGuardClient.LOGGER.error("Konfiguration beschädigt, sichere sie und starte neu", e);
                try {
                    Files.move(FILE, FILE.resolveSibling("dropguard.json.broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }
        if (cfg == null) cfg = new DropGuardConfig();
        cfg.sanitize();
        return cfg;
    }

    /** Entfernt Duplikate / ungültige Einträge. */
    private void sanitize() {
        Set<String> cleanItems = new LinkedHashSet<>();
        if (items != null) {
            for (String s : items) {
                if (s == null) continue;
                Identifier id = Identifier.tryParse(s.trim());
                if (id != null) cleanItems.add(id.toString());
            }
        }
        items = cleanItems;
        Set<Integer> cleanSlots = new TreeSet<>();
        if (slots != null) {
            for (Integer i : slots) if (i != null && i >= 0 && i <= 40) cleanSlots.add(i);
        }
        slots = cleanSlots;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("dropguard.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            DropGuardClient.LOGGER.error("Konfiguration konnte nicht gespeichert werden", e);
        }
    }

    public boolean hasItem(String id) { return items.contains(id); }
    public boolean hasSlot(int idx) { return slots.contains(idx); }

    public void toggleItem(String id) {
        if (!items.remove(id)) items.add(id);
        save();
    }

    public void toggleSlot(int idx) {
        if (!slots.remove(idx)) slots.add(idx);
        save();
    }

    public void clearItems() { items.clear(); save(); }
    public void clearSlots() { slots.clear(); save(); }

    public void resetAll() {
        itemBlacklistEnabled = true;
        slotBlacklistEnabled = true;
        items.clear();
        slots.clear();
        save();
    }
}
