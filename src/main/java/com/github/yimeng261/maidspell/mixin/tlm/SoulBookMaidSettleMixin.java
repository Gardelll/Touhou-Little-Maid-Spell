package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.SoulBookHealthProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 女仆的 actuallyHurt 整个重写、不调父类，魂之书的结算写血只能挂在女仆自己的这次 setHealth 上。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class SoulBookMaidSettleMixin {
    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;setHealth(F)V"))
    private void maidspell$commitSettledDamage(EntityMaid target, float health, Operation<Void> original,
                                              DamageSource source) {
        if (SoulBookHealthProtection.isProtected(target)) {
            SoulBookHealthProtection.settleDamage(target, source, health);
        } else {
            original.call(target, health);
        }
    }
}
