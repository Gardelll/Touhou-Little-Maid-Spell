package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Hits;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.DataItem;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 生命写入守卫在同步数据层：其他模组在 setHealth 开头抢先把生命截到 [0, 上限] 直接写入并取消原方法时，
 * 女仆、魂之书主人、发簪转伤与破愈咒锋的规则照样生效。这里用"先记账、再把截断后的值直接写进同步数据"模拟这类模组。
 */
@Scene("tlms")
@DisplayName("生命写入守卫：同步数据层")
class HealthWriteGuardTest {
    private static final String SOUL_BOOK = NS + "soul_book";
    private static final String HAIRPIN = NS + "hairpin";
    private static final String WOUND_RIME = NS + "wound_rime_blade";

    /** 像抢先接管 setHealth 的模组那样，把截断后的生命直接写进同步数据。 */
    private static void foreignWrite(LivingEntity entity, float health) {
        entity.getEntityData().set(LivingEntityHealthAccessor.maidspell$getHealthAccessor(),
                Mth.clamp(health, 0.0F, entity.getMaxHealth()));
    }

    /** 原版伤害链的那一步：先记账，再由接管 setHealth 的模组写入截断后的生命。 */
    private static void foreignHurt(LivingEntity entity, DamageSource source, float damage) {
        entity.getCombatTracker().recordDamage(source, damage);
        foreignWrite(entity, entity.getHealth() - damage);
    }

    private static DamageSource mobAttack(Stage ctx, Mob attacker) {
        return Hits.source(ctx.level(), DamageTypes.MOB_ATTACK, attacker, attacker);
    }

    private static Mob husk(Stage ctx) {
        return Actors.spawn(ctx, "minecraft:husk", 2, 0, 0, true);
    }

    // ---- 女仆 ----

