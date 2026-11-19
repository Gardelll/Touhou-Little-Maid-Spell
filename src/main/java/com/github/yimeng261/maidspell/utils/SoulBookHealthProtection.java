package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;

/**
 * 魂之书女仆的生命保护：掉血只在 hurt → actuallyHurt → setHealth 这条调用栈里结算一次再写入，
 * 死亡也只认这次结算把生命打到 0 之后由 hurt 发起的那一次。
 */
public final class SoulBookHealthProtection {
    private static final ThreadLocal<Damage> DAMAGE = new ThreadLocal<>();
    private static final ThreadLocal<Death> DEATH = new ThreadLocal<>();

    private SoulBookHealthProtection() {}

    public static boolean isProtected(EntityMaid maid) {
        // LivingEntity 构造时就会写入生命，那时女仆的饰品栏还没创建
        return !maid.level().isClientSide() && maid.getMaidBauble() != null
                && BaubleStateManager.hasBauble(maid, MaidSpellItems.SOUL_BOOK);
    }

    public static boolean trackDamage(EntityMaid maid, DamageSource source, Runnable actuallyHurt) {
        Damage previous = DAMAGE.get();
        Damage damage = new Damage(maid, source);
        DAMAGE.set(damage);
        try {
            actuallyHurt.run();
            return damage.lethal;
        } finally {
            if (previous == null) DAMAGE.remove();
            else DAMAGE.set(previous);
        }
    }

    public static void settleDamage(EntityMaid maid, DamageSource source, float requestedHealth) {
        Damage damage = DAMAGE.get();
        if (damage == null || damage.maid != maid || damage.source != source
                || damage.settled || !Float.isFinite(requestedHealth)) {
            return;
        }
        float before = maid.getHealth();
        if (!(before > 0.0F && requestedHealth < before)) return;
        damage.settled = true;

        var result = MaidDamageProcessor.processSettledMaidDamage(maid, requestedHealth);
        if (!result.writeHealth() || !Float.isFinite(result.health()) || maid.getHealth() != before) return;
        MaidHealthWriteGuard.writeAuthorized(maid, LivingEntityHealthAccessor.maidspell$getHealthAccessor(),
                Mth.clamp(result.health(), 0.0F, maid.getMaxHealth()), false);
        damage.lethal = maid.getHealth() == 0.0F;
    }

    public static void restoreLoadedHealth(EntityMaid maid, float health) {
        if (!maid.isAddedToLevel() && !maid.level().isClientSide() && Float.isFinite(health)) {
            MaidHealthWriteGuard.writeAuthorized(maid, LivingEntityHealthAccessor.maidspell$getHealthAccessor(),
                    Mth.clamp(health, 0.0F, maid.getMaxHealth()), false);
        }
    }

    public static void runDamageDeath(EntityMaid maid, DamageSource source, Runnable die) {
        if (maid.getHealth() != 0.0F || maid.isRemoved()) return;
        Death previous = DEATH.get();
        DEATH.set(new Death(maid, source));
        try {
            die.run();
        } finally {
            if (previous == null) DEATH.remove();
            else DEATH.set(previous);
        }
    }

    public static boolean allowDeath(EntityMaid maid, DamageSource source, boolean maidOverride) {
        Death death = DEATH.get();
        if (death == null || death.maid != maid) return !isProtected(maid);
        if (death.source != source || maid.getHealth() != 0.0F || maid.isRemoved()) return false;
        if (maidOverride) {
            if (death.enteredMaid || death.enteredBase) return false;
            death.enteredMaid = true;
        } else {
            if (death.enteredBase) return false;
            death.enteredBase = true;
        }
        return true;
    }

    private static final class Damage {
        final EntityMaid maid;
        final DamageSource source;
        boolean settled;
        boolean lethal;

        Damage(EntityMaid maid, DamageSource source) {
            this.maid = maid;
            this.source = source;
        }
    }

    private static final class Death {
        final EntityMaid maid;
        final DamageSource source;
        boolean enteredMaid;
        boolean enteredBase;

        Death(EntityMaid maid, DamageSource source) {
            this.maid = maid;
            this.source = source;
        }
    }
}
