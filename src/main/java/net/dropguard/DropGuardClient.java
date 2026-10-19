package net.dropguard;

import net.dropguard.gui.BlacklistScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DropGuardClient implements ClientModInitializer {
    public static final String MOD_ID = "dropguard";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding OPEN_KEY;

    @Override
    public void onInitializeClient() {
        DropGuardConfig.get();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dropguard.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new BlacklistScreen());
                }
            }
        });
    }
}
