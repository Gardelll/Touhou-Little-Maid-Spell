package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.SoulBookHealthProtection;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class SoulBookMaidDeathMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void maidspell$validateMaidDeath(DamageSource source, CallbackInfo ci) {
        if (!SoulBookHealthProtection.allowDeath((EntityMaid) (Object) this, source, true)) {
            ci.cancel();
        }
    }
}
