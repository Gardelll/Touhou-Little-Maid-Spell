package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * 供自行选术、施法的附属模组接入本模组的施法相关效果：冷却（全局倍率、梦云水晶、时痕之戒等饰品修正）与「施法时」饰品（春花-返等）。
 * <p>
 * 冷却按 {@code modId} 写入该模组 provider 的女仆法术数据，与内置 provider 共用同一份；该 {@code modId} 没有 provider 时冷却相关调用不生效。
 * 全部只在服务端调用，客户端调用直接忽略。
 *
 * @since API 1
 */
public final class MaidSpellHooks {
    private MaidSpellHooks() {
    }

    /**
     * 一次法术生效时调用，触发「施法时」饰品效果。{@code target} 可为空（自身施法）；持续施法每次生效调用一次
     */
    public static void onSpellCast(EntityMaid maid, String modId, String spellId, @Nullable LivingEntity target) {
        if (maid.level().isClientSide()) {
            return;
        }
        SpringBloomReturnBauble.onSpellCast(maid, modId, spellId, target);
    }

    /**
     * 写入冷却。{@code baseTicks} 是 provider 自身公式算完的冷却（例如铁魔法的 {@code 冷却 × (2 − softCap(冷却缩减))}），
     * 这里只再叠加全局冷却倍率与饰品修正；单位 tick
     */
    public static void applyCooldown(EntityMaid maid, String modId, String spellId, int baseTicks) {
        ISpellBookProvider<?, ?> provider = serverProvider(maid, modId);
        if (provider != null) {
            provider.applySpellCooldown(maid, spellId, baseTicks);
        }
    }

    public static boolean isOnCooldown(EntityMaid maid, String modId, String spellId) {
        return getCooldownRemaining(maid, modId, spellId) > 0;
    }

    /**
     * 剩余冷却，单位 tick；不在冷却中为 0
     */
    public static int getCooldownRemaining(EntityMaid maid, String modId, String spellId) {
        ISpellBookProvider<?, ?> provider = serverProvider(maid, modId);
        return provider == null ? 0 : provider.getSpellCooldownRemaining(maid, spellId);
    }

    /**
     * 每个冷却中的法术缩短剩余冷却的 {@code ratio}（至少 1 tick）；{@code modId} 为空时作用于所有 provider，同春花-返
     */
    public static void refundCooldowns(EntityMaid maid, @Nullable String modId, double ratio) {
        if (maid.level().isClientSide()) {
            return;
        }
        if (modId == null) {
            SpellBookManager.getOrCreateManager(maid).refundCooldowns(maid, ratio);
            return;
        }
        ISpellBookProvider<?, ?> provider = SpellBookManager.getProvider(modId);
        if (provider != null) {
            provider.refundCooldowns(maid, ratio);
        }
    }

    @Nullable
    private static ISpellBookProvider<?, ?> serverProvider(EntityMaid maid, String modId) {
        return maid.level().isClientSide() ? null : SpellBookManager.getProvider(modId);
    }
}
