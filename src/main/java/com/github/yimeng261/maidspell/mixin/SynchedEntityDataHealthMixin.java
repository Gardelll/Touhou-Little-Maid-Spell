package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.api.IBossSyncedDataGuard;
import com.github.yimeng261.maidspell.item.bauble.hairpin.HairpinBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.utils.MaidDamageProcessor;
import com.github.yimeng261.maidspell.utils.MaidHealthWriteGuard;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 生命写入的守卫：原版 setHealth 与直接写同步数据的模组最终都落到这里，
 * 其他模组在 setHealth 开头抢先写入并取消原方法时也绕不过去。
 * 这里拿到的写入值已被写入方截到 [0, 上限]。
 */
@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataHealthMixin {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private SyncedDataHolder entity;

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private <T> void maidspell$guardHealthWrites(EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
        if (this.entity instanceof IBossSyncedDataGuard boss
                && boss.maidspell$protectSyncedDataWrite(accessor, value)) {
            ci.cancel();
            return;
        }
        if (MaidHealthWriteGuard.isBypassing()) {
            return;
        }
        if (!maidspell$isHealthAccessor(accessor)) {
            return;
        }
        if (!(value instanceof Float requestedHealth)) {
            return;
        }
        if (!(this.entity instanceof LivingEntity living) || living.level().isClientSide()) {
            return;
        }

        if (requestedHealth >= IAuthoritativeHealth.health(living)) {
            // 被破愈咒锋记下的敌人回血被拦下
            if (WoundRimeBladeBauble.handleWoundRimeMap(living, requestedHealth)) {
                ci.cancel();
            }
            return;
        }
        if (living instanceof ServerPlayer player) {
            maidspell$guardPlayerHealthDecrease(player, requestedHealth, ci);
        } else if (living instanceof EntityMaid maid) {
            maidspell$guardMaidHealthDecrease(maid, requestedHealth, force, ci);
        }
    }

    /** 发簪试探主人受伤时截下伤害量；魂之书保护主人时，不是正常受伤的掉血被拦下。 */
    @Unique
    private static void maidspell$guardPlayerHealthDecrease(ServerPlayer player, float requestedHealth, CallbackInfo ci) {
        float hurtDamage = MaidDamageProcessor.commonHurtDamage(requestedHealth, player);
        boolean commonHurt = !Float.isNaN(hurtDamage);
        float dropped = commonHurt ? hurtDamage : player.getHealth() - requestedHealth;
        if (HairpinBauble.captureRedirectedDamage(player, dropped)) {
            player.invulnerableTime = 0;
            ci.cancel();
            return;
        }
        if (!commonHurt && SoulBookBauble.hasOwnerProtection(player.getUUID())) {
            ci.cancel();
        }
    }

    /** 女仆掉血只认正常受伤，并经饰品结算后写入；生命上限下降后截到上限不是伤害，照常写入。 */
    @Unique
    private static void maidspell$guardMaidHealthDecrease(EntityMaid maid, float requestedHealth, boolean force, CallbackInfo ci) {
        if (requestedHealth >= maid.getMaxHealth()) {
            return;
        }
        MaidDamageProcessor.MaidHealthChange healthChange = MaidDamageProcessor.processMaidHealthDecrease(maid, requestedHealth);
        ci.cancel();
        if (healthChange.writeHealth()) {
            MaidHealthWriteGuard.runBypassing(() -> maid.getEntityData().set(maidspell$healthAccessor(), healthChange.health(), force));
        }
    }

    @Unique
    private static EntityDataAccessor<Float> maidspell$healthAccessor() {
        return LivingEntityHealthAccessor.maidspell$getHealthAccessor();
    }

    @Unique
    private static boolean maidspell$isHealthAccessor(EntityDataAccessor<?> accessor) {
        return accessor == maidspell$healthAccessor();
    }
}
