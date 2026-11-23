package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.damage.InfoDamageSource;
import com.github.yimeng261.maidspell.item.bauble.hairpin.HairpinBauble;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 发簪采样主人伤害时只借用主人的护甲减伤，不消耗主人护甲耐久；
 * Player#hurtArmor 与 Player#hurtHelmet 只负责扣耐久，拦截它们不影响减伤计算。
 */
@Mixin(Player.class)
public abstract class HairpinArmorDurabilityMixin {
    @Inject(method = {"hurtArmor", "hurtHelmet"}, at = @At("HEAD"), cancellable = true)
    private void maidspell$skipHairpinSampleArmorDamage(DamageSource source, float damage, CallbackInfo ci) {
        if (HairpinBauble.isSamplingOwnerDamage((Player) (Object) this)
                && source instanceof InfoDamageSource infoDamage
                && "hairpin_redirect".equals(infoDamage.msg_type)) {
            ci.cancel();
        }
    }
}
