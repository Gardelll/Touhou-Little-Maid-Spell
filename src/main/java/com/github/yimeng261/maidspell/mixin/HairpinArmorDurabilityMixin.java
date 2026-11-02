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
 * 发簪采样主人伤害时，跳过主人护甲耐久消耗。
 *
 * <p>发簪转发会走玩家完整 hurt 流程，护甲耐久在 setHealth 之前就被扣掉；
 * 这里在 Player#hurtArmor 与 Player#hurtHelmet 处拦截，二者都只负责扣耐久。
 * 只对 hairpin_redirect 采样伤害生效，不影响其他来源的真实伤害，
 * 也不影响护甲值参与 CombatRules 的减伤计算。
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
