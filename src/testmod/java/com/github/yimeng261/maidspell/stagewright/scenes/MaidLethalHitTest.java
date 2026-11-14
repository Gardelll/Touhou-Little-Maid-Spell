package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Hits;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;

/**
 * 敌对生物打女仆的致命一击：不戴饰品的女仆被怪物打到残血后，最后一击要照常致死，不能卡在一两点血。
 * 女仆的扣血经本模组的写血拦截，只有扣血量与战斗记录里最后一笔伤害对得上才写入；
 * 后两条模拟其他模组把致死写血截到 0 后再写入，此时扣血量（剩余生命）与记账伤害不一致。
 */
@Scene("tlms")
@DisplayName("敌对生物对女仆的致命一击")
class MaidLethalHitTest {
    /** 残血：玩家反馈卡住的"一两滴血"。 */
    private static final float LOW_HEALTH = 1.5F;
    /** 远大于残血的一击，扣血后生命为负。 */
    private static final float LETHAL = 6.0F;

    private static Mob husk(Stage ctx) {
        // 尸壳白天不自燃；无 AI 只是不让它乱走，doHurtTarget 仍按它的攻击力出手
        return Actors.spawn(ctx, "minecraft:husk", 1, 0, 0, true);
    }

    private static DamageSource mobAttack(Stage ctx, Mob attacker) {
        return Hits.source(ctx.level(), DamageTypes.MOB_ATTACK, attacker, attacker);
    }

    @DisplayName("尸壳反复近战，满血女仆一路被打到死亡，不卡在残血")
    @Test
    void mobMeleeKillsMaid(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, -1, 0, 0);
            Mob husk = husk(ctx);
            List<Float> trace = new ArrayList<>();
            trace.add(maid.getHealth());
            for (int swing = 0; swing < 40 && !maid.isDeadOrDying(); swing++) {
                maid.invulnerableTime = 0;
                husk.doHurtTarget(maid);
                trace.add(maid.getHealth());
            }
            ctx.record("每次出手后的女仆生命", trace);
            ctx.check(maid.isDeadOrDying()).as("40 次近战后女仆死亡，生命变化 " + trace).isTrue();
        });
    }

    @DisplayName("残血女仆挨一记超过剩余生命的怪物攻击后死亡")
    @Test
    void lethalMobHitKillsLowHealthMaid(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, -1, 0, 0);
            Mob husk = husk(ctx);
            Actors.setMaidHealth(maid, LOW_HEALTH);
            maid.invulnerableTime = 0;
            boolean landed = maid.hurt(mobAttack(ctx, husk), LETHAL);
            ctx.check(landed).as("致命一击命中").isTrue();
            ctx.check(maid.getHealth()).as("致命一击后的生命").isEqualTo(0.0F);
            ctx.check(maid.isDeadOrDying()).as("致命一击后女仆死亡").isTrue();
        });
    }

    @DisplayName("致死写血被截到 0 再写入（其他模组改写 setHealth 参数）时女仆仍死亡")
    @Test
    void clampedLethalSetHealthStillKills(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, -1, 0, 0);
            Mob husk = husk(ctx);
            Actors.setMaidHealth(maid, LOW_HEALTH);
            // 与原版 actuallyHurt 相同：先记账整笔伤害，再写血；写血参数被截成 0 而非 1.5 - 6
            maid.getCombatTracker().recordDamage(mobAttack(ctx, husk), LETHAL);
            maid.setHealth(0.0F);
            ctx.check(maid.getHealth()).as("截断后的致死写血落账").isEqualTo(0.0F);
        });
    }

    @DisplayName("致死写血绕过 setHealth、直接把截断后的 0 写进同步数据时女仆仍死亡")
    @Test
    void clampedLethalSyncedWriteStillKills(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, -1, 0, 0);
            Mob husk = husk(ctx);
            Actors.setMaidHealth(maid, LOW_HEALTH);
            maid.getCombatTracker().recordDamage(mobAttack(ctx, husk), LETHAL);
            // 原版 setHealth 本身就是把截到 [0, 上限] 的值写进同步数据
            maid.getEntityData().set(LivingEntityHealthAccessor.maidspell$getHealthAccessor(), 0.0F);
            ctx.check(maid.getHealth()).as("直接写入同步数据的致死写血落账").isEqualTo(0.0F);
        });
    }
}
