package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.block.entity.YueLinglanBlockEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTestAccess;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTrades;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.StaranchorStunSpellInterrupt;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 上游 2c0a184 带来的星之魔女内容与修复：补给档的星海唱片、正式挑战战胜掉落 Vivaldi 唱片、
 * 掌心黑洞吟唱期间不受反制、星锚眩晕在吟唱期间同样无效、两只星之魔女互为目标不递归、
 * 月铃兰按结构集间距换算搜索圈数、落樱厨舍两个变体除铁魔法方块外一致。
 */
@DisplayName("星之魔女：星海唱片、掌心黑洞吟唱防护、月铃兰搜索圈数与落樱厨舍变体")
@Scene("tlms")
class StellarDiscGuardTest {
    private static final String STELLAR_DISC = NS + "music_disc_stellar_endshore";
    private static final String BATTLE_DISC = NS + "music_disc_concerto_no_10_b_minor";
    private static final String NEBULA_CORE = NS + "nebula_core";
    private static final Class<?> BOSS = MagicalWinefoxBossEntity.class;

    // ---- 交易 ----

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** 战胜前后补给档都有「10 绿宝石 → 星海唱片」，价格不打折、不限购，排在星荧花簇之后、经验修补附魔书之前。 */
    @DisplayName("补给档有 10 绿宝石换星海唱片的交易，价格不打折、不限购，位置固定")
    @Test
    void stellarDiscInSupplies(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            for (boolean unlocked : new boolean[]{false, true}) {
                String label = unlocked ? "战胜后 " : "战胜前 ";
                MerchantOffers offers = WinefoxTrades.build(unlocked);
                int index = -1;
                for (int i = 0; i < offers.size(); i++) {
                    if (id(offers.get(i).getResult()).equals(STELLAR_DISC)) {
                        ctx.check(index).as(label + "星海唱片只有一行").isEqualTo(-1);
                        index = i;
                    }
                }
                ctx.check(index >= 0).as(label + "有星海唱片").isTrue();
                if (index < 0) {
                    continue;
                }
                MerchantOffer offer = offers.get(index);
                ctx.check(offer.getBaseCostA().is(Items.EMERALD) ? offer.getBaseCostA().getCount() : -1).as(label + "价格（绿宝石）").isEqualTo(10);
                ctx.check(offer.getCostB().isEmpty()).as(label + "没有第二件花费").isTrue();
                ctx.check(offer.getResult().getCount()).as(label + "数量").isEqualTo(1);
                ctx.check(offer.getMaxUses()).as(label + "不限购").isEqualTo(Integer.MAX_VALUE);
                ctx.check(index > 0 ? id(offers.get(index - 1).getResult()) : "").as(label + "前一行")
                        .isEqualTo(NS + "star_glow_flower_cluster");
                ctx.check(index + 1 < offers.size() && offers.get(index + 1).getResult().is(Items.ENCHANTED_BOOK))
                        .as(label + "后一行是附魔书").isTrue();
            }
        });
    }

    // ---- 战败掉落 ----

    private static Map<String, Integer> dropsAround(Stage ctx, MagicalWinefoxBossEntity boss) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemEntity item : ctx.level().getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(2.5))) {
            counts.merge(id(item.getItem()), item.getItem().getCount(), Integer::sum);
            ctx.cleanup(item::discard);
        }
        return counts;
    }

    /** 正式挑战战胜掉星云核心与 Vivaldi 唱片各一；被判限制或自定义配置挑战都不掉。 */
    @DisplayName("正式挑战战胜掉星云核心与 Vivaldi 唱片，被判限制或自定义配置挑战都不掉")
    @Test
    void battleDiscOnlyForFormalWin(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityDataAccessor<Boolean> restricted = Reflect.field(null, BOSS, "RESTRICTED");
        String[] labels = {"正式挑战", "被判限制", "自定义配置"};
        List<MagicalWinefoxBossEntity> bosses = ctx.get(() -> {
            List<MagicalWinefoxBossEntity> spawned = new ArrayList<>();
            for (int i = 0; i < labels.length; i++) {
                MagicalWinefoxBossEntity boss = Actors.spawn(ctx, NS + "stellar_witch", i * 8 - 8, 0, 0, true);
                boss.getEntityData().set(restricted, i == 1);
                Reflect.set(boss, BOSS, "customChallenge", i == 2);
                spawned.add(boss);
            }
            return spawned;
        });
        ctx.await(20, "三只星之魔女入世", () -> bosses.stream().allMatch(boss -> boss.tickCount >= 2));
        ctx.run(() -> {
            for (int i = 0; i < labels.length; i++) {
                MagicalWinefoxBossEntity boss = bosses.get(i);
                Reflect.call(boss, BOSS, "dropDefeatRewards", new Class<?>[]{DamageSource.class}, (Object) null);
                Map<String, Integer> drops = dropsAround(ctx, boss);
                ctx.record(labels[i], drops);
                int expected = i == 0 ? 1 : 0;
                ctx.check(drops.getOrDefault(BATTLE_DISC, 0)).as(labels[i] + " 的 Vivaldi 唱片").isEqualTo(expected);
                ctx.check(drops.getOrDefault(NEBULA_CORE, 0)).as(labels[i] + " 的星云核心").isEqualTo(expected);
                ctx.check(drops.getOrDefault(STELLAR_DISC, 0)).as(labels[i] + " 不掉星海唱片").isEqualTo(0);
            }
        });
    }

    // ---- 掌心黑洞吟唱 ----

    /** 掌心黑洞吟唱测试里的星之魔女与尸壳。 */
    private record Casting(MagicalWinefoxBossEntity boss, Mob husk) {
    }

    /**
     * 与一只不动、打不死的尸壳开战，按 phaseTwo 设定阶段，再直接挂上掌心黑洞的施法状态（不走法术本体，不生成仪式实体）。
     * 星之魔女不开 AI，施法计时不会走完；测试结束时清掉施法状态。
     */
    private static Casting castingPalmBlackHole(Stage ctx, boolean phaseTwo) {
        MagicalWinefoxBossEntity boss = ctx.get(() -> Actors.spawn(ctx, NS + "stellar_witch", 4, 0, 0, true));
        Mob husk = ctx.get(() -> {
            Mob spawned = Actors.spawn(ctx, "minecraft:husk", 0, 0, 0, true);
            spawned.setInvulnerable(true);
            return spawned;
        });
        ctx.cleanup(() -> {
            boss.getMagicData().resetCastingState();
            Reflect.set(boss, AbstractSpellCastingMob.class, "castingSpell", null);
        });
        ctx.await(20, "星之魔女入世", () -> boss.tickCount >= 2);
        boolean engaged = ctx.get(() -> {
            if (!WinefoxTestAccess.beginMobCombat(boss, husk) || !boss.isBattleActive()) {
                return false;
            }
            EntityDataAccessor<Boolean> phaseTwoData = Reflect.field(null, BOSS, "PHASE_TWO");
            boss.getEntityData().set(phaseTwoData, phaseTwo);
            AbstractSpell palm = IronsSpellbooksCompatSpells.PALM_BLACK_HOLE.get();
            boss.getMagicData().initiateCast(palm, 1, 400, CastSource.MOB, "mainhand");
            // cancelCast 经 castComplete 收尾时只认这个字段，不填的话取消施法是空操作
            Reflect.set(boss, AbstractSpellCastingMob.class, "castingSpell", new SpellData(palm, 1));
            return true;
        });
        if (!engaged) {
            throw ctx.fail("星之魔女没有对尸壳开战");
        }
        ctx.run(() -> {
            ctx.check(boss.isCasting()).as("施法状态已挂上").isTrue();
            ctx.check(boss.isCastingPalmBlackHole()).as("算作掌心黑洞吟唱").isEqualTo(phaseTwo);
        });
        return new Casting(boss, husk);
    }

    /** 掌心黑洞吟唱（二阶段战斗中）：反制事件被取消，尸壳真正施放的反制法术也打不断吟唱。 */
    @DisplayName("掌心黑洞吟唱期间不受反制：反制事件被取消，反制法术也打不断吟唱")
    @Test
    void palmBlackHoleIgnoresCounterspell(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        counterspell(Stage.flat(game, plot, reporter), true);
    }

    /** 对照（一阶段挂同一个施法状态，不算掌心黑洞吟唱）：事件不取消，反制法术把施法取消掉。 */
    @DisplayName("非掌心黑洞的吟唱照常被反制：事件不取消，反制法术取消施法")
    @Test
    void otherCastsStillCountered(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        counterspell(Stage.flat(game, plot, reporter), false);
    }

    private static void counterspell(Stage ctx, boolean palmBlackHole) {
        Casting casting = castingPalmBlackHole(ctx, palmBlackHole);
        MagicalWinefoxBossEntity boss = casting.boss();
        Mob husk = casting.husk();
        ctx.run(() -> {
            boolean canceled = NeoForge.EVENT_BUS.post(new CounterSpellEvent(husk, boss)).isCanceled();
            ctx.check(canceled).as("反制事件被取消").isEqualTo(palmBlackHole);
            ctx.check(boss.isCasting()).as("只发事件不影响施法").isTrue();

            husk.lookAt(EntityAnchorArgument.Anchor.EYES, boss.getEyePosition());
            SpellRegistry.COUNTERSPELL_SPELL.get().onCast(ctx.level(), 1, husk, CastSource.MOB, MagicData.getPlayerMagicData(husk));
            ctx.check(boss.isCasting()).as("尸壳施放反制法术后仍在施法").isEqualTo(palmBlackHole);
        });
    }

    /** 星锚眩晕：掌心黑洞吟唱期间不打断、不眩晕。 */
    @DisplayName("掌心黑洞吟唱期间星锚眩晕无效：不打断吟唱、不进入眩晕")
    @Test
    void palmBlackHoleNotInterrupted(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        staranchorStun(Stage.flat(game, plot, reporter), true);
    }

    /** 对照（一阶段挂同一个施法状态）：照常打断。 */
    @DisplayName("非掌心黑洞的吟唱照常被星锚眩晕打断并进入眩晕")
    @Test
    void otherCastsInterrupted(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        staranchorStun(Stage.flat(game, plot, reporter), false);
    }

    private static void staranchorStun(Stage ctx, boolean palmBlackHole) {
        MagicalWinefoxBossEntity boss = castingPalmBlackHole(ctx, palmBlackHole).boss();
        ctx.run(() -> {
            StaranchorStunSpellInterrupt.interrupt(boss);
            ctx.check(boss.isCasting()).as("星锚眩晕后仍在施法").isEqualTo(palmBlackHole);
            boolean dizzy = Reflect.call(boss, BOSS, "isDizzy", new Class<?>[0]);
            ctx.check(dizzy).as("进入眩晕").isEqualTo(!palmBlackHole);
        });
    }

    // ---- 普通生物战斗的重入 ----

    /** 两只星之魔女互相设为目标：不抛 StackOverflowError，谁也不因对方开战。 */
    @DisplayName("两只星之魔女互为目标时不递归溢出，也不因对方开战")
    @Test
    void mutualWitchTargetsDoNotRecurse(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity first = ctx.get(() -> Actors.spawn(ctx, NS + "stellar_witch", 0, 0, 0, true));
        MagicalWinefoxBossEntity second = ctx.get(() -> Actors.spawn(ctx, NS + "stellar_witch", 6, 0, 0, true));
        ctx.await(20, "两只星之魔女入世", () -> first.tickCount >= 2 && second.tickCount >= 2);
        boolean overflowed = ctx.get(() -> {
            try {
                first.setTarget(second);
                second.setTarget(first);
                ctx.check(WinefoxTestAccess.beginMobCombat(first, second)).as("另一只星之魔女不能开普通战斗").isFalse();
                return false;
            } catch (StackOverflowError e) {
                return true;
            }
        });
        if (overflowed) {
            throw ctx.fail("互为目标时递归溢出");
        }
        ctx.run(() -> {
            ctx.check(first.isBattleActive()).as("第一只开战").isFalse();
            ctx.check(second.isBattleActive()).as("第二只开战").isFalse();
        });
    }

    /** 重入标志置位时，嵌套的 beginMobCombat 直接返回 false 且不开战；复位后同一只尸壳能正常开战。 */
    @DisplayName("重入标志置位时嵌套的普通战斗调用被拒绝，复位后能正常开战")
    @Test
    void reentryGuardRejectsNestedCall(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity boss = ctx.get(() -> Actors.spawn(ctx, NS + "stellar_witch", 4, 0, 0, true));
        Mob husk = ctx.get(() -> {
            Mob spawned = Actors.spawn(ctx, "minecraft:husk", 0, 0, 0, true);
            spawned.setInvulnerable(true);
            return spawned;
        });
        ctx.await(20, "星之魔女入世", () -> boss.tickCount >= 2);
        ctx.run(() -> {
            Reflect.set(boss, BOSS, "beginningMobCombat", true);
            ctx.check(WinefoxTestAccess.beginMobCombat(boss, husk)).as("标志置位时的嵌套调用").isFalse();
            ctx.check(boss.isBattleActive()).as("标志置位时没有开战").isFalse();
            Reflect.set(boss, BOSS, "beginningMobCombat", false);
            ctx.check(WinefoxTestAccess.beginMobCombat(boss, husk)).as("复位后的调用").isTrue();
            ctx.check(boss.isBattleActive()).as("复位后开战").isTrue();
            boolean flag = Reflect.field(boss, BOSS, "beginningMobCombat");
            ctx.check(flag).as("调用结束后标志复位").isFalse();
        });
    }

    // ---- 月铃兰 ----

    /** 搜索圈数 = 5120 格 ÷ (结构集 spacing × 16)：当前 elven_realm_set 是随机分布、spacing 32，换算为 10 圈。 */
    @DisplayName("月铃兰的搜索圈数按精灵秘境结构集间距换算，间距 32 时为 10 圈")
    @Test
    void searchRadiusFollowsStructureSetSpacing(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            ServerLevel level = ctx.level();
            StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                    .get(ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.parse(NS + "elven_realm_set")));
            ctx.check(set != null).as("有 elven_realm_set").isTrue();
            if (set == null) {
                return;
            }
            ctx.check(set.placement() instanceof RandomSpreadStructurePlacement).as("随机分布").isTrue();
            int spacing = set.placement() instanceof RandomSpreadStructurePlacement spread ? spread.spacing() : -1;
            ctx.record("spacing", spacing);
            int radius = Reflect.call(null, YueLinglanBlockEntity.class, "structureSearchRadius", new Class<?>[]{ServerLevel.class}, level);
            ctx.check(radius).as("搜索圈数").isEqualTo(Math.max(1, 5120 / (spacing * 16)));
            ctx.check(radius).as("spacing 32 时的搜索圈数").isEqualTo(10);
        });
    }

    // ---- 落樱厨舍两个变体 ----

    private record Cell(String state, String nbt) {
    }

    /** 模板里每个坐标的方块状态（含属性）与方块实体 NBT。 */
    private static Map<String, Cell> cells(CompoundTag template) {
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        Map<String, Cell> cells = new TreeMap<>();
        for (Tag tag : template.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) tag;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            String key = pos.getInt(0) + "," + pos.getInt(1) + "," + pos.getInt(2);
            CompoundTag state = palette.getCompound(block.getInt("state"));
            cells.put(key, new Cell(state.getString("Name") + state.getCompound("Properties"),
                    block.contains("nbt") ? block.getCompound("nbt").toString() : ""));
        }
        return cells;
    }

    private static List<String> entities(CompoundTag template) {
        List<String> entities = new ArrayList<>();
        for (Tag tag : template.getList("entities", Tag.TAG_COMPOUND)) {
            CompoundTag entity = (CompoundTag) tag;
            ListTag pos = entity.getList("blockPos", Tag.TAG_INT);
            entities.add(entity.getCompound("nbt").getString("id") + "@" + pos.getInt(0) + "," + pos.getInt(1) + "," + pos.getInt(2));
        }
        entities.sort(null);
        return entities;
    }

    /**
     * 无铁魔法变体同步了上游的陈设：两个模板尺寸相同，方块不同的坐标上，装铁魔法版一侧都是铁魔法方块、另一侧都不是；
     * 只有方块实体内容不同的坐标是那三个装着铁魔法物品的容器；实体（含盔甲架）完全一致。
     */
    @DisplayName("落樱厨舍两个变体除铁魔法方块与三个容器内容外完全一致")
    @Test
    void variantsDifferOnlyInIronsBlocks(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            var manager = ctx.level().getStructureManager();
            StructureTemplate iss = manager.get(ResourceLocation.parse(NS + "cherry_cabin")).orElse(null);
            StructureTemplate plain = manager.get(ResourceLocation.parse(NS + "cherry_cabin_without_iss")).orElse(null);
            if (iss == null || plain == null) {
                throw ctx.fail("缺少落樱厨舍模板");
            }
            ctx.check(plain.getSize()).as("尺寸").isEqualTo(iss.getSize());
            CompoundTag issTag = iss.save(new CompoundTag());
            CompoundTag plainTag = plain.save(new CompoundTag());
            Map<String, Cell> issCells = cells(issTag);
            Map<String, Cell> plainCells = cells(plainTag);
            Checks.sameSet(ctx, "坐标", issCells.keySet(), plainCells.keySet());

            List<String> stateDiffs = new ArrayList<>();
            List<String> unexpected = new ArrayList<>();
            Set<String> contentDiffs = new TreeSet<>();
            issCells.forEach((pos, a) -> {
                Cell b = plainCells.get(pos);
                if (b == null) {
                    return;
                }
                if (!a.state().equals(b.state())) {
                    stateDiffs.add(pos);
                    if (!a.state().startsWith("irons_spellbooks:") || b.state().startsWith("irons_spellbooks:")) {
                        unexpected.add(pos + " " + a.state() + " | " + b.state());
                    }
                } else if (!a.nbt().equals(b.nbt())) {
                    contentDiffs.add(pos);
                }
            });
            ctx.record("方块不同的坐标数", stateDiffs.size());
            ctx.check(unexpected).as("不是铁魔法方块与普通方块之间的差异").isEmpty();
            ctx.check(stateDiffs.size()).as("方块不同的坐标数").isEqualTo(14);
            Checks.sameSet(ctx, "只有容器内容不同的坐标", Set.of("5,10,10", "14,10,13", "11,11,5"), contentDiffs);
            ctx.check(entities(plainTag)).as("实体").isEqualTo(entities(issTag));
        });
    }
}
