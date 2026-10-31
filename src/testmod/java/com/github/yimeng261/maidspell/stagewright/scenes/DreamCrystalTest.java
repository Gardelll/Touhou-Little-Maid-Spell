package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.github.yimeng261.maidspell.stagewright.scenes.DreamCrystalFixtures.CRYSTAL;
import static com.github.yimeng261.maidspell.stagewright.scenes.DreamCrystalFixtures.now;
import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 梦云水晶（女仆佩戴）：命中不再定身、旧存档遗留的定身生物重进世界时释放、20 格范围强化、概率复活
 * （120 秒窗口、每次 -10%、最多 10 条记录、15 秒无敌、旧记录迁移）、Boss 中立、受击间隔内被挡下的一击不追加真伤；
 * 玩家不能通过 Curios 佩戴。
 * <p>计时都以主世界时间为准，在其他维度也一样。
 */
@Scene("tlms")
@DisplayName("梦云水晶（女仆佩戴）")
class DreamCrystalTest {
    private static final String FROZEN_TAG = NS + "dream_crystal_frozen_until";
    private static final ResourceLocation NEARBY_BOOST = ResourceLocation.parse(NS + "dream_crystal_nearby_boost");
    private static final int FREEZE_TICKS = 20;
    private static final int REVIVE_WINDOW = 2400;
    /** 概率统计的试验次数。 */
    private static final int TRIALS = 200;

    // ---- 命中不再定身 ----

