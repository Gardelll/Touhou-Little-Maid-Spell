package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.SoulBookHealthProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 魂之书女仆在原版伤害链上的结算点：记下本次 hurt 的伤害来源，致死结算后才放行 die，
 * 读档时的生命直接恢复。女仆 actuallyHurt 里的写血在 {@code tlm.SoulBookMaidSettleMixin}。
 */
@Mixin(LivingEntity.class)
public abstract class SoulBookDamageMixin {
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void maidspell$trackSettledDamage(LivingEntity target, DamageSource source, float amount,
                                            Operation<Void> original,
                                            @Share("maidspell$soulBookLethal") LocalBooleanRef lethal) {
        if (target instanceof EntityMaid maid && SoulBookHealthProtection.isProtected(maid)) {
            lethal.set(SoulBookHealthProtection.trackDamage(maid, source, () -> original.call(target, source, amount)));
        } else {
            original.call(target, source, amount);
        }
    }

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;die(Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void maidspell$admitSettledDeath(LivingEntity target, DamageSource source, Operation<Void> original,
                                           @Share("maidspell$soulBookLethal") LocalBooleanRef lethal) {
        if (target instanceof EntityMaid maid && lethal.get()) {
            SoulBookHealthProtection.runDamageDeath(maid, source, () -> original.call(target, source));
        } else {
            original.call(target, source);
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void maidspell$validateBaseDeath(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof EntityMaid maid && !SoulBookHealthProtection.allowDeath(maid, source, false)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "readAdditionalSaveData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void maidspell$restoreLoadedHealth(LivingEntity target, float health, Operation<Void> original) {
        if (target instanceof EntityMaid maid && !maid.level().isClientSide() && !maid.isAddedToLevel()) {
            SoulBookHealthProtection.restoreLoadedHealth(maid, health);
        } else {
            original.call(target, health);
        }
    }
}
