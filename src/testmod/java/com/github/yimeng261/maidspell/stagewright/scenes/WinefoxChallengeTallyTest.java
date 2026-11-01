package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTestAccess;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeProgress;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.function.Consumer;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星之魔女正式挑战的伤害归属（专用服）：假玩家拿星芒短剑开一场挑战，按不同来源逐记造成伤害，
 * 看战败结算时的受限判定与挑战者胜场。
 * 非玩家伤害用无来源的魔法伤害代替女仆、召唤物和环境：它们在占比里同属「玩家以外」。
 * 最后一击前用管理身份把生命压到 1，这一步不进伤害统计；生命低于一半会先转入二阶段，转场期间不受伤，
 * 所以等转场结束再打最后一击，它实际只扣这一点血。
 * 挑战者开创造模式，免得被她打到存活线以下；创造模式不影响伤害归属。
 */
@Scene("tlms")
@DisplayName("星之魔女：正式挑战的伤害归属")
@ResourceLock(value = "winefox.adaptiveDamageConfig", mode = ResourceAccessMode.READ)
class WinefoxChallengeTallyTest {
    private static final String WITCH = NS + "stellar_witch";
    /** 低于单次上限和高伤阈值，不触发高伤保护。 */
    private static final float HIT = 10.0F;

    private record Duel(MagicalWinefoxBossEntity witch, ServerPlayer challenger, Goal goal, int[] lastHit) {
    }

    @DisplayName("玩家以外的伤害占比超过上限时获胜判为受限，不记挑战者胜场")
    @Test
    void nonPlayerShareRestricts(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Duel duel = duel(ctx, "TlmsTallyOthers");
        int winsBefore = wins(ctx, duel);
        for (int i = 0; i < 3; i++) {
            strike(ctx, duel, "魔法", w -> w.hurt(w.damageSources().magic(), HIT));
        }
        finish(ctx, duel, "挑战者近战", w -> w.hurt(w.damageSources().playerAttack(duel.challenger()), HIT));
        ctx.run(() -> {
            ctx.check(duel.witch().isDefeated()).as("星之魔女战败").isTrue();
            ctx.check(duel.witch().isRestricted()).as("受限").isTrue();
            ctx.check(WinefoxChallengeProgress.getWins(ctx.server(), duel.challenger().getUUID())).as("挑战者胜场").isEqualTo(winsBefore);
        });
    }

    @DisplayName("挑战者的箭与荆棘反伤算玩家本人的伤害；最后一击是环境伤害也算挑战者获胜")
    @Test
    void projectileAndThornsCountAsChallenger(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Duel duel = duel(ctx, "TlmsTallyRanged");
        int winsBefore = wins(ctx, duel);
        strike(ctx, duel, "挑战者的箭", w -> {
            Arrow arrow = new Arrow(EntityType.ARROW, w.level());
            arrow.setOwner(duel.challenger());
            w.hurt(w.damageSources().arrow(arrow, duel.challenger()), HIT);
        });
        strike(ctx, duel, "挑战者的荆棘", w -> w.hurt(w.damageSources().thorns(duel.challenger()), HIT));
        finish(ctx, duel, "环境魔法", w -> w.hurt(w.damageSources().magic(), HIT));
        ctx.run(() -> {
            ctx.check(duel.witch().isDefeated()).as("星之魔女战败").isTrue();
            ctx.check(duel.witch().isRestricted()).as("受限").isFalse();
            ctx.check(WinefoxChallengeProgress.getWins(ctx.server(), duel.challenger().getUUID())).as("挑战者胜场").isEqualTo(winsBefore + 1);
        });
    }

