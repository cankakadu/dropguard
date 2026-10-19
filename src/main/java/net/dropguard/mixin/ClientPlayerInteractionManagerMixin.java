package net.dropguard.mixin;

import net.dropguard.ProtectionManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alle Inventar-Klicks laufen hier durch. Wird ein Klick abgebrochen, wird weder
 * lokal noch per Paket etwas ausgeführt -> keine Desynchronisation.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void dropguard$blockSlotDrop(int syncId, int slotId, int button, SlotActionType actionType,
                                         PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null || handler.syncId != syncId) return;

        ItemStack cursor = handler.getCursorStack();
        Slot clicked = (slotId >= 0 && slotId < handler.slots.size()) ? handler.slots.get(slotId) : null;

        ProtectionManager.trackCursorOrigin(cursor, clicked, actionType);

        boolean block = false;
        if (actionType == SlotActionType.THROW && clicked != null) {
            // Q / Strg+Q im Inventar, Truhen etc.
            block = ProtectionManager.shouldBlock(clicked.getStack(), ProtectionManager.playerInvIndex(clicked));
        } else if (actionType == SlotActionType.PICKUP && slotId == ScreenHandler.EMPTY_SLOT_INDEX) {
            // Klick außerhalb des Fensters mit Item am Cursor
            block = ProtectionManager.shouldBlockCursor(cursor);
        }

        if (block) {
            ProtectionManager.notifyBlocked();
            ci.cancel();
        }
    }

    /** Creative-Inventar: Droppen per Klick außerhalb / Q. */
    @Inject(method = "dropCreativeStack", at = @At("HEAD"), cancellable = true, require = 0)
    private void dropguard$blockCreativeDrop(ItemStack stack, CallbackInfo ci) {
        if (ProtectionManager.isItemProtected(stack)) {
            ProtectionManager.notifyBlocked();
            ci.cancel();
        }
    }
}
