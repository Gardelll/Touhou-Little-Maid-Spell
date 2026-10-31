package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.LootReach;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.registries.Registries;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("缺农夫乐事时的资源与内容")
@Scene("tlms.noFarmersDelight")
class MissingModNoFarmersDelightTest {
    @DisplayName("本模组的战利品表、标签、配方、结构数据包条目加载时没有解析报错")
    @Test
    void noDataLoadErrors(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MissingModFixtures.noDataLoadErrors(ctx, NS));
    }

    @DisplayName("暗影刺客的掉落表正常加载，击杀后能掉出物品")
    @Test
    void shadowAssassinDrops(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> LootTest.checkKillDrops(ctx, NS + "shadow_assassin", NS + "entities/shadow_assassin"));
    }

    @DisplayName("暗影刺客掉落的影刃只带锋利 5 附魔，没有农夫乐事的附魔")
    @Test
    void rapierOnlySharpness(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> MissingModFixtures.rapierEnchantments(ctx, Map.of("minecraft:sharpness", 5)));
    }

    @DisplayName("妖精女仆咖啡厅结构未注册，它的四种箱子战利品表也不加载")
    @Test
    void cafeNotRegistered(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> cafeAbsent(ctx));
    }

    @DisplayName("战利品表里的农夫乐事联动物品缺席时整张表仍可用，且抽不出缺席模组的物品")
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
