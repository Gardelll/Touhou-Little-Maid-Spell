package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Hits;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.FriendlyDamageHealthGuard;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.Deque;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 强力防友伤（{@code powerfulFriendlyFireProtection}）：友方强制写血在 1 tick 窗口内被恢复、窗口内的移除被拦截，
 * 窗口过期后放开，敌方伤害照常致死，开关默认关闭时什么都不做；星之魔女的 hurt 早退路径不残留伤害帧。
 */
@Scene("tlms")
class FriendlyGuardTest {
    /** 打开开关，测试结束时还原（服务端线程）。 */
    private static void enable(Stage ctx) {
        boolean previous = Config.powerfulFriendlyFireProtection;
        Config.powerfulFriendlyFireProtection = true;
        ctx.cleanup(() -> Config.powerfulFriendlyFireProtection = previous);
    }

    /** 主人的法术伤害：直接伤害实体是法术实体（这里用不会动的标记实体代替），记在主人名下，打自己的宠物属于友方伤害（服务端线程）。 */
    private static DamageSource friendly(Stage ctx, ServerPlayer owner) {
        Entity spell = Actors.spawn(ctx, "minecraft:marker", 0, 0, 0, true);
        return Hits.magic(spell, owner);
    }

    /** 友方命中后一个 tick 内：强制把血写低被恢复到命中前，KILLED 原因的移除被拦下。 */
    @Test
    void restoresForcedWriteAndBlocksRemoval(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            enable(ctx);
            ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
            TamableAnimal wolf = Owners.pet(ctx, "minecraft:wolf", owner, 3, 0, 0);
            float full = wolf.getHealth();

            FriendlyDamageHealthGuard.onDamageAttempt(wolf, friendly(ctx, owner));
            wolf.setHealth(1.0F);
            ctx.check(wolf.getHealth()).as("友方命中后强制写到 1 点的生命").isEqualTo(full);

            wolf.remove(Entity.RemovalReason.KILLED);
            ctx.check(wolf.isRemoved()).as("窗口内的 KILLED 移除").isFalse();
            ctx.check(wolf.getHealth()).as("移除被拦下后的生命").isEqualTo(full);
        });
    }

    /** 窗口只有 1 tick：之后同样的强制写血不再被恢复。 */
    @Test
    void windowExpiresAfterOneTick(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        TamableAnimal wolf = ctx.get(() -> {
            enable(ctx);
            ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
            TamableAnimal pet = Owners.pet(ctx, "minecraft:wolf", owner, 3, 0, 0);
            FriendlyDamageHealthGuard.onDamageAttempt(pet, friendly(ctx, owner));
            return pet;
        });
        ctx.ticks(4);
        ctx.run(() -> {
            wolf.setHealth(1.0F);
            ctx.check(wolf.getHealth()).as("窗口过期后强制写到 1 点的生命").isEqualTo(1.0F);
        });
    }

    /** 开关默认关闭：友方命中后强制写血照常生效。 */
    @Test
    void offByDefaultDoesNothing(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ctx.check(Config.powerfulFriendlyFireProtection).as("默认值").isFalse();
            ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
            TamableAnimal wolf = Owners.pet(ctx, "minecraft:wolf", owner, 3, 0, 0);
            FriendlyDamageHealthGuard.onDamageAttempt(wolf, friendly(ctx, owner));
            wolf.setHealth(1.0F);
            ctx.check(wolf.getHealth()).as("关闭时强制写到 1 点的生命").isEqualTo(1.0F);
        });
    }

    /** 开启后敌对生物的正常攻击不被误拦：致命一击照常致死。 */
    @Test
    void hostileDamageStillKills(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            enable(ctx);
            ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
            TamableAnimal wolf = Owners.pet(ctx, "minecraft:wolf", owner, 3, 0, 0);
            LivingEntity zombie = Actors.spawn(ctx, "minecraft:zombie", -3, 0, 0, true);
            wolf.hurt(Hits.source(ctx.level(), DamageTypes.MOB_ATTACK, zombie, zombie), 1000.0F);
            ctx.check(wolf.isDeadOrDying()).as("敌对生物致命一击后宠物").isTrue();
        });
    }

    /**
     * 星之魔女的 hurt 在伤害量非正等早退分支里不进 super.hurt，RETURN 钩子不会清伤害帧；
     * 魔女自己用 finally 清掉，不能残留到 tick 末。
     */
    @Test
    @SuppressWarnings("unchecked")
    void winefoxEarlyExitLeavesNoFrame(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            enable(ctx);
            MagicalWinefoxBossEntity boss = Actors.spawn(ctx, NS + "stellar_witch", 4, 0, 0, true);
            LivingEntity attacker = Actors.spawn(ctx, "minecraft:zombie", -4, 0, 0, true);
            Owners.team(ctx, "TlmsGuardTeam", false);
            var scoreboard = ctx.server().getScoreboard();
            scoreboard.addPlayerToTeam(boss.getStringUUID(), scoreboard.getPlayerTeam("TlmsGuardTeam"));
            scoreboard.addPlayerToTeam(attacker.getStringUUID(), scoreboard.getPlayerTeam("TlmsGuardTeam"));
            DamageSource source = Hits.source(ctx.level(), DamageTypes.MOB_ATTACK, attacker, attacker);
            ThreadLocal<Deque<?>> frames = Reflect.field(null, FriendlyDamageHealthGuard.class, "DAMAGE_FRAMES");

            FriendlyDamageHealthGuard.onDamageAttempt(boss, source);
            ctx.check(frames.get().size()).as("同队生物的伤害被识别为友方，记了一帧").isEqualTo(1);
            FriendlyDamageHealthGuard.leaveDamageAttempt(boss, source);
            ctx.check(frames.get().size()).as("手动离开后的伤害帧").isEqualTo(0);

            boolean landed = boss.hurt(source, 0.0F);
            ctx.check(landed).as("伤害量为 0 的命中").isFalse();
            ctx.check(frames.get().size()).as("早退后残留的伤害帧").isEqualTo(0);
        });
    }
}
