package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.LootReach;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 只装车万女仆（其余联动都不装）时的资源与内容：联动内容不注册，隐世之境箱子改开替代物品，女仆照常生成与驯服。
 * <p>这个类在缺模组的拓扑里加载，只能引用原版、NeoForge、车万女仆与本模组的通用类。
 */
@DisplayName("只装车万女仆时的资源与内容")
@Scene("tlms.minimal")
class MissingModMinimalTest {
    /** 挂在铁魔法条件下的结构；不装铁魔法时结构与结构集都不应注册。 */
    private static final List<String> IRONS_STRUCTURES = List.of("elven_realm", "enchantress_footsteps_igloo",
            "enchantress_footsteps_mushroom_fields", "enchantress_footsteps_outpost", "fallen_sanctum", "relic_sanctum",
            "starwatch_tower", "stellar_endshore", "woods_perch");
    private static final List<String> IRONS_STRUCTURE_SETS = List.of("elven_realm_set", "enchantress_footsteps_igloo",
            "enchantress_footsteps_mushroom_fields", "enchantress_footsteps_outpost", "fallen_sanctum_set",
            "relic_sanctum_set", "starwatch_tower_set", "stellar_endshore_set", "woods_perch");
    private static final List<String> GOETY_STRUCTURES = List.of("enchantress_footsteps_graveyard",
            "enchantress_footsteps_oasis", "yin_yang_altar");
    private static final List<String> GOETY_STRUCTURE_SETS = List.of("enchantress_footsteps_graveyard",
            "enchantress_footsteps_oasis", "yin_yang_altar_set");
    /** 不依赖可选模组、只装车万女仆也应在的结构。 */
    private static final List<String> BASE_STRUCTURES = List.of("hidden_retreat", "hidden_cherry_tree", "starfall_garden",
            "cherry_cabin", "enchantress_footsteps_basalt_forge", "enchantress_footsteps_crimson_cabin",
            "enchantress_footsteps_soulvalley_nook", "enchantress_footsteps_warped_perch");

    private static final List<String> IRONS_ITEMS = List.of("star_shadow_longsword", "star_shadow_staff",
            "star_witch_hat", "star_shadow_spear", "corrupted_knight_spawn_egg", "shadow_assassin_spawn_egg",
            "elf_templar_spawn_egg", "astro_mancer_spawn_egg", "holy_construct_spawn_egg", "stellar_witch_spawn_egg");
    private static final List<String> IRONS_ENTITIES = List.of("corrupted_knight", "shadow_assassin", "elf_templar",
            "astro_mancer", "holy_construct", "stellar_witch", "star_shadow_spear");
    private static final List<String> IRONS_RECIPES = List.of("altar/arc_cross", "altar/dream_cat_crystal_accessories",
            "altar/dream_cat_crystal_crossover", "returning_star", "starglint_dagger");
    private static final List<String> IRONS_LOOT_TABLES = List.of("chests/elven_realm_1",
            "chests/enchantress_footsteps_igloo", "chests/holy_relic_chapel_1", "chests/holy_relic_chapel_2",
            "chests/starwatch_tower_1", "entities/corrupted_knight", "entities/elf_templar", "entities/holy_construct",
            "entities/shadow_assassin", "entities/astro_mancer", "entities/stellar_witch");

    /** 隐世之境三类箱子在不装铁魔法时改开的物品（表里的条目，附魔之书由普通书经随机附魔得到）。 */
    private static final Map<String, Set<String>> RETREAT_FALLBACK = Map.of(
            "chests/hidden_hetreat_accessories", Set.of("touhou_little_maid:ultramarine_orb_elixir",
                    "touhou_little_maid:explosion_protect_bauble", "touhou_little_maid:fire_protect_bauble",
                    "touhou_little_maid:projectile_protect_bauble", "touhou_little_maid:magic_protect_bauble",
                    "touhou_little_maid:fall_protect_bauble", "touhou_little_maid:drown_protect_bauble",
                    "touhou_little_maid:nimble_fabric", "touhou_little_maid:item_magnet_bauble",
                    "touhou_little_maid:substitute_jizo", "minecraft:totem_of_undying"),
            "chests/hidden_hetreat_spell", Set.of("minecraft:paper", "minecraft:book", "minecraft:enchanted_book",
                    "minecraft:lapis_lazuli", "minecraft:experience_bottle"),
            "chests/hidden_hetreat_stuff", Set.of("minecraft:amethyst_shard", "minecraft:ender_pearl",
                    "minecraft:glowstone_dust", "minecraft:blaze_powder", "touhou_little_maid:power_point"));