    @DisplayName("截断后的致死写入放行时，饰品结算拿到的是记账伤害而不是剩余生命")
    @Test
    void lethalClampSettlesRecordedDamage(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, -2, 0, 0);
            Mob husk = husk(ctx);
            Item hairpin = MaidSpellItems.HAIRPIN.get();
            Actors.equipBauble(maid, HAIRPIN);
            List<Float> settled = new ArrayList<>();
            Function<DataItem, Void> previous = Global.baubleSetHealthHandlers.get(hairpin);
            Global.baubleSetHealthHandlers.put(hairpin, item -> {
                settled.add(item.getAmount());
                return previous == null ? null : previous.apply(item);
            });
            ctx.cleanup(() -> {
                if (previous == null) {
                    Global.baubleSetHealthHandlers.remove(hairpin);
                } else {
                    Global.baubleSetHealthHandlers.put(hairpin, previous);
                }
            });
            Actors.setMaidHealth(maid, 2.0F);
            foreignHurt(maid, mobAttack(ctx, husk), 6.0F);
            ctx.check(maid.getHealth()).as("截断后的致死写入落账").isEqualTo(0.0F);
            ctx.check(settled).as("饰品结算拿到的伤害量").isEqualTo(List.of(6.0F));
        });
    }

    @DisplayName("上一 tick 的记账不能让同样数额的直接写入蒙混过去")
    @Test
    void staleEntryDoesNotPass(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid m = Actors.stillMaid(ctx, -2, 0, 0);
            m.invulnerableTime = 0;
            m.hurt(mobAttack(ctx, husk(ctx)), 3.0F);
            return m;
        });
        float afterHit = ctx.get(maid::getHealth);
        ctx.ticks(1);
        ctx.run(() -> {
            foreignWrite(maid, afterHit - 3.0F);
            ctx.check(maid.getHealth()).as("下一 tick 同额直接写入后的生命").isEqualTo(afterHit);
            foreignWrite(maid, 0.0F);
            ctx.check(maid.getHealth()).as("下一 tick 直接写 0 后的生命").isEqualTo(afterHit);
        });
    }

    @DisplayName("本 tick 有记账但数额对不上的直接写入被拦下")
    @Test
    void mismatchedWriteBlocked(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, -2, 0, 0);
            Actors.setMaidHealth(maid, 10.0F);
            maid.getCombatTracker().recordDamage(mobAttack(ctx, husk(ctx)), 3.0F);
            foreignWrite(maid, 2.0F);
            ctx.check(maid.getHealth()).as("记账 3、直接写到 2 后的生命").isEqualTo(10.0F);
        });
    }

    @DisplayName("记账伤害小于当前生命时，直接写 0 被拦下")
    @Test
    void zeroWriteBelowRecordedBlocked(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, -2, 0, 0);
            Actors.setMaidHealth(maid, 5.0F);
            maid.getCombatTracker().recordDamage(mobAttack(ctx, husk(ctx)), 1.0F);
            foreignWrite(maid, 0.0F);
            ctx.check(maid.getHealth()).as("记账 1、当前 5、直接写 0 后的生命").isEqualTo(5.0F);
        });
    }

    // ---- 魂之书主人保护 ----

    @DisplayName("魂之书保护主人：直接写同步数据的掉血被拦下，正常受伤照常扣血")
    @Test
    void soulBookBlocksForeignOwnerWrite(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsHealthOwner", 0, 0, 0);
            Players.ownedMaid(ctx, owner, 2, 0, 0, SOUL_BOOK);
            owner.setHealth(20.0F);
            foreignWrite(owner, 10.0F);
            ctx.check(owner.getHealth()).as("没有记账的直接写入后主人的生命").isEqualTo(20.0F);
            owner.invulnerableTime = 0;
            owner.hurt(owner.damageSources().generic(), 4.0F);
            ctx.check(owner.getHealth()).as("正常受 4 点伤害后主人的生命").isEqualTo(16.0F);
        });
    }

    @DisplayName("魂之书保护主人：残血主人挨一记超过剩余生命的正常伤害，照常扣到 0")
    @Test
    void soulBookLetsLethalHurtThrough(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsHealthOwner", 0, 0, 0);
            // 受保护的主人直接改低生命会被拦下，残血要在魂之书女仆到场之前布置
            owner.setHealth(2.0F);
            Players.ownedMaid(ctx, owner, 2, 0, 0, SOUL_BOOK);
            owner.invulnerableTime = 0;
            owner.hurt(owner.damageSources().generic(), 6.0F);
            float after = owner.getHealth();
            owner.setHealth(owner.getMaxHealth());
            ctx.check(after).as("致死一击后主人的生命").isEqualTo(0.0F);
        });
    }

    // ---- 发簪 ----

    @DisplayName("发簪转伤：主人残血时，女仆承受的是主人这一击的完整伤害，不被截成主人剩余生命")
    @Test
    void hairpinRedirectsFullDamageAtLowOwnerHealth(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsHealthOwner", 0, 0, 0);
            EntityMaid maid = Players.ownedMaid(ctx, owner, 2, 0, 0, HAIRPIN);
            owner.setHealth(2.0F);
            float maidBefore = maid.getHealth();
            maid.invulnerableTime = 0;
            maid.hurt(maid.damageSources().generic(), 6.0F);
            ctx.check(owner.getHealth()).as("转伤后主人的生命").isEqualTo(2.0F);
            ctx.check(maidBefore - maid.getHealth()).as("女仆承受的伤害").isEqualTo(6.0F);
            owner.setHealth(owner.getMaxHealth());
        });
    }

    // ---- 破愈咒锋 ----

    @DisplayName("破愈咒锋：被记下的敌人直接写同步数据回血也被拦下")
    @Test
    void woundRimeBlocksForeignHeal(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid sitting = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(sitting, WOUND_RIME);
            return sitting;
        });
        Mob zombie = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true));
        ctx.ticks(2);
        ctx.run(() -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 4.0F);
            ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as("命中后有禁疗记录").isTrue();
            float before = zombie.getHealth();
            foreignWrite(zombie, before + 2.0F);
            ctx.check(zombie.getHealth()).as("直接写同步数据回血后的生命").isEqualTo(before);
        });
    }
}
