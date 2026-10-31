package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 全套模组下作为缺模组拓扑对照的几项：装了农夫乐事时细剑还带背刺；联动物品条目在模组在场时正常掉落；
 * 模组信息声明的可选依赖与版本下限。
 */
@Scene("tlms")
@DisplayName("缺模组拓扑的全套模组对照")
class MissingModTest {
    @DisplayName("装了农夫乐事时，暗影刺客掉落的细剑带锋利 5 与背刺 3")
    @Test
    void shadowAssassinRapierWithFarmersDelight(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> MissingModFixtures.rapierEnchantments(ctx, Map.of("minecraft:sharpness", 5, "farmersdelight:backstabbing", 3)));
    }

    @DisplayName("联动模组在场时，战利品表里的联动物品正常掉落")
    @Test
    void optionalItemsDropWhenModsPresent(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> optionalItemsDropWhenPresent(ctx));
    }

    @DisplayName("可选物品条目指向不存在的物品时被跳过，战利品表照常加载并掉落其余条目")
    @Test
    void optionalModItemSkipsUnknownItem(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> optionalItemSkipsUnknown(ctx));
    }

    @DisplayName("可选物品条目在物品存在时按权重与品质展开，物品不存在时不展开")
    @Test
    void optionalModItemKeepsWeightAndQuality(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> optionalItemWeight(ctx));
    }

    @DisplayName("模组信息声明的可选依赖与版本下限符合预期")
    @Test
    void optionalDependencies(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> optionalDependencies(ctx));
    }

    /** 可选依赖 → 版本范围。低于下限的版本因范围不满足无法启动（由 NeoForge 负责，这里只核对声明）。 */
    private static final Map<String, String> OPTIONAL_DEPENDENCIES = Map.of("goety", "[3.0.0,)",
            "youkaishomecoming", "[3.0.0,)", "youkaisfeasts", "[1.1.1,)", "psi", "[1.21.1-110,2.0.0),(2.0.0,)", "slashblade", "[2.0.0,)", "usefulmagic", "[3.1.0,)",
            "enigmaticaddons", "[0,)", "travelerstitles", "[1.21.1-NeoForge-5.1.0,)", "farmersdelight", "[1.3.0,)",
            "callresponse", "[2.2.1,)");

    private static void optionalDependencies(Stage ctx) {
        IModInfo info = ModList.get().getModContainerById(MaidSpellMod.MOD_ID).orElseThrow().getModInfo();
        Map<String, String> declared = new TreeMap<>();
        for (IModInfo.ModVersion dependency : info.getDependencies()) {
            if (OPTIONAL_DEPENDENCIES.containsKey(dependency.getModId())) {
                declared.put(dependency.getModId(), dependency.getType() + " " + dependency.getVersionRange());
            }
        }
        Map<String, String> expected = new TreeMap<>();
        OPTIONAL_DEPENDENCIES.forEach((mod, range) -> expected.put(mod, IModInfo.DependencyType.OPTIONAL + " " + range));
        ctx.check(declared).as("模组信息里的可选依赖").isEqualTo(expected);
    }

    /** 装着联动模组时，条目里的联动物品会正常掉落。 */
    private static void optionalItemsDropWhenPresent(Stage ctx) {
        Set<String> food = MissingModFixtures.rollChest(ctx, NS + "chests/cherry_cabin_food_1", 200);
        ctx.record("cherry_cabin_food_1", food);
        ctx.check(food.stream().anyMatch(id -> id.startsWith("farmersdelight:"))).as("cherry_cabin_food_1 掉出农夫乐事物品").isTrue();
        Set<String> nether = MissingModFixtures.rollChest(ctx, NS + "chests/enchantress_footsteps_nether_2", 200);
        ctx.record("enchantress_footsteps_nether_2", nether);
        ctx.check(nether.stream().anyMatch(id -> id.startsWith("irons_spellbooks:"))).as("enchantress_footsteps_nether_2 掉出铁魔法物品").isTrue();
    }

    /** 条目指向不存在的物品时不进入抽取，也不让战利品表加载失败；同表里存在的条目照常掉落。 */
    private static void optionalItemSkipsUnknown(Stage ctx) {
        JsonObject json = JsonParser.parseString("""
                {"pools":[{"rolls":1,"entries":[
                  {"type":"touhou_little_maid_spell:optional_mod_item","name":"missing_integration:food","weight":100},
                  {"type":"touhou_little_maid_spell:optional_mod_item","name":"minecraft:diamond","weight":1}]}]}
                """).getAsJsonObject();
        var table = LootTable.DIRECT_CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE,
                        ctx.server().registryAccess()), json)
                .getOrThrow(message -> new IllegalStateException("无法解析战利品表: " + message));
        var params = new LootParams.Builder(ctx.level())
                .create(LootContextParamSets.EMPTY);
        Set<String> dropped = new TreeSet<>();
        for (int roll = 0; roll < 50; roll++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                dropped.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount());
            }
        }
        ctx.check(dropped).as("缺失物品的条目被跳过，只掉落存在的物品").isEqualTo(Set.of("minecraft:diamond*1"));
    }

    /** 物品存在时条目照常展开：权重按 weight + quality × luck 计算，掉落一个该物品；物品不存在时不展开也不掉落。 */
    private static void optionalItemWeight(Stage ctx) {
        var context = new LootContext.Builder(new LootParams.Builder(ctx.level()).withLuck(1.0F)
                .create(LootContextParamSets.EMPTY)).create(Optional.empty());
        List<LootPoolEntry> rolls = new ArrayList<>();
        ctx.check(optionalEntry(ctx, "minecraft:diamond").expand(context, rolls::add)).as("钻石条目展开").isTrue();
        ctx.check(rolls).as("钻石条目展开出的抽取项").hasSize(1);
        if (rolls.size() == 1) {
            ctx.check(rolls.get(0).getWeight(1.0F)).as("weight 3 + quality 2 × luck 1").isEqualTo(5);
            List<String> drops = new ArrayList<>();
            rolls.get(0).createItemStack(stack -> drops.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount()), context);
            ctx.check(drops).as("钻石条目的掉落").isEqualTo(List.of("minecraft:diamond*1"));
        }
        List<LootPoolEntry> missing = new ArrayList<>();
        ctx.check(optionalEntry(ctx, "missing_integration:food").expand(context, missing::add)).as("缺失物品条目展开").isFalse();
        ctx.check(missing).as("缺失物品条目展开出的抽取项").isEmpty();
    }

    private static LootPoolEntryContainer optionalEntry(Stage ctx, String item) {
        JsonObject json = JsonParser.parseString("""
                {"type":"touhou_little_maid_spell:optional_mod_item","name":"%s","weight":3,"quality":2}
                """.formatted(item)).getAsJsonObject();
        return LootPoolEntries.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, ctx.server().registryAccess()), json)
                .getOrThrow(message -> new IllegalStateException("无法解析战利品条目: " + message));
    }
}
