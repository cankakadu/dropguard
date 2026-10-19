package net.dropguard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public final class ProtectionManager {
    private static boolean cursorFromProtectedSlot = false;
    private static long lastNotify = 0;

    private ProtectionManager() {}

    /** PlayerInventory-Index eines Slots, sonst -1 (Truhe, Crafting-Ergebnis, ...). */
    public static int playerInvIndex(Slot slot) {
        if (slot != null && slot.inventory instanceof PlayerInventory) return slot.getIndex();
        return -1;
    }

    public static boolean isItemProtected(ItemStack stack) {
        if (stack.isEmpty()) return false;
        DropGuardConfig cfg = DropGuardConfig.get();
        return cfg.itemBlacklistEnabled
                && cfg.hasItem(Registries.ITEM.getId(stack.getItem()).toString());
    }

    public static boolean isSlotProtected(int invIndex) {
        DropGuardConfig cfg = DropGuardConfig.get();
        return invIndex >= 0 && cfg.slotBlacklistEnabled && cfg.hasSlot(invIndex);
    }

    /** Darf der Stack in diesem Slot nicht gedroppt werden? */
    public static boolean shouldBlock(ItemStack stack, int invIndex) {
        if (stack.isEmpty()) return false;
        return isItemProtected(stack) || isSlotProtected(invIndex);
    }

    /** Drop des Cursor-Stacks (Klick außerhalb des Fensters). */
    public static boolean shouldBlockCursor(ItemStack cursor) {
        if (cursor.isEmpty()) return false;
        return isItemProtected(cursor)
                || (cursorFromProtectedSlot && DropGuardConfig.get().slotBlacklistEnabled);
    }

    /** Merkt, ob der Cursor-Stack aus einem geschützten Slot stammt. Vor dem Klick aufrufen. */
    public static void trackCursorOrigin(ItemStack cursor, Slot clicked, SlotActionType type) {
        if (type != SlotActionType.PICKUP || clicked == null || !clicked.hasStack()) {
            if (cursor.isEmpty()) cursorFromProtectedSlot = false;
            return;
        }
        boolean fromClicked = isSlotProtected(playerInvIndex(clicked));
        if (cursor.isEmpty()) {
            cursorFromProtectedSlot = fromClicked;
        } else if (!ItemStack.areItemsAndComponentsEqual(cursor, clicked.getStack())) {
            // Austausch: neuer Cursor-Inhalt stammt aus dem angeklickten Slot
            cursorFromProtectedSlot = fromClicked;
        }
    }

    public static void notifyBlocked() {
        long now = System.currentTimeMillis();
        if (now - lastNotify < 600) return;
        lastNotify = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.translatable("dropguard.blocked"), true);
    }
}
