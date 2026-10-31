package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxBossHealthController;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxBossSpellAction;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTestAccess;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Arrow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星之魔女的动态限伤：在真实战斗状态下按受击间隔连续走伤害写血路径，逐击记下实际掉血。
 * 持续高压时一个窗口内的有效 DPS 压在最终预算以内；轻度施压不减伤；静态上限以下的高频伤害同样会被压低，
 * 窗口过去后恢复原伤；关掉动态限伤时只剩单次上限。
 * 近战、弹射物、铁魔法法术、瞬间伤害药水、魔法、爆炸、火焰、本模组真伤和无来源的直接写血都走真实入口，
 * 持续高压下同样吃单次上限和最终预算；中毒与凋零的持续伤害受受击间隔约束。
 * 每击前清掉原版无敌帧（本模组多处法术也会清），并压住她自己的闪避、治疗类分支，只看本模组自己的限伤。
 * 每个测试掉的血都不到一半，不会触发转阶段。
 * 动态限伤开关是全局配置，整个类持有同一把读写锁，测试之间互斥。
 */
@DisplayName("星之魔女：动态限伤")
@Scene("tlms")
@ResourceLock(value = "winefox.adaptiveDamageConfig", mode = ResourceAccessMode.READ_WRITE)
class WinefoxAdaptiveDamageTest {
    private static final float OVERWHELMING = 600.0F;
    // 瞬间伤害按 6 << 等级 结算，7 级是 768，同样远超上限
    private static final int HARM_AMPLIFIER = 7;
    private static final int DOT_WATCH_TICKS = 60;
    private static final int SUPPRESSED = 100_000;

    /** 一种伤害：对星之魔女打一记 amount；queued 的伤害要到下一 tick 才落到生命上。 */
    private record Strike(String name, boolean queued, StrikeAction action) {
    }

    @FunctionalInterface
    private interface StrikeAction {
        void apply(MagicalWinefoxBossEntity boss, Mob attacker, float amount);
    }

    private static final Strike MELEE = new Strike("melee", false, (boss, attacker, amount) ->
            boss.hurt(boss.damageSources().mobAttack(attacker), amount));
    private static final Strike ARROW = new Strike("arrow", false, (boss, attacker, amount) -> {
        Arrow arrow = new Arrow(EntityType.ARROW, boss.level());
        arrow.setOwner(attacker);
        boss.hurt(boss.damageSources().arrow(arrow, attacker), amount);
    });
    private static final Strike SPELL = new Strike("spell", false, (boss, attacker, amount) -> DamageSources.applyDamage(boss, amount,
            SpellRegistry.getSpell(ResourceLocation.parse("irons_spellbooks:firebolt")).getDamageSource(attacker)));
    private static final Strike HARMING_POTION = new Strike("harmingPotion", false, (boss, attacker, amount) ->
            MobEffects.HARM.value().applyInstantenousEffect(attacker, attacker, boss, HARM_AMPLIFIER, 1.0D));
    private static final Strike MAGIC = new Strike("magic", false, (boss, attacker, amount) ->
            boss.hurt(boss.damageSources().magic(), amount));
    private static final Strike EXPLOSION = new Strike("explosion", false, (boss, attacker, amount) ->
            boss.hurt(boss.damageSources().explosion(attacker, attacker), amount));
    private static final Strike FIRE = new Strike("fire", false, (boss, attacker, amount) ->
            boss.hurt(boss.damageSources().inFire(), amount));
    private static final Strike TRUE_DAMAGE = new Strike("trueDamage", true, (boss, attacker, amount) ->
            TrueDamageUtil.dealTrueDamage(boss, amount, attacker));
    private static final Strike DIRECT_SET_HEALTH = new Strike("directSetHealth", false, (boss, attacker, amount) ->
            boss.setHealth(boss.maidspell$authoritativeHealth() - amount));

    /** 开战后的星之魔女、充当伤害来源的尸壳和她的战斗 AI。 */
    private record Combat(MagicalWinefoxBossEntity boss, Mob attacker, Goal goal) {
    }

