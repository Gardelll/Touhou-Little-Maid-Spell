package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class AnchorClientLevelMixin {
    @Inject(method = "removeEntity", at = @At("HEAD"), cancellable = true)
    private void maidspell$checkTrackedRemoval(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = ((ClientLevel) (Object) this).getEntity(id);
        if (ClientMaidRemovalGuard.shouldBlockRemoval(entity, id)) ci.cancel();
    }
}
