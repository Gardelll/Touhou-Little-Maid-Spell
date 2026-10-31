package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.data.LootSnapshots;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Kills;
import com.github.yimeng261.maidspell.stagewright.support.LootReach;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 战利品表测试：每个结构一组表的可达集合快照、实体类型绑定的掉落表、真实击杀掉落。
 */
@DisplayName("战利品表：可达物品、实体掉落表绑定与击杀掉落")
@Scene("tlms")
class LootTest {
    /** 实体 → 默认掉落表。 */
    private static final Map<String, String> ENTITY_TABLES = Map.of(
            "touhou_little_maid_spell:shadow_assassin", "touhou_little_maid_spell:entities/shadow_assassin",
            "touhou_little_maid_spell:holy_construct", "touhou_little_maid_spell:entities/holy_construct",
            "touhou_little_maid_spell:corrupted_knight", "touhou_little_maid_spell:entities/corrupted_knight",
            "touhou_little_maid_spell:elf_templar", "touhou_little_maid_spell:entities/elf_templar");

    /** 每种实体击杀的次数：单次掉落可能为空，多次取并集。 */
    private static final int KILLS = 8;

    /** 逐组检查：表已加载，可达物品与快照一致；每项断言的说明里带表名。 */
    @DisplayName("每个结构的战利品表都已加载，可达物品与快照一致")
    @Test
    void tables(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> LootSnapshots.GROUPS.forEach((group, tables) -> checkTables(ctx, group, tables)));
    }

    @DisplayName("各实体类型已注册，且默认掉落表绑定到各自的战利品表")
    @Test
    void entityTableBinding(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> checkEntityBinding(ctx));
    }

    @DisplayName("击杀暗影刺客的掉落都来自它的掉落表且并集非空")
    @Test
    void killDropsShadowAssassin(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        killDrops(game, plot, reporter, "touhou_little_maid_spell:shadow_assassin");
    }

    @DisplayName("击杀圣素构造体的掉落都来自它的掉落表且并集非空")
    @Test
    void killDropsHolyConstruct(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        killDrops(game, plot, reporter, "touhou_little_maid_spell:holy_construct");
    }

    @DisplayName("击杀入魔骑士的掉落都来自它的掉落表且并集非空")
    @Test
    void killDropsCorruptedKnight(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        killDrops(game, plot, reporter, "touhou_little_maid_spell:corrupted_knight");
    }

    @DisplayName("击杀精灵守卫的掉落都来自它的掉落表且并集非空")
    @Test
    void killDropsElfTemplar(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        killDrops(game, plot, reporter, "touhou_little_maid_spell:elf_templar");
    }

    private static void killDrops(Game game, Plot plot, TestReporter reporter, String entity) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> checkKillDrops(ctx, entity, ENTITY_TABLES.get(entity)));
    }

    private static void checkTables(Stage ctx, String group, List<String> tables) {
        MinecraftServer server = ctx.server();
        for (String table : tables) {
            boolean exists = LootReach.lootExists(server, table);
            ctx.check(exists).as(group + " 的战利品表 " + table + " 已加载").isTrue();
            if (exists) {
                Checks.sameSet(ctx, table + " 可达物品", LootSnapshots.REACHABLE.get(table), LootReach.reachable(server, table));
            }
        }
    }

    private static void checkEntityBinding(Stage ctx) {
        ENTITY_TABLES.forEach((entity, table) -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entity));
            ctx.check(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()).as(entity + " 已注册").isEqualTo(entity);
            ctx.check(type.getDefaultLootTable().location().toString()).as(entity + " 的默认掉落表").isEqualTo(table);
        });
    }

    /** 用假玩家击杀若干次，掉落物都应来自该实体的掉落表，且并集非空（服务端线程）。 */
    static void checkKillDrops(Stage ctx, String entityId, String table) {
        MinecraftServer server = ctx.server();
        Kills.Result result = Kills.drops(ctx, entityId, KILLS);
        if (result == null) {
            return;
        }
        Set<String> dropped = result.dropKeys(server);
        ctx.record("drops", dropped);
        ctx.record("equipment", result.equipment());
        ctx.record("survivedLethalHit", result.stillAlive());
        Set<String> allowed = new TreeSet<>(LootReach.reachable(server, table));
        allowed.addAll(result.equipment());
        Set<String> foreign = new TreeSet<>(dropped);
        foreign.removeAll(allowed);
        ctx.check(dropped).as(entityId + " 击杀 " + KILLS + " 次的掉落").isNotEmpty();
        ctx.check(foreign).as(entityId + " 掉落中不属于 " + table + " 的物品").isEmpty();
    }
}