    /** 一个窗口内每个受击间隔都打一记远超上限的伤害：首击吃满单次上限，之后被压低，整体有效 DPS 不超过最终预算。 */
    @DisplayName("持续高压下首击吃满单次上限，之后被压低，窗口内有效 DPS 不超过最终预算")
    @Test
    void sustainedPressureStaysWithinBudget(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity boss = inCombat(ctx, true).boss();
        int gap = ctx.get(() -> gap(boss));
        int count = ctx.get(() -> WinefoxTestAccess.adaptiveWindowTicks(boss)) / gap + 1;
        List<Float> losses = hits(ctx, boss, count, gap, OVERWHELMING);
        ctx.run(() -> {
            float cap = WinefoxBossHealthController.damageCap(boss);
            double target = WinefoxTestAccess.adaptiveTargetDps(boss);
            double budget = target * WinefoxTestAccess.adaptiveFinalBudgetMultiplier(boss);
            double dps = losses.stream().mapToDouble(Float::doubleValue).sum() * 20.0D / (count * gap);
            ctx.record("losses", losses.toString());
            ctx.record("effectiveDps", dps);
            ctx.check(losses.stream().filter(loss -> loss <= 0.0F).count()).as("没有生效的击中次数").isEqualTo(0L);
            ctx.check(losses.getFirst()).as("首击（窗口内没有历史）").isCloseTo(cap, 0.001F);
            ctx.check(losses.stream().filter(loss -> loss > cap + 0.001F).toList()).as("超过单次上限的击中").isEmpty();
            ctx.check(losses.getLast()).as("末击低于首击").isLessThan(losses.getFirst());
            ctx.check(dps).as("一个窗口内的有效 DPS（预算 " + budget + "）").isAtMost(budget);
            ctx.check(dps).as("一个窗口内的有效 DPS 低于只有单次上限时").isLessThan(cap * 20.0D / gap);
        });
    }

    /** 尝试 DPS 远低于启动倍数、单击也不到高伤阈值时，每一击都原样生效。 */
    @DisplayName("轻度施压（尝试 DPS 低、单击不到高伤阈值）时每一击原样生效，不减伤")
    @Test
    void lightPressureUnreduced(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity boss = inCombat(ctx, true).boss();
        int gap = ctx.get(() -> Math.max(20, gap(boss)));
        float amount = ctx.get(() -> (float) (WinefoxTestAccess.adaptiveTargetDps(boss) * gap / 20.0D * 0.4D));
        String problem = ctx.get(() -> preconditions(boss, amount));
        if (problem != null) {
            throw ctx.fail(problem);
        }
        List<Float> losses = hits(ctx, boss, 6, gap, amount);
        ctx.record("losses", losses.toString());
        for (int i = 0; i < losses.size(); i++) {
            ctx.check(losses.get(i)).as("第 " + (i + 1) + " 击").isCloseTo(amount, 0.001F);
        }
    }

    /** 单击不到上限和高伤阈值、但按受击间隔连打时也会被压低；停手一个窗口后，下一击恢复原伤。 */
    @DisplayName("低于单次上限的高频伤害也会被压低，停手一个窗口后恢复原伤")
    @Test
    void subCapPressureReducedThenRecovers(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity boss = inCombat(ctx, true).boss();
        int gap = ctx.get(() -> gap(boss));
        float amount = WinefoxTestAccess.highDamageThreshold(ctx.get(() -> Config.winefoxMaxHealth)) - 1.0F;
        String problem = ctx.get(() -> preconditions(boss, amount));
        if (problem != null) {
            throw ctx.fail(problem);
        }
        List<Float> burst = hits(ctx, boss, 10, gap, amount);
        ctx.record("burst", burst.toString());
        ctx.check(burst.getFirst()).as("连打首击").isCloseTo(amount, 0.001F);
        ctx.check(burst.getLast()).as("连打末击被压到一半以下").isLessThan(amount * 0.5F);
        int window = ctx.get(() -> WinefoxTestAccess.adaptiveWindowTicks(boss));
        int resumeAt = ctx.get(() -> boss.tickCount) + window + gap;
        ctx.await(window + gap + 20, "停手满一个窗口", () -> boss.tickCount >= resumeAt);
        float loss = ctx.get(() -> WinefoxTestAccess.hit(boss, amount));
        ctx.record("afterWindow", loss);
        ctx.check(loss).as("窗口过去后的一击").isCloseTo(amount, 0.001F);
    }

    /** 关掉动态限伤：连续远超上限的伤害每一击都正好是单次上限，没有动态减伤，也没有高伤保护。 */
    @DisplayName("关闭动态限伤后每击都正好是单次上限，无动态减伤也无高伤保护")
    @Test
    void disabledLeavesOnlyHitCap(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity boss = inCombat(ctx, false).boss();
        List<Float> losses = hits(ctx, boss, 5, ctx.get(() -> gap(boss)), OVERWHELMING);
        float cap = ctx.get(() -> WinefoxBossHealthController.damageCap(boss));
        ctx.record("losses", losses.toString());
        for (int i = 0; i < losses.size(); i++) {
            ctx.check(losses.get(i)).as("第 " + (i + 1) + " 击").isCloseTo(cap, 0.001F);
        }
    }

    @DisplayName("近战伤害持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void melee(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), MELEE);
    }

    @DisplayName("弹射物（箭）伤害持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void arrow(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), ARROW);
    }

    @DisplayName("铁魔法法术伤害持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void spell(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), SPELL);
    }

    @DisplayName("瞬间伤害药水持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void harmingPotion(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), HARMING_POTION);
    }

