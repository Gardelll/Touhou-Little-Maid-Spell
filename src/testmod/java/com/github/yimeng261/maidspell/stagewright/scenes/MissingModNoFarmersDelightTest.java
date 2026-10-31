package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.LootReach;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.registries.Registries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 不装农夫乐事（妖怪们的飨宴与妖怪们的归家移植版强依赖它，一并不装）时的资源与内容。
 * <p>这个类在缺模组的拓扑里加载，只能引用原版、NeoForge、车万女仆与本模组的通用类。
 */
@Scene("tlms.noFarmersDelight")
class MissingModNoFarmersDelightTest {
    @Test
    void noDataLoadErrors(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MissingModFixtures.noDataLoadErrors(ctx, NS));
    }

    @Test
    void shadowAssassinDrops(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> LootTest.checkKillDrops(ctx, NS + "shadow_assassin", NS + "entities/shadow_assassin"));
    }

    @Test
    void rapierOnlySharpness(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> MissingModFixtures.rapierEnchantments(ctx, Map.of("minecraft:sharpness", 5)));
    }

    @Test
    void cafeNotRegistered(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> cafeAbsent(ctx));
    }

    @Test
    void optionalItemTablesSkipAbsentItems(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MissingModFixtures.optionalItemTables(ctx, Set.of("farmersdelight")));
    }

    /** 妖怪们的归家强依赖农夫乐事，这个组合里不装；妖精女仆咖啡厅与它的箱子都不加载。 */
    private static void cafeAbsent(Stage ctx) {
        ctx.check(MissingModFixtures.has(ctx, Registries.STRUCTURE, "fairy_maid_cafe")).as("妖精女仆咖啡厅已注册").isFalse();
        for (String table : List.of("asset", "dessert", "drink", "ingredient")) {
            ctx.check(LootReach.lootExists(ctx.server(), NS + "chests/fairy_maid_cafe_" + table)).as("咖啡厅 " + table + " 箱子已加载").isFalse();
        }
    }
}