    /** 女仆打到敌人后，敌人不会被定住，也没有时停标记。 */
    @DisplayName("女仆打到敌人后敌人不会被定住，也没有时停标记")
    @Test
    void hitDoesNotFreeze(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid spawned = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(spawned, CRYSTAL);
            return spawned;
        });
        Mob zombie = ctx.get(() -> {
            Mob spawned = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, false);
            spawned.setPersistenceRequired();
            return spawned;
        });
        ctx.ticks(2);
        ctx.run(() -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 2);
            ctx.check(zombie.isNoAi()).as("命中后敌人被定住").isFalse();
            ctx.check(zombie.getPersistentData().contains(FROZEN_TAG)).as("命中后有时停标记").isFalse();
        });
        ctx.ticks(FREEZE_TICKS + 10);
        ctx.run(() -> ctx.check(zombie.isNoAi()).as("命中约 1 秒后敌人被定住").isFalse());
    }

    /** 本来就是 NoAI 的生物被打后仍保持 NoAI，也不打标记。 */
    @DisplayName("本来就是 NoAI 的生物被打后仍保持 NoAI，也不打时停标记")
    @Test
    void hitKeepsNoAiMobs(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid spawned = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(spawned, CRYSTAL);
            return spawned;
        });
        Mob statue = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true));
        ctx.ticks(2);
        ctx.run(() -> {
            statue.hurt(maid.damageSources().mobAttack(maid), 2);
            ctx.check(statue.getPersistentData().contains(FROZEN_TAG)).as("原本 NoAI 的生物被打上时停标记").isFalse();
            ctx.check(statue.isNoAi()).as("原本 NoAI 的生物被恢复 AI").isTrue();
        });
    }

    @DisplayName("旧存档遗留的定身生物重进主世界后恢复 AI 并清掉时停标记")
    @Test
    void legacyFrozenReleasedOnJoin(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        legacyFrozenReleased(Stage.flat(game, plot, reporter));
    }

    @DisplayName("旧存档遗留的定身生物在下界重进世界后同样恢复 AI 并清掉时停标记")
    @Test
    void legacyFrozenReleasedInOtherDimension(Game game, @Plot.In("minecraft:the_nether") Plot plot, TestReporter reporter) {
        legacyFrozenReleased(Stage.sky(game, plot, reporter));
    }

    /** 旧存档里带时停标记的 NoAI 生物重新进入世界：恢复 AI 并清掉标记。 */
    private static void legacyFrozenReleased(Stage ctx) {
        Mob zombie = ctx.get(() -> {
            Mob spawned = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, false);
            spawned.setPersistenceRequired();
            spawned.setNoAi(true);
            spawned.getPersistentData().putLong(FROZEN_TAG, now(ctx) + FREEZE_TICKS);
            return spawned;
        });
        Entity loaded = Maids.reload(ctx, zombie, 2);
        ctx.ticks(2);
        ctx.run(() -> {
            Mob mob = (Mob) loaded;
            ctx.check(mob.isNoAi()).as("重新进入世界后仍被定住").isFalse();
            ctx.check(mob.getPersistentData().contains(FROZEN_TAG)).as("重新进入世界后仍有时停标记").isFalse();
        });
    }

    /** 没有时停标记的 NoAI 生物重新进入世界后保持 NoAI。 */
    @DisplayName("没有时停标记的 NoAI 生物重进世界后保持 NoAI")
    @Test
    void legacyNoAiWithoutTagKept(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Mob statue = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true));
        Entity loaded = Maids.reload(ctx, statue, 2);
        ctx.ticks(2);
        ctx.run(() -> ctx.check(((Mob) loaded).isNoAi()).as("原本 NoAI 的生物重新进入世界后被恢复 AI").isTrue());
    }

    // ---- 范围强化 ----

    /** 20 格内的其他女仆获得攻击加成，20 格外的没有；佩戴者走远后约 2 秒内加成消失。 */
    @DisplayName("20 格内其他女仆获得攻击加成，20 格外没有，佩戴者走远后加成消失")
    @Test
    void rangeBoost(Game game, @Plot.Size(x = 128, y = 32, z = 128) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        plot.fill(-64, 0, -64, 63, 0, 63, Blocks.GRASS_BLOCK);
        EntityMaid wearer = ctx.get(() -> {
            EntityMaid spawned = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(spawned, CRYSTAL);
            return spawned;
        });
        EntityMaid near = ctx.get(() -> Actors.sittingMaid(ctx, 10, 0, 0));
        EntityMaid far = ctx.get(() -> Actors.sittingMaid(ctx, 0, 0, 24));
        ctx.await(40, "近处的女仆获得攻击加成", () -> boosted(near));
        ctx.run(() -> {
            ctx.check(boosted(far)).as("24 格外的女仆有攻击加成").isFalse();
            ctx.check(boosted(wearer)).as("佩戴者自己有范围攻击加成").isFalse();
            wearer.teleportTo(wearer.getX() - 60, wearer.getY(), wearer.getZ());
        });
        ctx.await(80, "佩戴者走远后加成消失", () -> !boosted(near));
        ctx.record("note", "佩戴者走远后加成已消失");
    }

    private static boolean boosted(EntityMaid maid) {
        var attribute = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        return attribute != null && attribute.getModifier(NEARBY_BOOST) != null;
    }

    // ---- 复活 ----

    @DisplayName("第一次致死时复活并回满生命，获得 15 秒无敌且记下一条复活时间")
    @Test
    void firstDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        DreamCrystalFixtures.reviveFirstDeath(Stage.flat(game, plot, reporter));
    }

    /** 无敌到期后解除（按主世界时间），之后照常受伤。 */
    @DisplayName("复活无敌到期后按主世界时间解除，之后照常受伤")
    @Test
    void invulnerabilityExpires(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> Actors.sittingMaid(ctx, 0, 0, 0));
        ItemStack crystal = ctx.get(() -> Actors.equipBauble(maid, CRYSTAL));
        ctx.run(() -> {
            crystal.set(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL.get(), now(ctx) + 10);
            ctx.check(DreamCatCrystalBauble.isInvulnerable(maid)).as("截止时间前无敌").isTrue();
        });
        ctx.ticks(20);
        ctx.run(() -> {
            ctx.check(DreamCatCrystalBauble.isInvulnerable(maid)).as("截止时间后无敌").isFalse();
            ctx.check(crystal.has(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL.get())).as("到期后仍留着截止时间").isFalse();
            Actors.setMaidHealth(maid, maid.getMaxHealth());
            maid.invulnerableTime = 0;
            maid.hurt(maid.damageSources().mobAttack(Actors.spawn(ctx, "minecraft:zombie", 0, 0, 3, true)), 4);
            ctx.check(maid.getHealth()).as("无敌到期后受伤").isLessThan(maid.getMaxHealth());
        });
    }

    /** 120 秒内每复活一次，概率降 10%：0 条记录必定复活，5 条约一半，窗口外的旧记录不计。 */
    @DisplayName("120 秒内每复活一次概率降 10%：无记录必复活，5 条约一半，窗口外旧记录不计")
    @Test
    void chanceByRecentCount(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
            long now = now(ctx);
            List<Long> five = Collections.nCopies(5, now - 10);
            List<Long> stale = Collections.nCopies(10, now - REVIVE_WINDOW - 1);
            int none = trials(maid, List.of(), 1);
            int half = trials(maid, five, TRIALS);
            int old = trials(maid, stale, 1);
            ctx.record("revivedWithFive", half);
            ctx.check(none).as("没有记录时复活").isEqualTo(1);
            ctx.check(old).as("记录都在 120 秒外时复活").isEqualTo(1);
            ctx.check(half).as("5 条近期记录时 " + TRIALS + " 次里复活的次数（期望约一半）").isBetween(TRIALS * 35 / 100, TRIALS * 65 / 100);
        });
    }

    /** 用给定的复活记录试 times 次，每次用新的水晶，返回复活次数。 */
    private static int trials(EntityMaid maid, List<Long> history, int times) {
        int revived = 0;
        for (int i = 0; i < times; i++) {
            ItemStack stack = Actors.stack(CRYSTAL);
            stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION.get(), 1);
            if (!history.isEmpty()) {
                stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), history);
            }
            if (DreamCatCrystalBauble.tryRevive(maid, stack)) {
                revived++;
            }
        }
        return revived;
    }

    /** 120 秒内已复活 10 次：不再复活，女仆真正死亡。 */
    @DisplayName("120 秒内已复活 10 次后不再复活，女仆真正死亡")
    @Test
    void tenRecentNeverRevives(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> Actors.sittingMaid(ctx, 0, 0, 0));
        ItemStack crystal = ctx.get(() -> Actors.equipBauble(maid, CRYSTAL));
        ctx.ticks(2);
        ctx.run(() -> {
            crystal.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION.get(), 1);
            crystal.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), Collections.nCopies(10, now(ctx) - 5));
            Maids.kill(maid);
            ctx.check(maid.isAlive()).as("近期已复活 10 次后致死，女仆存活").isFalse();
        });
    }

    @DisplayName("旧版本留下的复活记录升级后第一次复活按近期没有复活过，之后按主世界时间记录")
    @Test
    void legacyHistoryCountsAsNone(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> legacyHistory(ctx));
    }

    @DisplayName("旧版本复活记录在下界升级后同样按近期没有复活过，之后按主世界时间记录")
    @Test
    void legacyHistoryInOtherDimension(Game game, @Plot.In("minecraft:the_nether") Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> legacyHistory(ctx));
    }

    /**
     * 旧版本（按各维度时钟、没有时钟版本号）留下的复活记录：升级后第一次复活按近期没有复活过（100%），
     * 之后按主世界时间记录。
     */
    private static void legacyHistory(Stage ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        long now = now(ctx);
        ItemStack stack = Actors.stack(CRYSTAL);
        stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), Collections.nCopies(10, now - 5));
        ctx.check(DreamCatCrystalBauble.tryRevive(maid, stack)).as("旧记录 10 条时复活").isTrue();
        ctx.check(stack.get(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION.get())).as("迁移后的时钟版本").isEqualTo(1);
        ctx.check(stack.get(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get())).as("迁移后的复活记录").isEqualTo(List.of(now));
    }

    /** 在主世界复活几次后到下界：记录按主世界时间延续，不清零也不卡满。 */
    @DisplayName("主世界复活几次后到下界，复活记录按主世界时间延续，不清零也不卡满")
    @Test
    void historyCarriesAcrossDimensions(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
            long now = now(ctx);
            ItemStack stack = Actors.stack(CRYSTAL);
            stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION.get(), 1);
            stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), List.of(now - 30, now - 20));
            ServerLevel nether = ctx.server().getLevel(Level.NETHER);
            EntityMaid inNether = (EntityMaid) Maids.copyToDimension(ctx, maid, nether, new Vec3(0.5, 64, 0.5));
            ctx.check(inNether).as("传送到下界的女仆").isNotNull();
            if (inNether == null) {
                return;
            }
            ctx.cleanup(() -> Actors.cleanupEntity(inNether));
            inNether.setNoAi(true);
            // 两条记录时概率 80%，多试几次直到成功一次
            boolean revived = false;
            for (int i = 0; i < 20 && !revived; i++) {
                revived = DreamCatCrystalBauble.tryRevive(inNether, stack);
            }
            ctx.check(revived).as("下界里复活").isTrue();
            List<Long> history = stack.getOrDefault(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), List.of());
            ctx.check(history.size()).as("下界复活后的记录条数").isEqualTo(3);
            ctx.check(history.getLast()).as("下界复活记下的时间（主世界时间）").isEqualTo(now(ctx));
        });
    }

    /** 旧存档里正处于复活无敌（按剩余 tick 记）的水晶：升级后换成截止时间，剩余时间后结束。 */
    @DisplayName("旧存档里按剩余 tick 记的复活无敌升级后换成截止时间，到点结束")
    @Test
    void legacyInvulnerableTicksMigrate(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> Actors.sittingMaid(ctx, 0, 0, 0));
        ItemStack worn = ctx.get(() -> {
            ItemStack stack = Actors.stack(CRYSTAL);
            stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS.get(), 100);
            maid.getMaidBauble().setStackInSlot(0, stack);
            return maid.getMaidBauble().getStackInSlot(0);
        });
        // 迁移发生在女仆的第一次饰品 tick，新场地里实体开始 tick 的时间不固定，所以等迁移完成后再量截止时间
        ctx.await(120, "女仆 tick 后无敌改记截止时间", () -> !worn.has(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS.get()));
        ctx.run(() -> {
            long done = now(ctx);
            ctx.check(worn.has(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS.get())).as("迁移后仍有旧的剩余 tick").isFalse();
            Long until = worn.get(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL.get());
            ctx.check(until).as("迁移后的无敌截止时间").isNotNull();
            if (until != null) {
                ctx.check(until - done).as("截止时间距迁移完成时的时间").isBetween(95L, 105L);
            }
            ctx.check(DreamCatCrystalBauble.isInvulnerable(maid)).as("迁移后仍无敌").isTrue();
        });
    }

    // ---- Boss 中立 ----

    /** 普通怪物不以佩戴者为目标；Boss 只在女仆打过它之后才会以她为目标。 */
    @DisplayName("普通怪物不以佩戴者为目标，Boss 只在女仆打过它之后才以她为目标")
    @Test
    void bossNeutral(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
            Actors.equipBauble(maid, CRYSTAL);
            Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 3, 0, 0, true);
            Mob wither = Actors.spawn(ctx, "minecraft:wither", -6, 0, 0, true);
            ctx.check(Owners.aim(zombie, maid)).as("僵尸以佩戴者为目标").isNull();
            ctx.check(Owners.aim(wither, maid)).as("女仆没打过时凋灵以她为目标").isNull();
            wither.invulnerableTime = 0;
            wither.hurt(maid.damageSources().mobAttack(maid), 1);
            ctx.check(Owners.aim(wither, maid)).as("女仆打过后凋灵以她为目标").isEqualTo(maid);
            ctx.check(Owners.aim(zombie, maid)).as("女仆打过凋灵后僵尸以她为目标").isNull();
        });
    }

    // ---- 受击间隔 ----

    /** 同样伤害连打两下、第二下在受击间隔内被挡：只结算第一下和它的真伤（100 血的猪剩 96）。 */
    @DisplayName("受击间隔内被挡下的第二击不追加真伤，只结算第一击及其真伤")
    @Test
    void blockedHitAddsNoTrueDamage(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Mob pig = ctx.get(() -> {
            EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
            Actors.equipBauble(maid, CRYSTAL);
            Mob spawned = Actors.spawn(ctx, "minecraft:pig", 0, 0, 2, true);
            spawned.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            spawned.setHealth(100);
            spawned.invulnerableTime = 0;
            spawned.hurt(maid.damageSources().mobAttack(maid), 2);
            spawned.hurt(maid.damageSources().mobAttack(maid), 2);
            return spawned;
        });
        ctx.ticks(2);
        ctx.run(() -> ctx.check(pig.getHealth()).as("连打两下后猪的生命").isCloseTo(96, 1e-3));
    }

    /** 本模组不再给玩家加 curio 栏，梦云水晶和原先一起进 curios:curio 标签的饰品在玩家身上没有可放的栏位。 */
    @DisplayName("玩家没有 curio 栏，无法通过 Curios 佩戴梦云水晶等本模组饰品")
    @Test
    void playersCannotWear(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer player = Owners.visitor(ctx, "TlmsCurioPlayer", 2, 0, 0);
            ctx.check(CuriosApi.getEntitySlots(player).containsKey("curio")).as("玩家有 curio 栏").isFalse();
            List<String> wearable = new ArrayList<>();
            for (String id : List.of("dream_cat_crystal", "sliver_cercis", "chaos_book", "double_heart_chain", "fragrant_ingenuity")) {
                Map<String, ISlotType> slots = CuriosApi.getItemStackSlots(Actors.stack(NS + id), player);
                if (!slots.isEmpty()) {
                    wearable.add(id + " → " + slots.keySet());
                }
            }
            ctx.check(wearable).as("玩家能佩戴的本模组饰品").isEmpty();
        });
    }
}
