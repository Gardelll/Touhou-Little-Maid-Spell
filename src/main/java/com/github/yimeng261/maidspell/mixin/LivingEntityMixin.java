package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.FoxLeafOwnerEffectHelper;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * LivingEntity 的 Mixin：权威生命上限、女仆效果拦截与水上行走。
 * 生命写入的守卫在 {@link SynchedEntityDataHealthMixin}。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getMaxHealth", at = @At("HEAD"), cancellable = true)
    private void maidspell$readAuthoritativeMaxHealth(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof IAuthoritativeHealth authority) {
            cir.setReturnValue(authority.maidspell$authoritativeMaxHealth());
        }
    }

    @Shadow
    public abstract MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> effect);

    @Shadow
    protected abstract void onEffectRemoved(MobEffectInstance effectInstance);

    @Shadow public abstract void remove(Entity.RemovalReason pReason);

    /**
     * @Redirect 重定向 addEffect 中的 activeEffects.put 调用。
     *
     * <p>拦截点在 NeoForge 事件 post 之后、效果真正写入 Map 之前。
     * 若 baubleEffectBlockFilters 中有任意过滤器返回 true，则不写入 Map，
     * 效果既不会 tick，也不会显示图标/粒子。
     *
     * <p>同时设置 {@link Global#effectBlockFlag} 通知 MobEffectMixin 阻止属性修改器应用。
     */
    @SuppressWarnings("unchecked")
    @Redirect(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private Object maidspell$redirectEffectPut(Map<Holder<MobEffect>, MobEffectInstance> map,
                                               Object key, Object value) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof EntityMaid maid
                && maidspell$shouldBlockMaidEffect(maid, (Holder<MobEffect>) key)) {
            Global.effectBlockFlag.set(Boolean.TRUE);
            return null;
        }
        return map.put((Holder<MobEffect>) key, (MobEffectInstance) value);
    }

    /**
     * 部分模组绕过 LivingEntity#addEffect，直接写入 activeEffects，
     * 然后调用 vanilla 的更新钩子。在此清理已经写入的效果。
     */
    @Inject(method = "onEffectAdded", at = @At("HEAD"), cancellable = true)
    private void maidspell$blockDirectEffectAdded(MobEffectInstance effectInstance,
                                                  Entity source,
                                                  CallbackInfo ci) {
        maidspell$cancelBlockedDirectEffect(effectInstance, ci);
    }

    @Inject(method = "onEffectUpdated", at = @At("HEAD"), cancellable = true)
    private void maidspell$blockDirectEffectUpdated(MobEffectInstance effectInstance,
                                                    boolean reapplyAttributeModifiers,
                                                    Entity source,
                                                    CallbackInfo ci) {
        maidspell$cancelBlockedDirectEffect(effectInstance, ci);
    }

    @Unique
    private void maidspell$cancelBlockedDirectEffect(MobEffectInstance effectInstance, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof EntityMaid maid
                && maidspell$shouldBlockMaidEffect(maid, effectInstance.getEffect())) {
            MobEffectInstance removed = this.removeEffectNoUpdate(effectInstance.getEffect());
            if (removed != null) {
                this.onEffectRemoved(removed);
            }
            // 取消后 addAttributeModifiers 不会执行，旗标要在这里清掉，否则会吞掉下一个效果的属性修改器
            Global.effectBlockFlag.remove();
            ci.cancel();
        }
    }

    @Unique
    private boolean maidspell$shouldBlockMaidEffect(EntityMaid maid, Holder<MobEffect> effect) {
        for (Map.Entry<Item, BiFunction<EntityMaid, Holder<MobEffect>, Boolean>> entry
                : Global.baubleEffectBlockFilters.entrySet()) {
            if (BaubleStateManager.hasBauble(maid, entry.getKey())
                    && Boolean.TRUE.equals(entry.getValue().apply(maid, effect))) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "canStandOnFluid(Lnet/minecraft/world/level/material/FluidState;)Z", at = @At("HEAD"), cancellable = true)
    private void maidspell$allowWaterWalk(FluidState fluidState, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof Player player) {
            if (FoxLeafOwnerEffectHelper.canOwnerStandOnFluid(player, fluidState)) {
                cir.setReturnValue(true);
            }
            return;
        }
        if (!(self instanceof EntityMaid maid) || maid.isUnderWater()) {
            return;
        }
        if (fluidState.is(FluidTags.WATER) && BaubleStateManager.hasBauble(maid, MaidSpellItems.FLOATING_FOX_LEAF)) {
            cir.setReturnValue(true);
            return;
        }
        if (fluidState.is(FluidTags.LAVA) && BaubleStateManager.hasBauble(maid, MaidSpellItems.MOLTEN_FOX_LEAF)) {
            cir.setReturnValue(true);
        }
    }

}