    @DisplayName("魔法伤害持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void magic(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), MAGIC);
    }

    @DisplayName("爆炸伤害持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void explosion(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), EXPLOSION);
    }

    @DisplayName("火焰伤害持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void fire(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), FIRE);
    }

    @DisplayName("本模组真伤持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void trueDamage(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), TRUE_DAMAGE);
    }

    @DisplayName("无来源的直接写血持续高压：首击吃满单次上限，整体有效 DPS 不超过最终预算")
    @Test
    void directSetHealth(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sourcePressure(Stage.flat(game, plot, reporter), DIRECT_SET_HEALTH);
    }

    /** 这种伤害按受击间隔连打一个窗口：每击都生效，首击正好是单次上限，之后不超过上限，整体有效 DPS 不超过最终预算。 */
    private static void sourcePressure(Stage ctx, Strike strike) {
        Combat combat = inCombat(ctx, true);
        MagicalWinefoxBossEntity boss = combat.boss();
        int gap = ctx.get(() -> gap(boss));
        int count = ctx.get(() -> WinefoxTestAccess.adaptiveWindowTicks(boss)) / gap + 1;
        List<Float> losses = strikes(ctx, combat, strike, count, gap, OVERWHELMING);
        ctx.run(() -> {
            float cap = WinefoxBossHealthController.damageCap(boss);
            double budget = WinefoxTestAccess.adaptiveTargetDps(boss) * WinefoxTestAccess.adaptiveFinalBudgetMultiplier(boss);
            double dps = losses.stream().mapToDouble(Float::doubleValue).sum() * 20.0D / (count * gap);
            ctx.record("losses", losses.toString());
            ctx.record("effectiveDps", dps);
            ctx.check(losses.stream().filter(loss -> loss <= 0.0F).count()).as("没有生效的击中次数").isEqualTo(0L);
            ctx.check(losses.getFirst()).as("首击").isCloseTo(cap, 0.001F);
            ctx.check(losses.stream().filter(loss -> loss > cap + 0.001F).toList()).as("超过单次上限的击中").isEmpty();
            ctx.check(dps).as("一个窗口内的有效 DPS（预算 " + budget + "）").isAtMost(budget);
        });
    }

    /** 每 tick 结算的中毒与凋零：有伤害落下，但两次掉血之间不短于受击间隔。 */
    @DisplayName("中毒与凋零的持续伤害有伤害落下，且两次掉血间隔不短于受击间隔")
    @Test
    void poisonAndWitherFollowHitInterval(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity boss = inCombat(ctx, true).boss();
        int gap = ctx.get(() -> gap(boss));
        ctx.run(() -> {
            boss.addEffect(new MobEffectInstance(MobEffects.POISON, DOT_WATCH_TICKS + 40, 31));
            boss.addEffect(new MobEffectInstance(MobEffects.WITHER, DOT_WATCH_TICKS + 40, 31));
        });
        List<Integer> ticks = new ArrayList<>();
        List<Float> losses = new ArrayList<>();
        float[] health = {ctx.get(boss::maidspell$authoritativeHealth)};
        ctx.watch(DOT_WATCH_TICKS, () -> {
            float now = boss.maidspell$authoritativeHealth();
            if (now < health[0]) {
                ticks.add(boss.tickCount);
                losses.add(health[0] - now);
            }
            health[0] = now;
        });
        ctx.record("hitTicks", ticks.toString());
        ctx.record("losses", losses.toString());
        ctx.check(ctx.get(() -> boss.hasEffect(MobEffects.POISON) && boss.hasEffect(MobEffects.WITHER))).as("中毒和凋零都在").isTrue();
        ctx.check(ticks).as("掉血次数").isNotEmpty();
        ctx.check(ticks.size()).as("掉血次数").isAtMost(DOT_WATCH_TICKS / (double) gap + 1);
        List<Integer> close = new ArrayList<>();
        for (int i = 1; i < ticks.size(); i++) {
            if (ticks.get(i) - ticks.get(i - 1) < gap) {
                close.add(ticks.get(i));
            }
        }
        ctx.check(close).as("离上次掉血不到受击间隔的 tick").isEmpty();
    }

    /** 轻度施压的伤害要低于单次上限和高伤阈值，否则测的就不是动态限伤本身；满足时返回 null（服务端线程）。 */
    private static String preconditions(MagicalWinefoxBossEntity boss, float amount) {
        float cap = WinefoxBossHealthController.damageCap(boss);
        float threshold = WinefoxTestAccess.highDamageThreshold(Config.winefoxMaxHealth);
        if (amount <= 0.0F || amount >= cap || amount >= threshold) {
            return "当前配置下每击 " + amount + " 不在 (0, min(单次上限 " + cap + ", 高伤阈值 " + threshold + ")) 内";
        }
        return null;
    }

