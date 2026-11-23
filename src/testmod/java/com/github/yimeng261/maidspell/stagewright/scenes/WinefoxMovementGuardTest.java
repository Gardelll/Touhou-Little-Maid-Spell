package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.winefox.WinefoxMovementGuard;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星之魔女的外部位移判定：判定状态机本身（加载窗口、自身 aiStep、显式放行、客户端放行、调试开关、拒绝计数），
 * 以及实体加入世界后外部位置与速度写入被拒、骑乘双向被拒、原版 /tp 静默失败、管理员 tp 子命令仍能移动她。
 */
@Scene("tlms")
@DisplayName("星之魔女：外部位移判定")
class WinefoxMovementGuardTest {
    private static final String WITCH = NS + "stellar_witch";

    @DisplayName("判定状态机：未武装放行，武装后只有自身 aiStep 或显式放行期间可写，嵌套按序退出")
    @Test
    void guardStateMachine(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            WinefoxMovementGuard guard = new WinefoxMovementGuard();
            ctx.check(guard.canChange(false, true)).as("未武装时（生成、读档）允许写入").isTrue();
            ctx.check(guard.blocksExternalWrites(false, true)).as("未武装时外部写入是否被拒").isFalse();
            guard.arm();
            ctx.check(guard.canChange(false, true)).as("武装后外部写入").isFalse();
            ctx.check(guard.blocksExternalWrites(false, true)).as("武装后外部写入是否被拒").isTrue();

            guard.enterTrusted();
            guard.enterTrusted();
            ctx.check(guard.isTrusted()).as("进入自身 aiStep").isTrue();
            guard.exitTrusted();
            ctx.check(guard.canChange(false, true)).as("退出内层 aiStep 后外层仍可写").isTrue();
            guard.exitTrusted();
            ctx.check(guard.canChange(false, true)).as("aiStep 全部退出后").isFalse();

            guard.enterBypass();
            guard.enterBypass();
            guard.exitBypass();
            ctx.check(guard.canChange(false, true)).as("退出内层放行后外层仍可写").isTrue();
            ctx.check(guard.isTrusted()).as("显式放行不计入 aiStep 深度").isFalse();
            guard.exitBypass();
            ctx.check(guard.canChange(false, true)).as("放行全部退出后").isFalse();

            guard.exitTrusted();
            guard.exitBypass();
            ctx.check(guard.canChange(false, true)).as("多余的退出不会把深度减成负数").isFalse();
            guard.enterTrusted();
            ctx.check(guard.canChange(false, true)).as("多余退出之后再进入一次 aiStep").isTrue();
            guard.exitTrusted();

            ctx.check(guard.canChange(true, true)).as("客户端始终放行").isTrue();
            ctx.check(guard.blocksExternalWrites(false, false)).as("关闭配置时外部写入是否被拒").isFalse();
            guard.disarm();
            ctx.check(guard.canChange(false, true)).as("解除武装回到加载窗口").isTrue();
            ctx.check(guard.isArmed()).as("解除武装后的状态").isFalse();
        });
    }

    @DisplayName("拒绝记录按入口分别计数，可清零")
    @Test
    void rejectionsCountedPerEntryPoint(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            WinefoxMovementGuard guard = new WinefoxMovementGuard();
            guard.arm();
            guard.recordRejection("setPos");
            guard.recordRejection("setPos");
            guard.recordRejection("setDeltaMovement");
            ctx.check(guard.rejectedCount("setPos")).as("setPos 被拒次数").isEqualTo(2);
            ctx.check(guard.rejectedCount("setDeltaMovement")).as("setDeltaMovement 被拒次数").isEqualTo(1);
            ctx.check(guard.rejectedCount("teleportTo")).as("teleportTo 被拒次数").isEqualTo(0);
            ctx.check(guard.lastRejected()).as("最近一次被拒的入口").isEqualTo("setDeltaMovement");
            ctx.check(guard.describe()).as("诊断摘要").contains("counts=");
            guard.resetRejections();
            ctx.check(guard.rejectedCount("setPos")).as("清零后 setPos 被拒次数").isEqualTo(0);
            ctx.check(guard.lastRejected()).as("清零后最近一次被拒的入口").isEqualTo("");
        });
    }

    @DisplayName("加入世界后外部的位置与速度写入都被拒绝")
    @Test
    void rejectsExternalMovementWrites(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity witch = ctx.get(() -> Actors.spawn(ctx, WITCH, 0, 0, 0, true));
        ctx.ticks(2);
        ctx.run(() -> {
            ctx.check(witch.maidspell$blocksExternalMovement()).as("加入世界后判定已武装").isTrue();
            Vec3 before = witch.position();
            Vec3 beforeDelta = witch.getDeltaMovement();
            witch.setPos(before.x + 3.0D, before.y, before.z);
            witch.moveTo(before.x + 3.0D, before.y, before.z, 0.0F, 0.0F);
            witch.teleportTo(before.x + 3.0D, before.y, before.z);
            witch.setDeltaMovement(1.0D, 1.0D, 1.0D);
            witch.addDeltaMovement(new Vec3(1.0D, 0.0D, 0.0D));
            witch.push(1.0D, 0.0D, 0.0D);
            ctx.check(witch.position().distanceToSqr(before)).as("外部位置写入后的位移平方").isLessThan(1.0E-6D);
            ctx.check(witch.getDeltaMovement().distanceToSqr(beforeDelta)).as("外部速度写入后的速度变化平方")
                    .isLessThan(1.0E-6D);
        });
    }

    @DisplayName("星之魔女不能骑乘载具，其他实体也不能骑她")
    @Test
    void rejectsRidingInBothDirections(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity witch = ctx.get(() -> Actors.spawn(ctx, WITCH, 0, 0, 0, true));
        ctx.ticks(2);
        ctx.run(() -> {
            Boat boat = Actors.spawn(ctx, "minecraft:boat", 3, 0, 3, false);
            Pig pig = Actors.spawn(ctx, "minecraft:pig", 4, 0, 3, true);
            ctx.check(witch.startRiding(boat, true)).as("星之魔女强制骑船").isFalse();
            ctx.check(pig.startRiding(witch, true)).as("猪强制骑星之魔女").isFalse();
            ctx.check(witch.isPassenger()).as("星之魔女是否成了乘客").isFalse();
            ctx.check(pig.isPassenger()).as("猪是否成了乘客").isFalse();
        });
    }

    @DisplayName("原版 /tp 只选中星之魔女时静默失败，管理员 tp 子命令能把她连同家位一起挪走")
    @Test
    void vanillaTpSilencedButAdminTpWorks(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity witch = ctx.get(() -> Actors.spawn(ctx, WITCH, 0, 0, 0, true));
        ctx.ticks(2);
        Vec3 target = ctx.get(() -> {
            Vec3 before = witch.position();
            AtomicInteger result = new AtomicInteger(-1);
            CommandSourceStack source = ctx.server().createCommandSourceStack()
                    .withPosition(before)
                    .withSuppressedOutput()
                    .withPermission(4)
                    .withCallback((success, value) -> result.set(value));
            ctx.server().getCommands().performPrefixedCommand(source, "tp " + witch.getStringUUID() + " ~ ~5 ~");
            ctx.check(result.get()).as("/tp 星之魔女的返回值").isEqualTo(0);
            ctx.check(witch.position().distanceToSqr(before)).as("/tp 后的位移平方").isLessThan(1.0E-6D);

            BlockPos home = ctx.rel(5, 1, 5);
            Vec3 center = Vec3.atBottomCenterOf(home);
            ctx.server().getCommands().performPrefixedCommand(source, String.format(Locale.ROOT,
                    "maidspell_winefox %s tp %.2f %.2f %.2f", witch.getStringUUID(), center.x, center.y, center.z));
            return center;
        });
        ctx.ticks(3);
        ctx.run(() -> ctx.check(witch.position().distanceToSqr(target)).as("管理员 tp 三 tick 后离新家位的距离平方")
                .isLessThan(1.0D));
    }
}