    /** 其他模组或指令绕过伤害流程把生命直写成 0：没有来源，一滴有效伤害都没记下。 */
    @DisplayName("一滴有效伤害都没有就被判负时受限，不记挑战者胜场")
    @Test
    void noEffectiveDamageRestricts(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Duel duel = duel(ctx, "TlmsTallyNone");
        int winsBefore = wins(ctx, duel);
        lowToOne(ctx, duel);
        ctx.run(() -> duel.witch().setHealth(0.0F));
        ctx.await(5, "星之魔女战败", () -> duel.witch().isDefeated());
        ctx.run(() -> {
            ctx.check(duel.witch().isRestricted()).as("受限").isTrue();
            ctx.check(WinefoxChallengeProgress.getWins(ctx.server(), duel.challenger().getUUID())).as("挑战者胜场").isEqualTo(winsBefore);
        });
    }

    /** 生成星之魔女，假玩家拿星芒短剑右击她开战，等开场行礼结束进入战斗。 */
    private static Duel duel(Stage ctx, String name) {
        MagicalWinefoxBossEntity witch = ctx.get(() -> {
            MagicalWinefoxBossEntity w = Actors.spawn(ctx, WITCH, 0, 0, 0, false);
            w.setPersistenceRequired();
            return w;
        });
        ServerPlayer challenger = ctx.get(() -> {
            ServerPlayer p = Owners.visitor(ctx, name, 3, 0, 0);
            p.setGameMode(GameType.CREATIVE);
            p.setItemInHand(InteractionHand.MAIN_HAND, Actors.stack(NS + "starglint_dagger"));
            return p;
        });
        ctx.await(20, "星之魔女入世", () -> witch.tickCount >= 2);
        ctx.run(() -> {
            challenger.interactOn(witch, InteractionHand.MAIN_HAND);
            ctx.check(witch.isChallengeOngoingFor(challenger)).as("右击后挑战开始").isTrue();
        });
        ctx.await(200, "开场结束进入战斗", witch::isBattleActive);
        Goal goal = ctx.get(() -> {
            Goal found = StellarContentTest.combatGoal(witch);
            StellarContentTest.suppressSideBranches(found);
            return found;
        });
        return new Duel(witch, challenger, goal, new int[]{Integer.MIN_VALUE});
    }

    private static int wins(Stage ctx, Duel duel) {
        return ctx.get(() -> WinefoxChallengeProgress.getWins(ctx.server(), duel.challenger().getUUID()));
    }

    /** 等过受击间隔后打一记，要求真的掉了血。 */
    private static void strike(Stage ctx, Duel duel, String label, Consumer<MagicalWinefoxBossEntity> action) {
        MagicalWinefoxBossEntity witch = duel.witch();
        int gap = ctx.get(() -> Math.max(1, witch.maidspell$hitIntervalTicks()));
        ctx.await(gap + 20, label + "：等过受击间隔", () -> {
            StellarContentTest.suppressSideBranches(duel.goal());
            return duel.lastHit()[0] == Integer.MIN_VALUE || witch.tickCount - duel.lastHit()[0] >= gap;
        });
        ctx.run(() -> {
            witch.invulnerableTime = 0;
            witch.clearFire();
            float before = witch.maidspell$authoritativeHealth();
            action.accept(witch);
            float dealt = before - witch.maidspell$authoritativeHealth();
            duel.lastHit()[0] = witch.tickCount;
            ctx.record(label, dealt);
            if (dealt <= 0.0F) {
                throw ctx.fail(label + "没有造成伤害");
            }
        });
    }

    /** 把生命压到 1 后由这一记打败她。 */
    private static void finish(Stage ctx, Duel duel, String label, Consumer<MagicalWinefoxBossEntity> action) {
        lowToOne(ctx, duel);
        strike(ctx, duel, label, action);
    }

    /** 以管理身份把生命压到 1，等半血转阶段走完。 */
    private static void lowToOne(Stage ctx, Duel duel) {
        MagicalWinefoxBossEntity witch = duel.witch();
        ctx.run(() -> WinefoxTestAccess.setHealth(witch, 1.0F));
        ctx.await(300, "转入二阶段", () -> {
            StellarContentTest.suppressSideBranches(duel.goal());
            return witch.isPhaseTwo() && !witch.isTransitioning();
        });
        ctx.run(() -> ctx.check(witch.maidspell$authoritativeHealth()).as("转阶段后的生命").isCloseTo(1.0F, 1e-3));
    }
}