    /** 压住近战前的各个分支，并让闪避与深渊庇佑一直留在冷却里：两者都会闪开攻击并把她瞬移出场地（服务端线程）。 */
    private static void suppressDefenses(Goal goal) {
        StellarContentTest.suppressSideBranches(goal);
        Map<WinefoxBossSpellAction, Integer> cooldowns = Reflect.field(goal, goal.getClass(), "spellCooldowns");
        cooldowns.put(WinefoxBossSpellAction.EVASION, SUPPRESSED);
        cooldowns.put(WinefoxBossSpellAction.ABYSSAL_SHROUD, SUPPRESSED);
    }

    private static int gap(MagicalWinefoxBossEntity boss) {
        return Math.max(1, boss.maidspell$hitIntervalTicks());
    }

    /**
     * 生成星之魔女、补满血并与一只不动、打不死的尸壳开战，尸壳同时充当伤害来源；动态限伤开关在测试结束时还原。
     * 投枪、虚空相位、治疗、逃跑传送、闪避、深渊庇佑与破法回响压住不放：闪避和庇佑会闪开攻击并把她瞬移出场地，治疗会抵消掉血。
     */
    private static Combat inCombat(Stage ctx, boolean adaptive) {
        ctx.run(() -> {
            boolean previous = Config.winefoxAdaptiveDamageEnabled;
            Config.winefoxAdaptiveDamageEnabled = adaptive;
            ctx.cleanup(() -> Config.winefoxAdaptiveDamageEnabled = previous);
        });
        MagicalWinefoxBossEntity boss = ctx.get(() -> Actors.spawn(ctx, NS + "stellar_witch", 0, 0, 0, false));
        Mob dummy = ctx.get(() -> {
            Mob husk = Actors.spawn(ctx, "minecraft:husk", 3, 0, 0, true);
            husk.setInvulnerable(true);
            return husk;
        });
        ctx.await(20, "星之魔女入世", () -> boss.tickCount >= 2);
        Goal goal = ctx.get(() -> {
            if (!WinefoxTestAccess.beginMobCombat(boss, dummy) || !boss.isBattleActive()) {
                return null;
            }
            Goal found = StellarContentTest.combatGoal(boss);
            suppressDefenses(found);
            WinefoxTestAccess.fillHealth(boss);
            return found;
        });
        if (goal == null) {
            throw ctx.fail("星之魔女没有对假人开战");
        }
        return new Combat(boss, dummy, goal);
    }

    /** 每隔 gap 个星之魔女 tick 打一记 amount，共 count 记，返回每记实际掉的血。 */
    private static List<Float> hits(Stage ctx, MagicalWinefoxBossEntity boss, int count, int gap, float amount) {
        List<Float> losses = new ArrayList<>();
        int[] last = {Integer.MIN_VALUE};
        Goal goal = ctx.get(() -> StellarContentTest.combatGoal(boss));
        ctx.await(count * gap + 20, "打满 " + count + " 记", () -> {
            suppressDefenses(goal);
            if (last[0] == Integer.MIN_VALUE || boss.tickCount - last[0] >= gap) {
                last[0] = boss.tickCount;
                losses.add(WinefoxTestAccess.hit(boss, amount));
            }
            return losses.size() >= count;
        });
        return new ArrayList<>(losses);
    }

    /**
     * 每隔 gap 个星之魔女 tick 用 strike 打一记，共 count 记，返回每记实际掉的血。
     * 每记之前清掉原版无敌帧；排队结算的伤害在下一 tick 读掉血。
     */
    private static List<Float> strikes(Stage ctx, Combat combat, Strike strike, int count, int gap, float amount) {
        MagicalWinefoxBossEntity boss = combat.boss();
        List<Float> losses = new ArrayList<>();
        int[] fired = {0};
        int[] last = {Integer.MIN_VALUE};
        float[] pending = {Float.NaN};
        ctx.await(count * gap + 20, "打满 " + count + " 记", () -> {
            suppressDefenses(combat.goal());
            // 法术伤害会点着她，燃烧在两记之间结算会占掉受击间隔
            boss.clearFire();
            if (!Float.isNaN(pending[0])) {
                losses.add(pending[0] - boss.maidspell$authoritativeHealth());
                pending[0] = Float.NaN;
            }
            if (fired[0] < count && (last[0] == Integer.MIN_VALUE || boss.tickCount - last[0] >= gap)) {
                last[0] = boss.tickCount;
                fired[0]++;
                boss.invulnerableTime = 0;
                float before = boss.maidspell$authoritativeHealth();
                strike.action().apply(boss, combat.attacker(), amount);
                if (strike.queued()) {
                    pending[0] = before;
                } else {
                    losses.add(before - boss.maidspell$authoritativeHealth());
                }
            }
            return losses.size() >= count;
        });
        return new ArrayList<>(losses);
    }
}