    @DisplayName("只装车万女仆时本模组的数据加载没有错误")
    @Test
    void noDataLoadErrors(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MissingModFixtures.noDataLoadErrors(ctx, NS));
    }

    @DisplayName("不装铁魔法时，依赖它的物品、实体、配方、战利品表都不注册")
    @Test
    void ironsContentNotRegistered(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> ironsContentAbsent(ctx));
    }

    @DisplayName("不装铁魔法时，依赖它的结构与结构集都不注册")
    @Test
    void ironsStructuresNotRegistered(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> structuresAbsent(ctx, IRONS_STRUCTURES, IRONS_STRUCTURE_SETS));
    }

    @DisplayName("不装 Goety 时，依赖它的结构与结构集都不注册")
    @Test
    void goetyStructuresNotRegistered(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> structuresAbsent(ctx, GOETY_STRUCTURES, GOETY_STRUCTURE_SETS));
    }

    @DisplayName("不装 Goety 时，虚拟法杖物品不注册")
    @Test
    void goetyVirtualWandNotRegistered(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> goetyVirtualWandAbsent(ctx));
    }

    @DisplayName("不依赖可选模组的结构在只装车万女仆时仍然注册")
    @Test
    void baseStructuresRegistered(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> baseStructuresPresent(ctx));
    }

    @DisplayName("不装铁魔法时，隐世之境三类箱子改开替代物品且不含铁魔法物品")
    @Test
    void retreatChestsFallBack(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> retreatChestsFallBack(ctx));
    }

    @DisplayName("含可选物品的战利品表照常加载，抽不出未安装模组的物品")
    @Test
    void optionalItemTablesSkipAbsentItems(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MissingModFixtures.optionalItemTables(ctx, Set.of("irons_spellbooks", "farmersdelight", "goety")));
    }

    @DisplayName("只装车万女仆时女仆照常生成并能被驯服")
    @Test
    void maidSpawnsAndTames(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> maidTames(ctx));
    }

    /** 没有 Curios 时梦云水晶仍是女仆饰品：戴上后第一次致死被救下。 */
    @DisplayName("没有 Curios 时梦云水晶仍是女仆饰品，戴上后第一次致死被救下")
    @Test
    void dreamCrystalWithoutCurios(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        DreamCrystalFixtures.reviveFirstDeath(Stage.flat(game, plot, reporter));
    }

    private static void ironsContentAbsent(Stage ctx) {
        List<String> present = new ArrayList<>();
        IRONS_ITEMS.forEach(id -> {
            if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(NS + id))) {
                present.add("物品 " + id);
            }
        });
        IRONS_ENTITIES.forEach(id -> {
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(NS + id))) {
                present.add("实体 " + id);
            }
        });
        IRONS_RECIPES.forEach(id -> {
            if (ctx.server().getRecipeManager().byKey(ResourceLocation.parse(NS + id)).isPresent()) {
                present.add("配方 " + id);
            }
        });
        IRONS_LOOT_TABLES.forEach(id -> {
            if (LootReach.lootExists(ctx.server(), NS + id)) {
                present.add("战利品表 " + id);
            }
        });
        ctx.check(present).as("不装铁魔法时仍然存在的铁魔法内容").isEmpty();
    }

    /** 虚拟法杖实现了 Goety 的 IWand，不装 Goety 时它不该登记。 */
    private static void goetyVirtualWandAbsent(Stage ctx) {
        ctx.check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(NS + "virtual_goety_wand")))
                .as("不装 Goety 时的虚拟法杖物品").isFalse();
    }

    private static void structuresAbsent(Stage ctx, List<String> structures, List<String> sets) {
        List<String> present = new ArrayList<>();
        structures.stream().filter(id -> MissingModFixtures.has(ctx, Registries.STRUCTURE, id)).forEach(id -> present.add("结构 " + id));
        sets.stream().filter(id -> MissingModFixtures.has(ctx, Registries.STRUCTURE_SET, id)).forEach(id -> present.add("结构集 " + id));
        ctx.check(present).as("缺少依赖模组时仍然注册的结构").isEmpty();
    }

    private static void baseStructuresPresent(Stage ctx) {
        BASE_STRUCTURES.forEach(id -> ctx.check(MissingModFixtures.has(ctx, Registries.STRUCTURE, id)).as("结构 " + id + " 已注册").isTrue());
    }

    /** 隐世之境的饰品、墨水、法术材料三类箱子不再是空的，开出的都是替代物品，不含铁魔法物品。 */
    private static void retreatChestsFallBack(Stage ctx) {
        RETREAT_FALLBACK.forEach((table, allowed) -> {
            String id = NS + table;
            if (!LootReach.lootExists(ctx.server(), id)) {
                throw ctx.fail("战利品表 " + id + " 未加载");
            }
            Set<String> rolled = MissingModFixtures.rollChest(ctx, id, 200);
            Set<String> foreign = new TreeSet<>(rolled);
            foreign.removeAll(allowed);
            ctx.record(table, rolled);
            ctx.check(rolled).as(table + " 开 200 次的物品").isNotEmpty();
            ctx.check(foreign).as(table + " 里不在替代清单内的物品").isEmpty();
        });
    }

    private static void maidTames(Stage ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        ctx.check(maid.isAlive()).as("女仆已生成").isTrue();
        ctx.check(Players.tame(Owners.visitor(ctx, "TlmsMinimalOwner", 2, 0, 0), maid)).as("驯服女仆").isTrue();
    }
}
