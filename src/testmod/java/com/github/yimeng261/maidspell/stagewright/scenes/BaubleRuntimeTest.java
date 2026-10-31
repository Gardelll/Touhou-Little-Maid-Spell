package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Locks;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.List;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 饰品运行态按女仆隔离（专用服）：魂之书受伤间隔、破愈咒锋禁疗记录、紫荆银冠计数；
 * 卸下、死亡、卸载、换维度时清理，被复活饰品救下时保留。魂之书的主人保护见 {@link BaubleRuntimeIntegratedTest}。
 */
@DisplayName("饰品运行态：按女仆隔离与清理")
@ResourceLock(Locks.TICK_RATE)
@Scene("tlms")
class BaubleRuntimeTest {
    static final String SOUL_BOOK = NS + "soul_book";
    private static final String WOUND_RIME = NS + "wound_rime_blade";
    private static final String CERCIS = NS + "sliver_cercis";
    private static final String CRYSTAL = NS + "dream_cat_crystal";

    // ---- 魂之书 ----

    /**
     * 受伤间隔：卸下再装备后，旧的受伤时间被清掉；重新装备后第一次受击按首次受击记录，
     * 间隔超过阈值的下一次受击照常扣血。
     */
    @DisplayName("魂之书卸下再装备后旧受伤时间被清掉，隔过阈值的下一次受击照常扣血")
    @Test
    void intervalAfterReequip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        EntityMaid maid = ctx.get(() -> {
            EntityMaid sitting = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(sitting, SOUL_BOOK);
            return sitting;
        });
        int gap = Config.soulBookDamageIntervalThreshold + 2;
        ctx.ticks(2);
        ctx.run(() -> {
            hurt(maid, 2);
            ctx.check(Maids.soulBookRecorded(maid.getUUID())).as("受伤后记录了受伤时间").isTrue();
            Maids.takeOffAll(maid);
            ctx.check(Maids.soulBookRecorded(maid.getUUID())).as("卸下后仍有受伤时间记录").isFalse();
            Actors.equipBauble(maid, SOUL_BOOK);
            float first = hurt(maid, 2);
            ctx.record("firstHitAfterReequip", first);
        });
        ctx.ticks(gap);
        ctx.run(() -> ctx.check(hurt(maid, 2)).as("重新装备后隔 " + gap + " tick 的受击扣血").isGreaterThan(0F));
    }

    /** 把生命回满后受一次通用伤害，返回实际扣掉的生命。 */
    static float hurt(LivingEntity entity, float amount) {
        if (entity instanceof EntityMaid maid) {
            Actors.setMaidHealth(maid, maid.getMaxHealth());
        } else {
            entity.setHealth(entity.getMaxHealth());
        }
        entity.invulnerableTime = 0;
        float before = entity.getHealth();
        entity.hurt(entity.damageSources().generic(), amount);
        return before - entity.getHealth();
    }

    // ---- 破愈咒锋 ----

    /** 被女仆打过的敌人回血被拦下；记录次数用完后恢复正常回血。 */
    @DisplayName("破愈咒锋拦下被女仆打过的敌人回血，记录次数用完后恢复正常回血")
    @Test
    void blocksHealingUntilUsedUp(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        EntityMaid maid = ctx.get(() -> {
            EntityMaid sitting = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(sitting, WOUND_RIME);
            return sitting;
        });
        Mob zombie = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true));
        ctx.ticks(2);
        ctx.run(() -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 4);
            ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as("命中后有禁疗记录").isTrue();
            int blocked = 0;
            int limit = Config.woundRimeBladeRecordTimes * 2 + 2;
            while (blocked < limit) {
                float before = zombie.getHealth();
                zombie.heal(1);
                if (zombie.getHealth() > before) {
                    break;
                }
                blocked++;
            }
            ctx.record("blockedHeals", blocked);
            ctx.check(blocked).as("被拦下的回血次数").isGreaterThan(0);
            ctx.check(blocked).as("被拦下的回血次数（次数用完应恢复）").isLessThan(limit);
            float before = zombie.getHealth();
            zombie.heal(1);
            ctx.check(zombie.getHealth()).as("记录用完后回血").isGreaterThan(before);
        });
    }

    /** 玩家和女仆不会被记录。 */
    @DisplayName("破愈咒锋不记录玩家和女仆，被打的女仆照常回血")
    @Test
    void skipsPlayersAndMaids(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        EntityMaid maid = ctx.get(() -> {
            EntityMaid sitting = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(sitting, WOUND_RIME);
            return sitting;
        });
        EntityMaid other = ctx.get(() -> Actors.stillMaid(ctx, 0, 0, 2));
        ctx.ticks(2);
        ctx.run(() -> {
            other.hurt(maid.damageSources().mobAttack(maid), 2);
            ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as("打女仆后有禁疗记录").isFalse();
            Actors.setMaidHealth(other, other.getMaxHealth() - 4);
            float before = other.getHealth();
            other.heal(2);
            ctx.check(other.getHealth()).as("被打过的女仆回血").isGreaterThan(before);
        });
    }

    @DisplayName("敌人死亡后破愈咒锋的禁疗记录被清掉，同 UUID 实体回来照常回血")
    @Test
    void noResidueAfterDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        woundRimeResidue(Stage.flat(game, plot, reporter).fastForward(), "death");
    }

    @DisplayName("敌人卸载后破愈咒锋的禁疗记录被清掉，同 UUID 实体回来照常回血")
    @Test
    void noResidueAfterUnload(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        woundRimeResidue(Stage.flat(game, plot, reporter).fastForward(), "unload");
    }

    @DisplayName("敌人换维度后破愈咒锋的禁疗记录被清掉，同 UUID 实体回来照常回血")
    @Test
    void noResidueAfterDimensionChange(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        woundRimeResidue(Stage.flat(game, plot, reporter).fastForward(), "dimension");
    }

    /** 敌人死亡、卸载或换维度后记录被清掉：同 UUID 的实体回来后照常回血。 */
    private static void woundRimeResidue(Stage ctx, String how) {
        EntityMaid maid = ctx.get(() -> {
            EntityMaid sitting = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(sitting, WOUND_RIME);
            return sitting;
        });
        Mob zombie = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true));
        UUID id = zombie.getUUID();
        ctx.ticks(2);
        ctx.run(() -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 4);
            ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as("命中后有禁疗记录").isTrue();
            switch (how) {
                case "death" -> zombie.kill();
                case "unload" -> zombie.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                default -> {
                    ServerLevel nether = ctx.server().getLevel(Level.NETHER);
                    Entity moved = zombie.changeDimension(new DimensionTransition(nether, zombie.position(), Vec3.ZERO,
                            0, 0, DimensionTransition.DO_NOTHING));
                    if (moved != null) {
                        ctx.cleanup(() -> Actors.cleanupEntity(moved));
                    }
                }
            }
        });
        // 女仆每 tick 清理不在场或已死亡的目标
        ctx.ticks(5);
        ctx.run(() -> {
            ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as(how + " 后仍有禁疗记录").isFalse();
            Mob again = Actors.spawn(ctx, "minecraft:zombie", 0, 0, -2, true, (Mob mob) -> mob.setUUID(id));
            again.setHealth(again.getMaxHealth() - 4);
            float before = again.getHealth();
            again.heal(2);
            ctx.check(again.getHealth()).as(how + " 后同 UUID 的实体回血").isGreaterThan(before);
        });
    }

    // ---- 紫荆银冠 ----

    /** 受伤计数在卸下饰品后清空，重新装备后从头计。 */
    @DisplayName("紫荆银冠的受伤计数在卸下饰品后清空，重新装备后从头计")
    @Test
    void resetOnTakeOff(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        EntityMaid maid = ctx.get(() -> {
            EntityMaid sitting = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(sitting, CERCIS);
            return sitting;
        });
        Mob zombie = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true));
        ctx.ticks(2);
        ctx.run(() -> {
            for (int i = 0; i < 2; i++) {
                maid.setLastHurtByMob(zombie);
                hurtBy(maid, zombie);
            }
            ctx.record("countBeforeTakeOff", Maids.silverCercisCount(maid.getUUID()));
            ctx.check(Maids.silverCercisCount(maid.getUUID())).as("受击后有计数").isNotNull();
            Maids.takeOffAll(maid);
            ctx.check(Maids.silverCercisCount(maid.getUUID())).as("卸下后的计数").isNull();
            Actors.equipBauble(maid, CERCIS);
            maid.setLastHurtByMob(zombie);
            hurtBy(maid, zombie);
            Integer count = Maids.silverCercisCount(maid.getUUID());
            ctx.check(count == null || count <= 1).as("重新装备后第一次受击后的计数（" + count + "）从头开始").isTrue();
        });
    }

    private static void hurtBy(EntityMaid maid, Mob attacker) {
        Actors.setMaidHealth(maid, maid.getMaxHealth());
        maid.invulnerableTime = 0;
        maid.hurt(maid.damageSources().mobAttack(attacker), 2);
    }

    // ---- 复活与死亡 ----

    /** 被梦云水晶救下（死亡事件取消）后，魂之书受伤时间、破愈咒锋禁疗记录、紫荆银冠计数都保留。 */
    @DisplayName("被梦云水晶救下后，魂之书、破愈咒锋、紫荆银冠的运行态都保留")
    @Test
    void runtimeKeptWhenRevived(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        EntityMaid maid = ctx.get(() -> {
            EntityMaid sitting = Actors.sittingMaid(ctx, 0, 0, 0);
            Maids.equip(sitting, CRYSTAL, SOUL_BOOK, WOUND_RIME, CERCIS);
            return sitting;
        });
        Mob zombie = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true));
        UUID id = maid.getUUID();
        ctx.ticks(2);
        ctx.run(() -> {
            buildRuntimeState(maid, zombie);
            ctx.record("before", state(id));
        });
        ctx.ticks(Config.soulBookDamageIntervalThreshold + 2);
        ctx.run(() -> {
            Maids.kill(maid);
            ctx.check(maid.isAlive()).as("梦云水晶救下女仆").isTrue();
            ctx.check(maid.getHealth()).as("复活恢复满血").isCloseTo(maid.getMaxHealth(), 1e-3f);
            ctx.check(state(id)).as("复活后的运行态（魂之书/破愈咒锋/紫荆银冠）").isEqualTo(List.of(true, true, true));
        });
    }

    /** 真正死亡时三种运行态都清掉。 */
    @DisplayName("女仆真正死亡时，魂之书、破愈咒锋、紫荆银冠三种运行态都清掉")
    @Test
    void runtimeClearedOnDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        EntityMaid maid = ctx.get(() -> {
            EntityMaid sitting = Actors.sittingMaid(ctx, 0, 0, 0);
            Maids.equip(sitting, SOUL_BOOK, WOUND_RIME, CERCIS);
            return sitting;
        });
        Mob zombie = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true));
        UUID id = maid.getUUID();
        ctx.ticks(2);
        ctx.run(() -> {
            buildRuntimeState(maid, zombie);
            ctx.check(state(id)).as("死亡前的运行态").isEqualTo(List.of(true, true, true));
        });
        ctx.ticks(Config.soulBookDamageIntervalThreshold + 2);
        ctx.run(() -> {
            Maids.kill(maid);
            ctx.check(maid.isAlive()).as("女仆已死亡").isFalse();
            ctx.check(state(id)).as("死亡后的运行态").isEqualTo(List.of(false, false, false));
        });
    }

    private static void buildRuntimeState(EntityMaid maid, Mob zombie) {
        hurt(maid, 2);
        zombie.hurt(maid.damageSources().mobAttack(maid), 2);
        maid.setLastHurtByMob(zombie);
        hurtBy(maid, zombie);
    }

    private static List<Boolean> state(UUID id) {
        return List.of(Maids.soulBookRecorded(id), Maids.woundRimeRecorded(id), Maids.silverCercisCount(id) != null);
    }
}
