package net.dropguard.mixin;

import net.dropguard.ProtectionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Q / Strg+Q außerhalb von Inventar-Screens. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void dropguard$blockDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        PlayerInventory inv = self.getInventory();
        int idx = inv.getSelectedSlot();
        if (ProtectionManager.shouldBlock(inv.getStack(idx), idx)) {
            ProtectionManager.notifyBlocked();
            cir.setReturnValue(false);
        }
    }
}
