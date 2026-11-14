package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercisBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;
import oshi.util.tuples.Pair;

import java.util.List;

public final class MaidDamageProcessor {

    private MaidDamageProcessor() {}

    public record MaidHealthChange(boolean writeHealth, float health) {
        public static MaidHealthChange block() {
            return new MaidHealthChange(false, 0.0f);
        }

        public static MaidHealthChange write(float health) {
            return new MaidHealthChange(true, health);
        }
    }

    /** 允许的记账伤害与实际掉血之差。 */
    private static final float HURT_TOLERANCE = 0.1f;

    /**
     * 把生命写到 requestedHealth 若是原版伤害链的一次正常受伤，返回这一击的伤害量，否则返回 NaN。
     *
     * <p>正常受伤先在战斗记录里记一笔、同一 tick 内再按同样的量扣血，所以只认本 tick 记的最后一笔。
     * 写入方（原版 setHealth 与直接写同步数据的模组）会把生命截到 [0, 上限]，致死一击写下的是 0 而不是负数，
     * 此时记账伤害不小于当前生命也算对上，返回记账伤害而不是被截断的掉血量。
     */
    public static float commonHurtDamage(float requestedHealth, LivingEntity entity) {
        CombatTracker tracker = entity.getCombatTracker();
        List<CombatEntry> entries = tracker.entries;
        if (entries.isEmpty() || tracker.lastDamageTime != entity.tickCount) {
            return Float.NaN;
        }
        float recorded = entries.get(entries.size() - 1).damage();
        float currentHealth = entity.getHealth();
        float dropped = currentHealth - requestedHealth;
        if (Math.abs(recorded - dropped) <= HURT_TOLERANCE) {
            return dropped;
        }
        if (requestedHealth <= 0.0f && recorded >= currentHealth - HURT_TOLERANCE) {
            return recorded;
        }
        return Float.NaN;
    }

    public static void processSoulBook(DataItem dataItem) {
        EntityMaid maid = dataItem.getMaid();
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.SOUL_BOOK)) {
            return;
        }
        Pair<Boolean, Float> result = SoulBookBauble.damageCalc(maid, dataItem.getAmount());
        if (!result.getA()) {
            dataItem.setCanceled(true);
            return;
        }
        dataItem.setAmount(result.getB());
        SoulBookBauble.recordHurt(maid);
    }

    public static void applyBaubleHandlers(DataItem dataItem, EntityMaid maid) {
        Global.baubleSetHealthHandlers.forEach((item, func) -> {
            if (BaubleStateManager.hasBauble(maid, item)) {
                func.apply(dataItem);
            }
        });
        Global.baubleSetHealthFinalHandlers.forEach((item, func) -> {
            if (BaubleStateManager.hasBauble(maid, item)) {
                func.apply(dataItem);
            }
        });
    }

    public static MaidHealthChange processMaidHealthDecrease(EntityMaid maid, float requestedHealth) {
        float currentHealth = maid.getHealth();
        float hurtDamage = commonHurtDamage(requestedHealth, maid);
        boolean commonHurt = !Float.isNaN(hurtDamage);
        DataItem dataItem = new DataItem(maid, commonHurt ? hurtDamage : currentHealth - requestedHealth);

        processSilverCercis(dataItem);

        if (!commonHurt) {
            return MaidHealthChange.block();
        }

        processSoulBook(dataItem);
        applyBaubleHandlers(dataItem, maid);

        if (dataItem.isCanceled()) {
            dataItem.setAmount(0.0f);
        }

        return MaidHealthChange.write(Math.max(0.0f, currentHealth - dataItem.getAmount()));
    }

    private static void processSilverCercis(DataItem dataItem) {
        EntityMaid maid = dataItem.getMaid();
        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.SLIVER_CERCIS)) {
            return;
        }
        LivingEntity target = maid.getLastAttacker();
        if (target == null) {
            return;
        }
        if (!IAuthoritativeHealth.combatAlive(target)) {
            target = maid.getTarget();
        }

        SilverCercisBauble.handleSilverCercis(maid, target, dataItem);
    }
}
