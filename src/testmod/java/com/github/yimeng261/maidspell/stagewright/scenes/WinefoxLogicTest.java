package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.api.IBossDamageClamp;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxAction;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxBossSpellAction;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTestAccess;
import com.github.yimeng261.maidspell.effect.StaranchorStunEffect;
import com.github.yimeng261.maidspell.stagewright.support.Mismatches;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.stagewright.support.WitchPack;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import com.github.yimeng261.maidspell.winefox.WinefoxSpellChoice;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 星之魔女战斗里的纯计算部分：承伤先减伤后封顶、高伤保护只被够格的命中消耗、超时失效、最低有效伤害、剑系连段的步骤顺序、
 * 眩晕动画与星锚眩晕时长、法术默认权重、近战连段的出招节奏，以及挑战配置（含护甲）的存取与网络往返。
 */
@Scene("tlms")
@DisplayName("星之魔女战斗：纯计算逻辑")
class WinefoxLogicTest {
    private static final List<WinefoxAction> SWORD_ACTIONS = List.of(
            WinefoxAction.SWORD_ATTACK_1, WinefoxAction.SWORD_ATTACK_2,
            WinefoxAction.SWORD_ATTACK_3, WinefoxAction.SWORD_ATTACK_4);

    /** 先减伤再封顶：100 减到 50 再封顶 48，而不是先封顶到 48 再减成 24。 */
    @DisplayName("承伤先减伤再按单次上限封顶，而不是先封顶再减伤")
    @Test
    void reductionBeforeSingleHitCap(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> reductionBeforeCap(ctx));
    }

    /** 额外直接写入的生命变化也算进本次伤害。 */
    @DisplayName("额外直接写入的生命变化也算进本次伤害")
    @Test
    void extraHealthWriteCounted(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> extraHealthWrite(ctx));
    }

    /** 溢出伤害按原始压力计算，不截到剩余生命。 */
    @DisplayName("溢出伤害按原始压力计算，不截到剩余生命")
    @Test
    void overkillNotClippedToRemainingHealth(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> overkill(ctx));
    }

    /** 极端但有限的写入仍得到有限伤害。 */
    @DisplayName("极端但有限的生命写入仍得到有限的伤害")
    @Test
    void extremeWritesStayFinite(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> extremeWrites(ctx));
    }

    /** 治疗和 NaN、负无穷这类无效写入不算伤害。 */
    @DisplayName("治疗以及 NaN、负无穷这类无效写入不算伤害")
    @Test
    void healingAndInvalidWritesAreNotDamage(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> healingWrites(ctx));
    }

    /** 减伤和保护叠完之后只剩一点残血的写入，会被补足到最低有效伤害并清零残血。 */
    @DisplayName("减伤和保护叠完只剩一点残血的写入，补足到最低有效伤害并清零残血")
    @Test
    void minimumDamageClearsTinyResidual(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> minimumClearsResidual(ctx));
    }

    /** 最低有效伤害在高伤保护之后结算。 */
    @DisplayName("最低有效伤害在高伤保护之后结算")
    @Test
    void minimumDamageAppliesAfterGuard(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> minimumAfterGuard(ctx));
    }

    /** 最低有效伤害不超过单次封顶。 */
    @DisplayName("最低有效伤害不超过单次封顶")
    @Test
    void minimumDamageNeverExceedsHitCap(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> minimumWithinCap(ctx));
    }

    /** 被减到零的伤害写入仍然算一次命中，且不足最低伤害的残血直接归零。 */
    @DisplayName("被减到零的伤害写入仍算一次命中，不足最低伤害的残血直接归零")
    @Test
    void minimumDamageCountsZeroedHit(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> minimumCountsZeroedHit(ctx));
    }

    /** 治疗不会被当成伤害，致命一击保持为零。 */
    @DisplayName("最低有效伤害不把治疗当成伤害，致命一击保持为零")
    @Test
    void minimumDamageLeavesHealingAndLethalAlone(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> minimumLeavesHealingAndLethal(ctx));
    }

    /** 不限伤的写入使用默认的最低有效伤害。 */
    @DisplayName("不限伤的写入使用默认的最低有效伤害")
    @Test
    void minimumDamageUnlimitedCapUsesDefault(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> minimumUnlimitedCap(ctx));
    }

    /** 高伤一击之后保护仍在，不够格的小伤害不消耗它。 */
    @DisplayName("高伤一击之后保护仍在，不够格的小伤害不消耗它")
    @Test
    void smallHitKeepsGuard(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> guardSmallHit(ctx));
    }

    /** 连续高伤只刷新保护，不叠加。 */
    @DisplayName("连续高伤只刷新高伤保护，不叠加")
    @Test
    void refreshesWithoutStacking(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> guardNoStacking(ctx));
    }

    /** 阈值是最大生命的 5%，正好等于阈值也算高伤，之后的小伤害不会清掉保护。 */
    @DisplayName("高伤阈值为最大生命的 5%，正好等于阈值也算高伤")
    @Test
    void thresholdFivePercentInclusive(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> guardThreshold(ctx));
    }

    /** 保护在时限内有效，过了时限失效。 */
    @DisplayName("高伤保护在时限内有效，过了时限失效")
    @Test
    void expiresAfterLifetime(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> guardExpires(ctx));
    }

    /** 保护状态可以从存档恢复，也可以清掉。 */
    @DisplayName("高伤保护状态可以从存档恢复，也可以清掉")
    @Test
    void pendingRestoredAndReset(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> guardPending(ctx));
    }

    /** 剑阵之后依次是停顿、闪身、瞬移、斩击，各步等对应法术真正开始或放出。 */
    @DisplayName("剑系连段在剑阵后依次为停顿、闪身、瞬移、斩击，各步等对应法术开始或放出")
    @Test
    void stepsInOrder(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> comboOrder(ctx));
    }

    /** 无关法术的放出不能跳过停顿或闪身。 */
    @DisplayName("无关法术的放出不能让剑系连段跳过停顿或闪身")
    @Test
    void unrelatedReleasesCannotSkip(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> comboNoSkip(ctx));
    }

    /** 在任意一步打断都会清空连段，之后能重新开始。 */
    @DisplayName("在剑系连段任意一步打断都会清空连段，之后能重新开始")
    @Test
    void interruptionClearsEveryStep(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> comboInterrupt(ctx));
    }

    /** 眩晕动画长 6 秒，覆盖星锚珍珠的整段眩晕。 */
    @DisplayName("眩晕动画长 6 秒，覆盖星锚珍珠的整段眩晕")
    @Test
    void dizzyAnimationCoversPearlStun(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> dizzyCoversStun(ctx));
    }

    /** 默认权重：剑牢二阶段减半，黑洞二阶段加倍，其余法术两阶段都是 2。 */
    @DisplayName("法术默认权重：剑牢二阶段减半，黑洞二阶段加倍，其余法术两阶段都是 2")
    @Test
    void swordPrisonHalvedOnlyInPhaseTwo(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> spellWeights(ctx));
    }

    @DisplayName("三连击近战每一击的起手时刻与整套打完放开 AI 的时刻符合预期")
    @Test
    void threeHitComboTimeline(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> comboTimeline(ctx, 3, 0, 12, 24, 42));
    }

    @DisplayName("四连击近战每一击的起手时刻与整套打完放开 AI 的时刻符合预期")
    @Test
    void fourHitComboTimeline(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> comboTimeline(ctx, 4, 0, 12, 24, 36, 55));
    }

    /** 只有剑系动作允许提前接续，其余动作的接续时刻等于动作时长。 */
    @DisplayName("只有剑系动作允许提前接续，其余动作的接续时刻等于动作时长")
    @Test
    void onlySwordActionsFollowUpEarly(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> onlySwordsFollowUp(ctx));
    }

    /** 剑系接续不丢动作事件，单独出招时冷却覆盖完整动画。 */
    @DisplayName("剑系接续不丢动作事件，单独出招时冷却覆盖完整动画")
    @Test
    void followUpsKeepEventsAndFullRecovery(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> followUpsKeepEvents(ctx));
    }

    /** 护甲与其他设置一起经 NBT 和网络往返后不变。 */
    @DisplayName("挑战配置的护甲与其他设置经 NBT 和网络往返后不变")
    @Test
    void armorRoundTrips(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> armorRoundTrips(ctx));
    }

    /** 没有护甲字段的旧挑战与预设按默认护甲读入。 */
    @DisplayName("没有护甲字段的旧挑战与预设按默认护甲读入")
    @Test
    void legacyTagsUseDefaultArmor(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> legacyArmor(ctx));
    }

    /** 护甲下限是服务器默认值的一半，上限是原版属性上限 30。 */
    @DisplayName("挑战护甲下限是服务器默认值的一半，上限是原版属性上限 30")
    @Test
    void armorBounds(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> armorBounds(ctx));
    }

    /** 插入护甲一项后，其他基础设置的下限仍对应各自的设置项。 */
    @DisplayName("插入护甲一项后，其他基础设置的下限仍对应各自的设置项")
    @Test
    void basicLimitsKeepTheirSlots(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> basicLimits(ctx));
    }

    /** 预设经网络往返后保留护甲和空槽位。 */
    @DisplayName("挑战预设经网络往返后保留护甲和空槽位")
    @Test
    void presetsRoundTripWithEmptySlots(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> presetsRoundTrip(ctx));
    }

    // ---- 承伤换算 ----

    private static void reductionBeforeCap(Stage ctx) {
        float damage = WinefoxTestAccess.damageBeforeCap(300.0F, 200.0F);
        ctx.check(WinefoxTestAccess.reduceAndCapDamage(damage, 0.5D, 48.0F)).as("减伤后封顶").isEqualTo(48.0F);
    }

    private static void extraHealthWrite(Stage ctx) {
        float damage = WinefoxTestAccess.damageBeforeCap(250.0F, 190.0F);
        ctx.check(damage).as("本次伤害").isEqualTo(60.0F);
        ctx.check(WinefoxTestAccess.reduceAndCapDamage(damage, 0.5D, 48.0F)).as("减伤后").isEqualTo(30.0F);
    }

    private static void overkill(Stage ctx) {
        float damage = WinefoxTestAccess.damageBeforeCap(300.0F, 300.0F - 1_000_000_000.0F);
        ctx.check(damage).as("本次伤害").isCloseTo(1_000_000_000.0F, 128.0F);
        ctx.check(WinefoxTestAccess.reduceAndCapDamage(damage, 0.05D, 48.0F)).as("减伤后封顶").isEqualTo(48.0F);
    }

    private static void extremeWrites(Stage ctx) {
        float damage = WinefoxTestAccess.damageBeforeCap(Float.MAX_VALUE, -Float.MAX_VALUE);
        ctx.check(Float.isFinite(damage)).as("有限").isTrue();
        ctx.check(damage).as("本次伤害").isEqualTo(Float.MAX_VALUE);
    }

    private static void healingWrites(Stage ctx) {
        ctx.check(WinefoxTestAccess.damageBeforeCap(300.0F, 320.0F)).as("治疗").isEqualTo(0.0F);
        ctx.check(WinefoxTestAccess.damageBeforeCap(300.0F, Float.NaN)).as("NaN").isEqualTo(0.0F);
        ctx.check(WinefoxTestAccess.damageBeforeCap(300.0F, Float.NEGATIVE_INFINITY)).as("负无穷").isEqualTo(0.0F);
    }

    // ---- 最低有效伤害 ----

    private static void minimumClearsResidual(Stage ctx) {
        ctx.check(WinefoxTestAccess.enforceMinimumDamage(0.0140709765F, 0.0135784923F, 48.0F))
                .as("残血清零").isEqualTo(0.0F);
    }

    private static void minimumAfterGuard(Stage ctx) {
        ctx.check(WinefoxTestAccess.enforceMinimumDamage(1.0F, 0.9995F, 48.0F)).as("补足到 0.1").isCloseTo(0.9F, 1.0E-6);
    }

    private static void minimumWithinCap(Stage ctx) {
        ctx.check(WinefoxTestAccess.enforceMinimumDamage(0.2F, 0.199F, 0.05F)).as("按封顶补足").isCloseTo(0.15F, 1.0E-6);
    }

    private static void minimumCountsZeroedHit(Stage ctx) {
        ctx.check(WinefoxTestAccess.enforceMinimumDamage(0.2F, 0.2F, 48.0F)).as("零伤害补足").isCloseTo(0.1F, 1.0E-6);
        ctx.check(WinefoxTestAccess.enforceMinimumDamage(0.05F, 0.05F, 48.0F)).as("残血不足最低伤害").isEqualTo(0.0F);
    }

    private static void minimumLeavesHealingAndLethal(Stage ctx) {
        ctx.check(WinefoxTestAccess.enforceMinimumDamage(0.1F, 0.2F, 48.0F)).as("治疗").isEqualTo(0.2F);
        ctx.check(WinefoxTestAccess.enforceMinimumDamage(0.2F, 0.0F, 48.0F)).as("致命一击").isEqualTo(0.0F);
    }

    private static void minimumUnlimitedCap(Stage ctx) {
        ctx.check(WinefoxTestAccess.enforceMinimumDamage(1.0F, 0.999F, IBossDamageClamp.NO_DAMAGE_CAP))
                .as("默认最低伤害").isCloseTo(0.9F, 1.0E-6);
    }

    // ---- 高伤保护 ----

    private static void guardSmallHit(Stage ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        float threshold = WinefoxTestAccess.highDamageThreshold(600.0D);
        ctx.check(guard.multiplier(0L)).as("初始倍率").isEqualTo(1.0D);
        guard.recordEffectiveHit(100.0F, threshold, 0L);
        ctx.check(guard.multiplier(10L)).as("高伤后倍率").isEqualTo(0.7D);
        guard.recordEffectiveHit(3.0F, threshold, 20L);
        ctx.check(guard.pending()).as("小伤害后仍在保护").isTrue();
        ctx.check(48.0D * guard.multiplier(20L)).as("48 封顶伤害打折后").isCloseTo(33.6D, 0.0001D);
    }

    private static void guardNoStacking(Stage ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        Mismatches m = new Mismatches();
        for (int i = 0; i < 5; i++) {
            long now = i * 10L;
            guard.recordEffectiveHit(1_000_000_000.0F, WinefoxTestAccess.highDamageThreshold(600.0D), now);
            m.equal("第 " + (i + 1) + " 次高伤后倍率", 0.7D, guard.multiplier(now));
        }
        m.verify(ctx, "连续高伤");
    }

    private static void guardThreshold(Stage ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        float threshold = WinefoxTestAccess.highDamageThreshold(600.0D);
        ctx.check(threshold).as("600 生命的阈值").isEqualTo(30.0F);
        ctx.check(WinefoxTestAccess.highDamageThreshold(1_000.0D)).as("1000 生命的阈值").isEqualTo(50.0F);
        guard.recordEffectiveHit(29.0F, threshold, 0L);
        ctx.check(guard.pending()).as("低于阈值").isFalse();
        guard.recordEffectiveHit(30.0F, threshold, 0L);
        ctx.check(guard.pending()).as("等于阈值").isTrue();
        guard.recordEffectiveHit(20.0F, threshold, 0L);
        ctx.check(guard.pending()).as("之后的小伤害").isTrue();
    }

    private static void guardExpires(Stage ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        int lifetime = WinefoxTestAccess.HIGH_DAMAGE_GUARD_LIFETIME_TICKS;
        guard.recordEffectiveHit(100.0F, WinefoxTestAccess.highDamageThreshold(600.0D), 0L);
        ctx.check(guard.multiplier(lifetime)).as("时限当刻").isEqualTo(0.7D);
        ctx.check(guard.multiplier(lifetime + 1L)).as("时限后一刻").isEqualTo(1.0D);
        ctx.check(guard.pending()).as("失效后").isFalse();
    }

    private static void guardPending(Stage ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        guard.setPending(true, 0L);
        ctx.check(guard.multiplier(0L)).as("恢复保护后倍率").isEqualTo(0.7D);
        guard.setPending(false, 0L);
        ctx.check(guard.multiplier(0L)).as("清掉后倍率").isEqualTo(1.0D);
    }

    // ---- 剑系连段 ----

    private static void comboOrder(Stage ctx) {
        WinefoxTestAccess.SwordCombo combo = new WinefoxTestAccess.SwordCombo();
        combo.start(100);
        ctx.check(combo.step(119)).as("119").isEqualTo("PAUSE");
        ctx.check(combo.step(120)).as("120").isEqualTo("FLASH");
        combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        ctx.check(combo.step(121)).as("开始施放瞬移").isEqualTo("FLASH_CASTING");
        combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        ctx.check(combo.step(122)).as("瞬移放出").isEqualTo("TELEPORT");
        combo.teleported();
        ctx.check(combo.step(123)).as("瞬移完成").isEqualTo("SLASH");
        combo.castStarted(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        ctx.check(combo.step(124)).as("开始施放斩击").isEqualTo("SLASH_CASTING");
        combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        ctx.check(combo.isActive()).as("斩击放出后仍在连段").isFalse();
    }

    private static void comboNoSkip(Stage ctx) {
        WinefoxTestAccess.SwordCombo combo = new WinefoxTestAccess.SwordCombo();
        combo.start(0);
        combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        combo.teleported();
        ctx.check(combo.step(19)).as("19").isEqualTo("PAUSE");
        ctx.check(combo.step(20)).as("20").isEqualTo("FLASH");
        combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        ctx.check(combo.step(100)).as("放出无关法术后").isEqualTo("FLASH_CASTING");
    }

    private static void comboInterrupt(Stage ctx) {
        List<String> steps = Arrays.asList(WinefoxTestAccess.SwordCombo.stepNames());
        Mismatches m = new Mismatches();
        for (String interrupted : steps) {
            int at = steps.indexOf(interrupted);
            WinefoxTestAccess.SwordCombo combo = new WinefoxTestAccess.SwordCombo();
            combo.start(0);
            if (at >= steps.indexOf("FLASH")) {
                combo.step(20);
            }
            if (at >= steps.indexOf("FLASH_CASTING")) {
                combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            }
            if (at >= steps.indexOf("TELEPORT")) {
                combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            }
            if (at >= steps.indexOf("SLASH")) {
                combo.teleported();
            }
            if (interrupted.equals("SLASH_CASTING")) {
                combo.castStarted(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
            }
            combo.cancel();
            combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
            m.check(interrupted + " 处打断后仍在连段", !combo.isActive());
            combo.start(200);
            m.check(interrupted + " 处打断后无法重新开始", combo.isActive());
            m.equal(interrupted + " 处打断后 219", "PAUSE", combo.step(219));
            m.equal(interrupted + " 处打断后 220", "FLASH", combo.step(220));
        }
        m.verify(ctx, "打断连段");
    }

    // ---- 眩晕与法术权重 ----

    private static void dizzyCoversStun(Stage ctx) {
        JsonObject animations = WitchPack.animations("touhou_little_maid_spell.stellar_witch.main.animation.json");
        double seconds = animations.getAsJsonObject(WinefoxAction.DIZZY.animationName()).get("animation_length").getAsDouble();
        ctx.check(seconds).as("眩晕动画秒数").isEqualTo(6.0D);
        ctx.check((int) Math.ceil(seconds * 20)).as("动画 tick").isEqualTo(StaranchorStunEffect.STUN_TICKS);
        ctx.check(WinefoxAction.DIZZY.durationTicks()).as("眩晕动作时长").isEqualTo(StaranchorStunEffect.STUN_TICKS);
    }

    private static void spellWeights(Stage ctx) {
        ctx.check(WinefoxBossSpellAction.SWORD_PRISON.defaultSelectionWeight(false)).as("剑牢一阶段").isEqualTo(2);
        ctx.check(WinefoxBossSpellAction.SWORD_PRISON.defaultSelectionWeight(true)).as("剑牢二阶段").isEqualTo(1);
        ctx.check(WinefoxBossSpellAction.BLACK_HOLE.defaultSelectionWeight(false)).as("黑洞一阶段").isEqualTo(1);
        ctx.check(WinefoxBossSpellAction.BLACK_HOLE.defaultSelectionWeight(true)).as("黑洞二阶段").isEqualTo(2);
        Mismatches m = new Mismatches();
        for (WinefoxBossSpellAction action : WinefoxBossSpellAction.values()) {
            if (action != WinefoxBossSpellAction.SWORD_PRISON && action != WinefoxBossSpellAction.BLACK_HOLE) {
                m.equal(action.name() + " 一阶段", 2, action.defaultSelectionWeight(false));
                m.equal(action.name() + " 二阶段", 2, action.defaultSelectionWeight(true));
            }
        }
        m.verify(ctx, "其余法术的默认权重");
    }

    // ---- 近战节奏 ----

    /** 连段每一击的起手时刻，最后一项是整套打完、AI 被放开的时刻。 */
    private static void comboTimeline(Stage ctx, int hits, int... expected) {
        int[] ticks = new int[hits + 1];
        Mismatches m = new Mismatches();
        for (int i = 0; i < hits; i++) {
            WinefoxAction action = SWORD_ACTIONS.get(i);
            int cooldown = action.meleeCooldownTicks(i + 1 < hits);
            m.check(action.name() + " 冷却不短于接续窗口", cooldown >= action.followUpTicks());
            ticks[i + 1] = ticks[i] + cooldown;
        }
        m.equal("起手时刻互不相同", (long) hits, Arrays.stream(ticks).limit(hits).distinct().count());
        m.verify(ctx, hits + " 连击的冷却");
        ctx.check(Arrays.toString(ticks)).as(hits + " 连击时间线").isEqualTo(Arrays.toString(expected));
    }

    private static void onlySwordsFollowUp(Stage ctx) {
        Mismatches m = new Mismatches();
        for (WinefoxAction action : WinefoxAction.values()) {
            if (!SWORD_ACTIONS.contains(action)) {
                m.equal(action.name(), action.durationTicks(), action.followUpTicks());
            }
        }
        m.verify(ctx, "非剑系动作的接续时刻");
    }

    private static void followUpsKeepEvents(Stage ctx) {
        JsonObject animations = WitchPack.animations("touhou_little_maid_spell.stellar_witch.arm.animation.json");
        Mismatches m = new Mismatches();
        for (WinefoxAction action : SWORD_ACTIONS) {
            double length = animations.getAsJsonObject(action.animationName()).get("animation_length").getAsDouble();
            m.equal(action.name() + " 收招冷却", (int) Math.ceil(length * 20), action.meleeCooldownTicks(false));
            m.check(action.name() + " 接续窗口在动作中途", action.followUpTicks() > 0 && action.followUpTicks() < action.durationTicks());
            for (WinefoxAction.Event event : action.events()) {
                m.check(action.name() + " 的事件 " + event + " 在接续前触发", event.tick() < action.followUpTicks());
            }
        }
        m.verify(ctx, "剑系动作的接续与事件");
    }

    // ---- 挑战配置 ----

    private static WinefoxChallengeConfig config(double armor) {
        return new WinefoxChallengeConfig(900.0D, armor, 1.4D, 2.3D, 0.12D, 9,
                0.6D, 7.0D, 0.9D, true, false, 40,
                List.of(new WinefoxSpellChoice("irons_spellbooks:magic_missile", 7)),
                List.of(new WinefoxSpellChoice("irons_spellbooks:heal", 4)), true);
    }

    private static <T> T roundTrip(Consumer<FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read, Stage ctx) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write.accept(buffer);
            T result = read.apply(buffer);
            ctx.check(buffer.readableBytes()).as("读完后剩余字节").isEqualTo(0);
            return result;
        } finally {
            buffer.release();
        }
    }

    private static void armorRoundTrips(Stage ctx) {
        WinefoxChallengeConfig config = config(18.0D);
        ctx.check(WinefoxChallengeConfig.fromTag(config.toTag())).as("NBT 往返").isEqualTo(config);
        ctx.check(roundTrip(config::writeToBuffer, WinefoxChallengeConfig::fromBuffer, ctx)).as("网络往返").isEqualTo(config);
    }

    private static void legacyArmor(Stage ctx) {
        CompoundTag legacy = config(18.0D).toTag();
        legacy.remove("Armor");
        ctx.check(WinefoxChallengeConfig.defaults().armor()).as("默认护甲").isEqualTo(12.0D);
        ctx.check(WinefoxChallengeConfig.fromTag(legacy)).as("旧数据").isEqualTo(config(12.0D));
    }

    private static void armorBounds(Stage ctx) {
        WinefoxChallengeConfig defaults = config(12.0D);
        ctx.check(WinefoxChallengeConfig.minimumBasicValue(1, defaults)).as("护甲下限").isEqualTo(6.0D);
        ctx.check(config(-1.0D).sanitized(defaults).armor()).as("负数").isEqualTo(6.0D);
        ctx.check(config(Double.NaN).sanitized(defaults).armor()).as("NaN").isEqualTo(6.0D);
        ctx.check(config(100.0D).sanitized(defaults).armor()).as("超上限").isEqualTo(30.0D);
        ctx.check(config(0.0D).sanitized(config(18.0D)).armor()).as("默认 18 时的下限").isEqualTo(9.0D);
    }

    private static void basicLimits(Stage ctx) {
        WinefoxChallengeConfig defaults = config(12.0D);
        double[] expected = {450.0D, 6.0D, 0.7D, 1.15D, 0.06D, 5.0D, 0.3D, 3.5D, 0.45D};
        Mismatches m = new Mismatches();
        for (int i = 0; i < expected.length; i++) {
            m.equal("第 " + i + " 项下限", expected[i], WinefoxChallengeConfig.minimumBasicValue(i, defaults));
        }
        m.verify(ctx, "基础设置的下限");
    }

    private static void presetsRoundTrip(Stage ctx) {
        List<WinefoxChallengeConfig> presets = WinefoxChallengePresets.empty();
        presets.set(0, config(18.0D));
        presets.set(2, config(24.0D));
        List<WinefoxChallengeConfig> restored = roundTrip(buffer -> WinefoxChallengePresets.writeBuffer(buffer, presets),
                WinefoxChallengePresets::readBuffer, ctx);
        ctx.check(restored).as("往返后的预设").isEqualTo(presets);
        ctx.check(restored.get(1)).as("空槽位").isNull();
    }
}
