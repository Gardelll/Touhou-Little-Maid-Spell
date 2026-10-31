package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.LootReach;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.stagewright.support.Kills;
import com.github.yimeng261.maidspell.stagewright.support.Logs;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 缺可选模组的拓扑与完整模组组合共用的检查。
 * <p>这个类在缺模组的拓扑里加载，只能引用原版、NeoForge、车万女仆与本模组的通用类。
 */
final class MissingModFixtures {
    private static final String RAPIER = "irons_spellbooks:amethyst_rapier";
    private static final String RAPIER_NAME = "item.touhou_little_maid_spell.loot.shadow_assassin_rapier";

    /** 按联动模组开关生成的结构的箱子：条目里的联动物品用 optional_mod_item，模组缺席时整张表仍然可用。 */
    private static final List<String> OPTIONAL_ITEM_TABLES = List.of("chests/cherry_cabin_food_1",
            "chests/cherry_cabin_food_2", "chests/cherry_cabin_food_3", "chests/cherry_cabin_asset_without_iss",
            "chests/enchantress_footsteps_nether_1", "chests/enchantress_footsteps_nether_2",
            "chests/enchantress_footsteps_nether_3", "chests/enchantress_footsteps_nether_4");


    private MissingModFixtures() {
    }

    /** 本次启动的日志里没有本模组数据包条目（战利品表、标签、配方、结构）的解析报错。 */
    static void noDataLoadErrors(Stage ctx, String namespace) {
        List<String> problems = Logs.problems(namespace, Logs.DATA_LOAD_ERRORS);
        if (problems == null) {
            throw ctx.fail("读不到 " + Logs.path());
        }
        ctx.check(problems).as(Logs.path() + " 里本模组数据的解析报错").isEmpty();
    }

    static <T> boolean has(Stage ctx, ResourceKey<Registry<T>> registry, String id) {
        return ctx.server().registryAccess().registryOrThrow(registry).containsKey(ResourceLocation.parse(NS + id));
    }


    /** 缺联动模组时战利品表仍然加载并能抽出物品，且抽不出缺席模组的物品。 */
    static void optionalItemTables(Stage ctx, Set<String> absentNamespaces) {
        for (String table : OPTIONAL_ITEM_TABLES) {
            String id = NS + table;
            if (!LootReach.lootExists(ctx.server(), id)) {
                throw ctx.fail("战利品表 " + id + " 未加载");
            }
            Set<String> rolled = rollChest(ctx, id, 200);
            ctx.record(table, rolled);
            ctx.check(rolled).as(table + " 开 200 次的物品").isNotEmpty();
            ctx.check(rolled.stream().filter(item -> absentNamespaces.contains(item.substring(0, item.indexOf(':')))).toList())
                    .as(table + " 里缺席模组的物品").isEmpty();
        }
    }

    static Set<String> rollChest(Stage ctx, String id, int rolls) {
        var pos = ctx.rel(0, 0, 0);
        var previous = ctx.level().getBlockState(pos);
        ctx.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        ctx.cleanup(() -> ctx.level().setBlockAndUpdate(pos, previous));
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) ctx.level().getBlockEntity(pos);
        var table = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id));
        Set<String> items = new TreeSet<>();
        for (int roll = 1; roll <= rolls; roll++) {
            chest.clearContent();
            chest.setLootTable(table, roll);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (!stack.isEmpty()) items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return items;
    }

    /** 击杀一只影刺客，掉落的细剑恰好带这些附魔。 */
    static void rapierEnchantments(Stage ctx, Map<String, Integer> expected) {
        Kills.Result result = Kills.drops(ctx, NS + "shadow_assassin", 1);
        if (result == null) {
            return;
        }
        List<ItemStack> rapiers = result.drops().stream()
                .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(RAPIER))
                .filter(stack -> stack.get(DataComponents.CUSTOM_NAME) != null
                        && stack.get(DataComponents.CUSTOM_NAME).getContents() instanceof TranslatableContents name
                        && name.getKey().equals(RAPIER_NAME))
                .toList();
        ctx.check(rapiers.size()).as("掉落表给的细剑（" + RAPIER_NAME + "）数").isEqualTo(1);
        if (rapiers.size() != 1) {
            return;
        }
        Map<String, Integer> actual = new TreeMap<>();
        ItemEnchantments enchantments = rapiers.getFirst().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            actual.put(enchantment.getRegisteredName(), enchantments.getLevel(enchantment));
        }
        ctx.check(actual).as("细剑的附魔").isEqualTo(new TreeMap<>(expected));
    }

}
