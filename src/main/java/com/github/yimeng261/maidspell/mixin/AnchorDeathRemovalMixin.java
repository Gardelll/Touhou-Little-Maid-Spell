package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class AnchorDeathRemovalMixin {
    @Shadow protected boolean dead;

    @Redirect(method = "tickDeath", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void maidspell$authorizeCompletedDeath(LivingEntity entity, Entity.RemovalReason reason) {
        if (dead && entity instanceof EntityMaid maid) {
            MaidHardRemovalProtection.runAuthorizedRemoval(maid, () -> maid.remove(reason));
        } else {
            entity.remove(reason);
        }
    }
}
