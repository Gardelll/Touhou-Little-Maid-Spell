package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.MaidOriginData;
import com.github.yimeng261.maidspell.stagewright.data.StructureSnapshots;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Fidelity;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.StructureStage;
import com.github.yimeng261.maidspell.stagewright.support.WorldExtract;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * 结构内容：每个结构放置一次，依次检查方块 / 方块实体 / 容器 / 实体 / 女仆，外加村庄补充街道与房屋、模板内容和已知缺陷。
 * <p>每个测试各自在超平坦场地放置一次结构（场地互相独立，不能共享放置）；场地按结构的占地加大。
 */
@DisplayName("结构内容")
@Scene("tlms")
class StructureTest {
    /** 单模板结构（optional_mod_template）：要放置的模板、所属结构名、是否带结构女仆。 */
    private record SingleTemplate(String template, String structure, boolean maid) {
    }

    private static final List<String> VILLAGE_BIOMES = List.of("desert", "plains", "savanna", "snowy", "taiga");

    private static final String VILLAGE = Checks.NS + "enchantress_footsteps/village/";

    /** 不逐键比对的（模板, "方块#键"）：格式变化由语义场景覆盖，已知缺陷由 required=false 的场景单独断言。 */
    private static final BiPredicate<String, String> KNOWN_BE_DEFECTS = (template, key) ->
            // Goety 1.21 的缠魂水槽固定是无限水，不读模板里的 FluidName/Amount（structure.yin_yang_altar.hauntedJugHasWater）
            template.equals("touhou_little_maid_spell:yin_yang_altar/yin_yang_altar")
                    && (key.equals("goety:haunted_jug#FluidName") || key.equals("goety:haunted_jug#Amount"));

    /** 平原房屋的椅子，由 village.plains.house.chairsNotInBlocks 单独检查。 */
    private static final String CHAIR = "touhou_little_maid:chair";

    /** 实体放置后观察多少 tick 再检查存活/卡墙。 */
    private static final int SETTLE_TICKS = 40;

    /** 挂着画的模板（本模组命名空间下的路径）、放置时注册的结构、强制加载区块半径。 */
    private record PaintingTemplate(String path, String structure, int radius) {
    }

    /** 本模组所有挂着画的模板，由 structure.paintings.templatesListed 与模板池核对。 */
    private static final List<PaintingTemplate> PAINTING_TEMPLATES = List.of(
            new PaintingTemplate("starwatch_tower/starwatch_tower_1", Checks.NS + "starwatch_tower", 2),
            new PaintingTemplate("starwatch_tower/starwatch_tower_2", Checks.NS + "starwatch_tower", 3),
            new PaintingTemplate("starfall_garden_5", Checks.NS + "starfall_garden", 3),
            new PaintingTemplate("hidden_retreat/hidden_retreat_building", Checks.NS + "hidden_retreat", 2),
            new PaintingTemplate("fairy_maid_cafe/fairy_maid_cafe", Checks.NS + "fairy_maid_cafe", 2),
            new PaintingTemplate("fairy_maid_cafe/fairy_maid_cafe_yh_port", Checks.NS + "fairy_maid_cafe", 2),
            new PaintingTemplate("fairy_maid_cafe/fairy_maid_cafe_youkaisfeasts", Checks.NS + "fairy_maid_cafe", 2),
            new PaintingTemplate("enchantress_footsteps/village/snowy_house", "minecraft:village_snowy", 2));

    /**
     * 画的中心在偶数尺寸方向上落在方块边界，按中心取整时两组旋转的边界取向相反：
     * 原 x 在 {NONE, CW90} 与 {CW180, CCW90} 间翻转，原 z 在 {NONE, CCW90} 与 {CW90, CW180} 间翻转，
     * 一对相反旋转就覆盖了两个轴的两种取向。
     */
    private static final List<Rotation> PAINTING_ROTATIONS = List.of(Rotation.NONE, Rotation.CLOCKWISE_180);

    private static final String PAINTING = "minecraft:painting";

    @DisplayName("隐世之境：放置后方块、容器、女仆与实体都与模板一致，生物不死不卡墙")
    @Test
    void hiddenRetreat(Game game, @Plot.Size(x = 176, y = 256, z = 176) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "hidden_retreat", true, Set.of());
    }

    /** 狐狸的落地高度由 {@link #foxOnGround} 单独检查，这里不计入死亡与卡墙。 */
    @DisplayName("隐世樱花树：放置后内容与模板一致（狐狸另测落地高度）")
    @Test
    void hiddenCherryTree(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "hidden_cherry_tree", false, Set.of("minecraft:fox"));
    }

    @DisplayName("妖精女仆咖啡厅：放置后内容与模板一致，结构女仆数据正确")
    @Test
    void fairyMaidCafe(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "fairy_maid_cafe", true, Set.of());
    }

    @DisplayName("阴阳祭坛：放置后内容与模板一致")
    @Test
    void yinYangAltar(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "yin_yang_altar", false, Set.of());
    }

    @DisplayName("圣遗礼拜堂：放置后内容与模板一致，结构女仆数据正确")
    @Test
    void relicSanctum(Game game, @Plot.Size(x = 144, y = 256, z = 144) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "relic_sanctum", true, Set.of());
    }

    @DisplayName("堕天圣堂：放置后内容与模板一致，结构女仆数据正确")
    @Test
    void fallenSanctum(Game game, @Plot.Size(x = 240, y = 256, z = 240) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "fallen_sanctum", true, Set.of());
    }

    @DisplayName("精灵秘境：放置后内容与模板一致，结构女仆数据正确")
    @Test
    void elvenRealm(Game game, @Plot.Size(x = 208, y = 256, z = 208) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "elven_realm", true, Set.of());
    }

    @DisplayName("魔女足迹-林间栖所：放置后内容与模板一致")
    @Test
    void woodsPerch(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "woods_perch", false, Set.of());
    }

    @DisplayName("魔女足迹-蘑菇岛屋：放置后内容与模板一致")
    @Test
    void enchantressFootstepsMushroomFields(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "enchantress_footsteps_mushroom_fields", false, Set.of());
    }

    @DisplayName("魔女足迹-雪原冰屋：放置后内容与模板一致")
    @Test
    void enchantressFootstepsIgloo(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "enchantress_footsteps_igloo", false, Set.of());
    }

    @DisplayName("魔女足迹-掠夺者前哨站：放置后内容与模板一致")
    @Test
    void enchantressFootstepsOutpost(Game game, @Plot.Size(x = 144, y = 256, z = 144) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "enchantress_footsteps_outpost", false, Set.of());
    }

    @DisplayName("魔女足迹-墓园：放置后内容与模板一致")
    @Test
    void enchantressFootstepsGraveyard(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "enchantress_footsteps_graveyard", false, Set.of());
    }

    @DisplayName("魔女足迹-绿洲：放置后内容与模板一致")
    @Test
    void enchantressFootstepsOasis(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "enchantress_footsteps_oasis", false, Set.of());
    }

    @DisplayName("观星塔：放置后方块、容器与实体都与模板一致，观星术士不死不卡墙")
    @Test
    void starwatchTower(Game game, @Plot.Size(x = 144, y = 256, z = 144) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        structure(ctx, "starwatch_tower", false, Set.of());
    }

    @DisplayName("星途终岸：放置后方块、容器与实体都与模板一致，星之魔女不死不卡墙")
    @Test
    void stellarEndshore(Game game, @Plot.Size(x = 176, y = 256, z = 176) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        content(ctx, c -> {
            StructureStage.Placed placed = StructureStage.place(c, Checks.NS + "stellar_endshore");
            // 星之魔女是 Boss，plot 还原时的普通移除会被拒绝，按授权的 Boss 清理移除，免得留给后续测试
            WorldExtract.entities(c.level(), placed).stream()
                    .filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(Checks.NS + "stellar_witch"))
                    .forEach(witch -> c.cleanup(() -> Actors.cleanupEntity(witch)));
            return placed;
        }, false, Set.of());
    }

    /** 星落之庭的结构类型自己选址（每个世界一座、悬空），这里只从起始池组装，比对内容。 */
    @DisplayName("星落之庭：从起始池组装后方块、容器与实体都与模板一致")
    @Test
    void starfallGarden(Game game, @Plot.Size(x = 176, y = 256, z = 176) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        content(ctx, c -> StructureStage.placePool(c, Checks.NS + "starfall_garden", Checks.NS + "starfall_garden_start_pool", 10),
                false, Set.of());
    }

    @DisplayName("落樱厨舍：按模板直接放置后内容与模板一致，结构女仆数据正确")
    @Test
    void cherryCabin(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        singleTemplate(ctx, new SingleTemplate("cherry_cabin", "cherry_cabin", true));
    }

    /** 不装铁魔法时代替 cherry_cabin 的模板。 */
    @DisplayName("落樱厨舍：不装铁魔法时的替代模板放置后内容与模板一致，结构女仆数据正确")
    @Test
    void cherryCabinWithoutIss(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        singleTemplate(ctx, new SingleTemplate("cherry_cabin_without_iss", "cherry_cabin", true));
    }

    @DisplayName("魔女足迹-玄武岩锻坊：按模板直接放置后内容与模板一致")
    @Test
    void enchantressFootstepsBasaltForge(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        singleTemplate(ctx, new SingleTemplate("enchantress_footsteps_basalt_forge", "enchantress_footsteps_basalt_forge", false));
    }

    @DisplayName("魔女足迹-绯红林屋：按模板直接放置后内容与模板一致")
    @Test
    void enchantressFootstepsCrimsonCabin(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        singleTemplate(ctx, new SingleTemplate("enchantress_footsteps_crimson_cabin", "enchantress_footsteps_crimson_cabin", false));
    }

    @DisplayName("魔女足迹-魂谷一隅：按模板直接放置后内容与模板一致")
    @Test
    void enchantressFootstepsSoulvalleyNook(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        singleTemplate(ctx, new SingleTemplate("enchantress_footsteps_soulvalley_nook", "enchantress_footsteps_soulvalley_nook", false));
    }

    /** 不装 Goety 时代替 soulvalley_nook 的模板。 */
    @DisplayName("魔女足迹-魂谷一隅：不装 Goety 时的替代模板放置后内容与模板一致")
    @Test
    void enchantressFootstepsSoulvalleyNookWithoutGoety(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        singleTemplate(ctx, new SingleTemplate("enchantress_footsteps_soulvalley_nook_without_goety", "enchantress_footsteps_soulvalley_nook", false));
    }

    @DisplayName("魔女足迹-诡林栖所：按模板直接放置后内容与模板一致")
    @Test
    void enchantressFootstepsWarpedPerch(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        singleTemplate(ctx, new SingleTemplate("enchantress_footsteps_warped_perch", "enchantress_footsteps_warped_perch", false));
    }

    /** 从补充街道池组装一层（相邻的原版街道/尽头片一并放置）。 */
    @DisplayName("沙漠村庄补充街道：从街道池组装一层后方块与模板一致")
    @Test
    void villageDesertStreet(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> blocks(ctx, StructureStage.placePool(ctx, "minecraft:village_desert", VILLAGE + "desert_streets", 1)));
    }

    /** 直接从房屋池放置检查内容（自然村庄里经补充街道接入，见 NaturalTest#villageSupplementAppears）。 */
    @DisplayName("沙漠村庄补充房屋：从房屋池放置后内容与模板一致")
    @Test
    void villageDesertHouse(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        content(ctx, c -> placeHouse(c, "desert"), false, Set.of());
    }

    @DisplayName("平原村庄补充街道：从街道池组装一层后方块与模板一致")
    @Test
    void villagePlainsStreet(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> blocks(ctx, StructureStage.placePool(ctx, "minecraft:village_plains", VILLAGE + "plains_streets", 1)));
    }

    @DisplayName("平原村庄补充房屋：从房屋池放置后内容与模板一致（椅子另测）")
    @Test
    void villagePlainsHouse(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        content(ctx, c -> placeHouse(c, "plains"), false, Set.of(CHAIR));
    }

    @DisplayName("热带草原村庄补充街道：从街道池组装一层后方块与模板一致")
    @Test
    void villageSavannaStreet(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> blocks(ctx, StructureStage.placePool(ctx, "minecraft:village_savanna", VILLAGE + "savanna_streets", 1)));
    }

    @DisplayName("热带草原村庄补充房屋：从房屋池放置后内容与模板一致")
    @Test
    void villageSavannaHouse(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        content(ctx, c -> placeHouse(c, "savanna"), false, Set.of());
    }

    @DisplayName("雪原村庄补充街道：从街道池组装一层后方块与模板一致")
    @Test
    void villageSnowyStreet(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> blocks(ctx, StructureStage.placePool(ctx, "minecraft:village_snowy", VILLAGE + "snowy_streets", 1)));
    }

    @DisplayName("雪原村庄补充房屋：从房屋池放置后内容与模板一致")
    @Test
    void villageSnowyHouse(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        content(ctx, c -> placeHouse(c, "snowy"), false, Set.of());
    }

    @DisplayName("针叶林村庄补充街道：从街道池组装一层后方块与模板一致")
    @Test
    void villageTaigaStreet(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> blocks(ctx, StructureStage.placePool(ctx, "minecraft:village_taiga", VILLAGE + "taiga_streets", 1)));
    }

    @DisplayName("针叶林村庄补充房屋：从房屋池放置后内容与模板一致")
    @Test
    void villageTaigaHouse(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        content(ctx, c -> placeHouse(c, "taiga"), false, Set.of());
    }

    /** 街道的 minecraft:street 接口应指向本生物群系的原版街道池。 */
    @DisplayName("沙漠补充街道的街道接口指向沙漠原版街道池")
    @Test
    void villageDesertStreetLinksOwnBiome(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> streetLinksOwnBiome(ctx, "desert"));
    }

    @DisplayName("平原补充街道的街道接口指向平原原版街道池")
    @Test
    void villagePlainsStreetLinksOwnBiome(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> streetLinksOwnBiome(ctx, "plains"));
    }

    @DisplayName("热带草原补充街道的街道接口指向热带草原原版街道池")
    @Test
    void villageSavannaStreetLinksOwnBiome(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> streetLinksOwnBiome(ctx, "savanna"));
    }

    @DisplayName("雪原补充街道的街道接口指向雪原原版街道池")
    @Test
    void villageSnowyStreetLinksOwnBiome(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> streetLinksOwnBiome(ctx, "snowy"));
    }

    @DisplayName("针叶林补充街道的街道接口指向针叶林原版街道池")
    @Test
    void villageTaigaStreetLinksOwnBiome(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> streetLinksOwnBiome(ctx, "taiga"));
    }

    @DisplayName("观星塔模板 1 不旋转：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsStarwatchTowerStarwatchTower1None(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(0), Rotation.NONE);
    }

    @DisplayName("观星塔模板 1 旋转 180°：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsStarwatchTowerStarwatchTower1Clockwise180(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(0), Rotation.CLOCKWISE_180);
    }

    @DisplayName("观星塔模板 2 不旋转：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsStarwatchTowerStarwatchTower2None(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(1), Rotation.NONE);
    }

    @DisplayName("观星塔模板 2 旋转 180°：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsStarwatchTowerStarwatchTower2Clockwise180(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(1), Rotation.CLOCKWISE_180);
    }

    @DisplayName("星落之庭模板 5 不旋转：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsStarfallGarden5None(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(2), Rotation.NONE);
    }

    @DisplayName("星落之庭模板 5 旋转 180°：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsStarfallGarden5Clockwise180(Game game, @Plot.Size(x = 112, y = 128, z = 112) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(2), Rotation.CLOCKWISE_180);
    }

    @DisplayName("隐世之境主建筑不旋转：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsHiddenRetreatHiddenRetreatBuildingNone(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(3), Rotation.NONE);
    }

    @DisplayName("隐世之境主建筑旋转 180°：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsHiddenRetreatHiddenRetreatBuildingClockwise180(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(3), Rotation.CLOCKWISE_180);
    }

    @DisplayName("妖精女仆咖啡厅（妖怪们的归家官方版模板）不旋转：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsFairyMaidCafeFairyMaidCafeNone(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(4), Rotation.NONE);
    }

    @DisplayName("妖精女仆咖啡厅（妖怪们的归家官方版模板）旋转 180°：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsFairyMaidCafeFairyMaidCafeClockwise180(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(4), Rotation.CLOCKWISE_180);
    }

    @DisplayName("妖精女仆咖啡厅（妖怪们的归家移植版模板）不旋转：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsFairyMaidCafeFairyMaidCafeYhPortNone(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(5), Rotation.NONE);
    }

    @DisplayName("妖精女仆咖啡厅（妖怪们的归家移植版模板）旋转 180°：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsFairyMaidCafeFairyMaidCafeYhPortClockwise180(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(5), Rotation.CLOCKWISE_180);
    }

    @DisplayName("妖精女仆咖啡厅（Youkais' Feasts 模板）不旋转：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsFairyMaidCafeFairyMaidCafeYoukaisfeastsNone(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(6), Rotation.NONE);
    }

    @DisplayName("妖精女仆咖啡厅（Youkais' Feasts 模板）旋转 180°：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsFairyMaidCafeFairyMaidCafeYoukaisfeastsClockwise180(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(6), Rotation.CLOCKWISE_180);
    }

    @DisplayName("雪原村庄补充房屋模板不旋转：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsEnchantressFootstepsVillageSnowyHouseNone(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(7), Rotation.NONE);
    }

    @DisplayName("雪原村庄补充房屋模板旋转 180°：挂画的变体、挂靠格、朝向与模板一致且挂得住")
    @Test
    void paintingsEnchantressFootstepsVillageSnowyHouseClockwise180(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        paintingsStayAnchored(ctx, PAINTING_TEMPLATES.get(7), Rotation.CLOCKWISE_180);
    }

    @DisplayName("画的挂靠格经模板四种旋转变换后仍等于模板记录的 blockPos")
    @Test
    void paintingAnchorMechanism(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> paintingAnchorMechanism(ctx));
    }

    @DisplayName("本模组模板池里挂着画的模板，正好是测试列出的那些")
    @Test
    void paintingTemplatesListed(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> paintingTemplatesListed(ctx));
    }

    @DisplayName("服务器启动后五个原版村庄街道池都注入了补充街道（权重 1）")
    @Test
    void villagePoolInjection(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> poolInjection(ctx));
    }

    @DisplayName("隐世樱花树的狐狸生成在结构地面附近，不悬空也不埋在地下")
    @Test
    void foxOnGround(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> foxOnGround(ctx));
    }

    @DisplayName("阴阳祭坛的缠魂水槽装着水")
    @Test
    void hauntedJugHasWater(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> hauntedJugHasWater(ctx));
    }

    @DisplayName("平原房屋的炼药锅保留 250mb 罕见墨水")
    @Test
    void villagePlainsHouseCauldronKeepsInk(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> cauldronKeepsInk(ctx));
    }

    @DisplayName("针叶林房屋展示台上的紫晶细剑保留名字、锋利 II 和召唤剑刃 2 级")
    @Test
    void villageTaigaHousePedestalRapierKeepsData(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> rapierKeepsData(ctx));
    }

    @DisplayName("平原房屋模板里的椅子不嵌在实心方块里，也不在同一位置重复")
    @Test
    void villagePlainsHouseChairsNotInBlocks(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> chairsNotInBlocks(ctx));
    }

    @DisplayName("模板里的方块都来自已注册的方块，可选装饰模组没装时除外")
    @Test
    void templatesUseOnlyRegisteredBlocks(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> onlyRegisteredBlocks(ctx));
    }

    @DisplayName("模板里的女仆都带来源标记和预设效果快照，隐世之境的等待女仆带隐世女仆标记且没有主人")
    @Test
    void templateMaidsDeclareOrigin(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> templateMaidsDeclareOrigin(ctx));
    }

    @DisplayName("模板里物品框、容器、实体背包等处的物品都已注册")
    @Test
    void templatesUseOnlyRegisteredItems(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> onlyRegisteredItems(ctx));
    }

    /** 放置 NS + path 结构，依次检查各类内容。 */
    private static void structure(Stage ctx, String path, boolean maid, Set<String> knownDead) {
        content(ctx, c -> StructureStage.place(c, Checks.NS + path), maid, knownDead);
    }

    /** 按指定模板直接放置单模板结构（含不装联动模组时的回退模板），不经过结构自身的选址判定。 */
    @DisplayName("落樱厨舍的女仆按区块卸载重载后仍坐着，模型、手持物与骑乘状态不变")
    @Test
    void cherryCabinMaidSurvivesReload(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            SinglePoolElement element = StructurePoolElement.single(Checks.NS + "cherry_cabin")
                    .apply(StructureTemplatePool.Projection.RIGID);
            StructureStage.Placed placed = StructureStage.placeElement(ctx, Checks.NS + "cherry_cabin", element, Rotation.NONE);
            List<EntityMaid> maids = WorldExtract.entities(ctx.level(), placed).stream()
                    .filter(EntityMaid.class::isInstance).map(EntityMaid.class::cast).toList();
            if (maids.size() != 1) {
                throw ctx.fail("落樱厨舍里的女仆有 " + maids.size() + " 只");
            }
            return maids.getFirst();
        });
        ctx.ticks(5);
        String before = ctx.get(() -> seatedMaid(maid));
        ctx.record("重载前", before);
        Entity reloaded = Maids.reload(ctx, maid, 5);
        ctx.ticks(5);
        ctx.run(() -> ctx.check(reloaded instanceof EntityMaid back ? seatedMaid(back) : String.valueOf(reloaded))
                .as("重载后的女仆").isEqualTo(before));
    }

    private static String seatedMaid(EntityMaid maid) {
        return "坐着=" + maid.isMaidInSittingPose() + " 模型=" + maid.getModelId()
                + " 手持=" + BuiltInRegistries.ITEM.getKey(maid.getMainHandItem().getItem())
                + " 骑乘=" + (maid.getVehicle() == null ? "无" : BuiltInRegistries.ENTITY_TYPE.getKey(maid.getVehicle().getType()));
    }

    private static void singleTemplate(Stage ctx, SingleTemplate single) {
        content(ctx, c -> {
            SinglePoolElement element = StructurePoolElement.single(Checks.NS + single.template())
                    .apply(StructureTemplatePool.Projection.RIGID);
            return StructureStage.placeElement(c, Checks.NS + single.structure(), element, Rotation.NONE);
        }, single.maid(), Set.of());
    }

    private static StructureStage.Placed placeHouse(Stage ctx, String biome) {
        return StructureStage.placePool(ctx, "minecraft:village_" + biome, VILLAGE + biome + "_houses", 1);
    }

    /** 放置后检查方块、方块实体、容器、女仆和实体，再过 SETTLE_TICKS 个 tick 检查生物是否存活、有没有卡进方块。 */
    private static void content(Stage ctx, Function<Stage, StructureStage.Placed> place, boolean maid, Set<String> knownDead) {
        // 放进运行中的世界会给重力方块排计划刻（世界生成时不会），放置与比对须在同一 tick 内做完，免得悬空的沙子、混凝土粉末先落下
        List<Entity> entities = ctx.get(() -> {
            StructureStage.Placed placed = place.apply(ctx);
            blocks(ctx, placed);
            blockEntities(ctx, placed);
            containers(ctx, placed);
            if (maid) {
                maids(ctx, placed);
            }
            return entities(ctx, placed);
        });
        ctx.ticks(SETTLE_TICKS);
        ctx.run(() -> settled(ctx, entities, knownDead));
    }

    private static List<Entity> entities(Stage ctx, StructureStage.Placed placed) {
        ServerLevel level = ctx.level();
        List<Entity> entities = WorldExtract.entities(level, placed);
        Checks.sameMultiset(ctx, "实体（类型/展示物/装备/画）", WorldExtract.expected(placed).entities(),
                entities.stream().map(e -> WorldExtract.entity(WorldExtract.nbtOf(e))).toList());
        Fidelity.entities(ctx, level, placed);
        return entities;
    }

    private static void settled(Stage ctx, List<Entity> entities, Set<String> knownDead) {
        List<String> dead = new ArrayList<>();
        List<String> inWall = new ArrayList<>();
        for (Entity entity : entities) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (!(entity instanceof LivingEntity living) || knownDead.contains(type)) {
                continue;
            }
            // 无重力的椅子是挂在墙上的门铃一类模型，本来就嵌在墙里
            boolean wallMounted = type.equals(CHAIR) && living.isNoGravity();
            if (!living.isAlive() || living.isRemoved()) {
                dead.add(type + "@" + entity.blockPosition());
            } else if (living.isInWall() && !wallMounted) {
                inWall.add(type + "@" + entity.blockPosition());
            }
        }
        ctx.check(dead).as(SETTLE_TICKS + " tick 后死亡或消失的生物").isEmpty();
        ctx.check(inWall).as(SETTLE_TICKS + " tick 后卡在方块里的生物").isEmpty();
    }

    /** 已放置结构里指定方块的方块实体保存结果（按模板顺序）。 */
    private static List<CompoundTag> savedBlockEntities(Stage ctx, StructureStage.Placed placed, String block) {
        List<CompoundTag> out = new ArrayList<>();
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (StructureTemplate.StructureBlockInfo info : piece.blocks()) {
                if (info.nbt() != null && BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString().equals(block)) {
                    BlockEntity be = ctx.level().getBlockEntity(info.pos());
                    ctx.check(be).as(block + " 的方块实体 @" + info.pos()).isNotNull();
                    if (be != null) {
                        out.add(be.saveWithoutMetadata(ctx.level().registryAccess()));
                    }
                }
            }
        }
        ctx.check(out).as("结构中的 " + block).isNotEmpty();
        return out;
    }

    private static void blocks(Stage ctx, StructureStage.Placed placed) {
        ServerLevel level = ctx.level();
        Checks.sameSet(ctx, "特殊方块", WorldExtract.expected(placed).specialBlocks(), WorldExtract.specialBlocks(level, placed));
        Fidelity.blocks(ctx, level, placed);
    }

    private static void blockEntities(Stage ctx, StructureStage.Placed placed) {
        Fidelity.blockEntities(ctx, ctx.level(), placed, KNOWN_BE_DEFECTS);
    }

    private static void containers(Stage ctx, StructureStage.Placed placed) {
        Checks.sameMultiset(ctx, "容器（战利品表/固定物品/刷怪笼/手办）",
                WorldExtract.expected(placed).containers(), WorldExtract.containers(ctx.level(), placed));
    }

    private static void maids(Stage ctx, StructureStage.Placed placed) {
        List<Map<String, String>> expected = new ArrayList<>(WorldExtract.expected(placed).maids());
        List<Map<String, String>> actual = new ArrayList<>(WorldExtract.entities(ctx.level(), placed).stream()
                .filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(Actors.MAID))
                .map(e -> WorldExtract.maid(WorldExtract.nbtOf(e)))
                .toList());
        Comparator<Map<String, String>> order = Comparator.comparing(m -> m.get("model_id") + m.get("favorability"));
        expected.sort(order);
        actual.sort(order);
        ctx.check(actual.size()).as("结构女仆数量").isEqualTo(expected.size());
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            Map<String, String> want = expected.get(i);
            Map<String, String> got = actual.get(i);
            want.forEach((field, value) ->
                    ctx.check(got.get(field)).as("女仆[" + want.get("model_id") + "]." + field).isEqualTo(value));
        }
    }

    /**
     * 按指定旋转单独放置一个模板（取模板池里的拼图元素，处理器与自然生成一致），逐幅比对变体、挂靠格和朝向，
     * 并当场判定每幅画能否挂住（原版每 100 tick 才做一次同样的判定）。
     * 预期挂靠格是模板 blockPos 经原版变换后的位置，偏一格也算错。
     */
    private static void paintingsStayAnchored(Stage ctx, PaintingTemplate painting, Rotation rotation) {
        String unused = unusedTemplateReason(Checks.NS + painting.path());
        if (unused != null) {
            throw ctx.skip(unused);
        }
        ctx.run(() -> {
            ServerLevel level = ctx.level();
            StructureStage.Placed placed = StructureStage.placeElement(ctx, painting.structure(),
                    Worldgen.poolElement(level, Checks.NS + painting.path()), rotation);
            StructureStage.Piece piece = placed.pieces().getFirst();
            List<String> expected = piece.entities().stream()
                    .filter(StructureTest::isPainting)
                    .map(info -> paintingKey(info.nbt.getString("variant"), info.blockPos,
                            rotation.rotate(Direction.from2DDataValue(info.nbt.getByte("facing")))))
                    .toList();
            ctx.check(expected).as(piece.template() + " 里的画").isNotEmpty();

            List<Painting> paintings = level.getEntitiesOfClass(Painting.class, AABB.of(piece.box()).inflate(2), Entity::isAlive);
            List<String> actual = paintings.stream()
                    .map(p -> paintingKey(p.getVariant().unwrapKey().map(key -> key.location().toString()).orElse("?"),
                            p.getPos(), p.getDirection()))
                    .toList();
            ctx.record("paintings", actual);
            Checks.sameMultiset(ctx, "放置后的画（变体 @挂靠格 朝向）", expected, actual);
            ctx.check(paintings.stream().filter(p -> !p.survives()).map(p -> p.getPos().toShortString()).toList())
                    .as("挂不住的画").isEmpty();
        });
    }

    private static boolean isPainting(StructureTemplate.StructureEntityInfo info) {
        return info.nbt.getString("id").equals(PAINTING);
    }

    private static String paintingKey(String variant, BlockPos anchor, Direction facing) {
        return variant + " @" + anchor.toShortString() + " " + facing;
    }
    /**
     * 放置模板时画的挂靠格等于模板 blockPos 经旋转变换后的位置：每种尺寸的画按四个朝向保存成模板实体信息
     * （位置取实体中心，与结构方块保存的一致），四种旋转下经 processEntityInfos 处理后按位置取整都应回到 blockPos。
     * 同时确认用例里既有偶数高度、也有偶数宽度的画在直接按中心取整时会错位，保证覆盖了两类情形。
     */
    private static void paintingAnchorMechanism(Stage ctx) {
        ServerLevel level = ctx.level();
        Map<String, Holder<PaintingVariant>> bySize = new TreeMap<>();
        level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).holders()
                .forEach(variant -> bySize.putIfAbsent(variant.value().width() + "x" + variant.value().height(), variant));
        BlockPos anchor = new BlockPos(8, 8, 8);
        List<StructureTemplate.StructureEntityInfo> raw = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        bySize.forEach((size, variant) -> {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                Painting painting = new Painting(level, anchor, facing, variant);
                CompoundTag nbt = new CompoundTag();
                painting.save(nbt);
                raw.add(new StructureTemplate.StructureEntityInfo(painting.position(), anchor, nbt));
                labels.add(size + " " + facing);
            }
        });
        ctx.record("cases", raw.size() * Rotation.values().length);

        List<String> misplaced = new ArrayList<>();
        int heightMisses = 0;
        int widthMisses = 0;
        for (Rotation rotation : Rotation.values()) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
            List<StructureTemplate.StructureEntityInfo> placed =
                    StructureTemplate.processEntityInfos(null, level, BlockPos.ZERO, settings, raw);
            for (int i = 0; i < raw.size(); i++) {
                BlockPos expected = placed.get(i).blockPos;
                BlockPos rounded = BlockPos.containing(StructureTemplate.transformedVec3d(settings, raw.get(i).pos));
                heightMisses += rounded.getY() != expected.getY() ? 1 : 0;
                widthMisses += rounded.getX() != expected.getX() || rounded.getZ() != expected.getZ() ? 1 : 0;
                BlockPos actual = BlockPos.containing(placed.get(i).pos);
                if (!actual.equals(expected)) {
                    misplaced.add(labels.get(i) + " " + rotation + ": " + actual.toShortString() + " ≠ " + expected.toShortString());
                }
            }
        }
        ctx.record("heightMisses", heightMisses);
        ctx.record("widthMisses", widthMisses);
        ctx.check(heightMisses).as("按中心取整会上下错位的用例数（偶数高度）").isGreaterThan(0);
        ctx.check(widthMisses).as("按中心取整会水平错位的用例数（偶数宽度）").isGreaterThan(0);
        ctx.check(misplaced).as("处理后挂靠格偏离 blockPos 的画").isEmpty();
    }

    /** 本模组模板池引用的模板里，挂着画的正好是 PAINTING_TEMPLATES 列出的那些。 */
    private static void paintingTemplatesListed(Stage ctx) {
        StructureTemplateManager manager = ctx.level().getStructureManager();
        List<String> withPaintings = new ArrayList<>();
        Worldgen.poolElements(ctx.level()).keySet().stream().filter(id -> id.startsWith(Checks.NS)).forEach(id -> {
            List<StructureTemplate.StructureEntityInfo> entities =
                    Reflect.field(manager.getOrCreate(ResourceLocation.parse(id)), StructureTemplate.class, "entityInfoList");
            if (entities.stream().anyMatch(StructureTest::isPainting)) {
                withPaintings.add(id);
            }
        });
        Checks.sameSet(ctx, "挂着画的模板（PAINTING_TEMPLATES）",
                PAINTING_TEMPLATES.stream().map(painting -> Checks.NS + painting.path()).toList(), withPaintings);
    }

    /** 隐世樱树的狐狸生成在结构地面附近，不悬空也不埋在地下。 */
    private static void foxOnGround(Stage ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, "touhou_little_maid_spell:hidden_cherry_tree");
        StructureStage.Piece piece = WorldExtract.ownPieces(placed).getFirst();
        int ground = piece.blocks().stream()
                .filter(info -> !info.state().isAir() && !info.state().is(Blocks.STRUCTURE_VOID))
                .mapToInt(info -> info.pos().getY()).min().orElseThrow();
        for (StructureTemplate.StructureEntityInfo fox : piece.entities()) {
            ctx.record("foxY", fox.pos.y);
            ctx.record("structureGroundY", ground);
            ctx.check(fox.pos.y).as("狐狸生成高度（结构最低方块 y=" + ground + "）").isBetween(ground, ground + 4);
        }
    }

    /** 街道的 minecraft:street 接口应指向本生物群系的原版街道池。 */
    private static void streetLinksOwnBiome(Stage ctx, String biome) {
        for (StructureTemplate.StructureBlockInfo jigsaw : jigsaws(ctx, VILLAGE + biome + "_street")) {
            if (jigsaw.nbt().getString("name").equals("minecraft:street")) {
                ctx.check(jigsaw.nbt().getString("pool")).as(biome + "_street 的街道接口 @" + jigsaw.pos())
                        .isEqualTo("minecraft:village/" + biome + "/streets");
            }
        }
    }

    private static List<StructureTemplate.StructureBlockInfo> jigsaws(Stage ctx, String template) {
        return ctx.level().getStructureManager().getOrCreate(ResourceLocation.parse(template))
                .filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW);
    }

    /** 服务器启动后五个原版村庄街道池里都注入了补充街道（权重 1）。 */
    private static void poolInjection(Stage ctx) {
        var pools = ctx.level().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        for (String biome : VILLAGE_BIOMES) {
            StructureTemplatePool pool = pools.get(ResourceLocation.parse("minecraft:village/" + biome + "/streets"));
            String street = VILLAGE + biome + "_street";
            List<Integer> weights = pool.rawTemplates.stream()
                    .filter(pair -> templateOf(pair.getFirst()).equals(street))
                    .map(Pair::getSecond).toList();
            ctx.check(weights).as("minecraft:village/" + biome + "/streets 中 " + street + " 的权重").isEqualTo(List.of(1));
            long entries = pool.templates.stream().filter(element -> templateOf(element).equals(street)).count();
            ctx.check(entries).as("minecraft:village/" + biome + "/streets 抽取列表中的条目数").isEqualTo(1L);
        }
    }

    private static String templateOf(StructurePoolElement element) {
        return element instanceof SinglePoolElement single ? Worldgen.templateId(single) : "";
    }

    /** 阴阳祭坛的缠魂水槽装着水（模板里的流体键 Goety 不读，逐键比对跳过，这里按能力检查语义）。 */
    private static void hauntedJugHasWater(Stage ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, "touhou_little_maid_spell:yin_yang_altar");
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (StructureTemplate.StructureBlockInfo info : piece.blocks()) {
                if (!BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString().equals("goety:haunted_jug")) {
                    continue;
                }
                IFluidHandler handler = ctx.level().getCapability(Capabilities.FluidHandler.BLOCK, info.pos(), null);
                ctx.check(handler).as("缠魂水槽的流体能力 @" + info.pos()).isNotNull();
                if (handler != null) {
                    ctx.check(handler.getFluidInTank(0).getFluid().isSame(Fluids.WATER)).as("缠魂水槽装着水 @" + info.pos()).isTrue();
                }
            }
        }
    }

    /** 平原房屋炼药锅有 250mb 罕见墨水。 */
    private static void cauldronKeepsInk(Stage ctx) {
        for (CompoundTag saved : savedBlockEntities(ctx, placeHouse(ctx, "plains"), "irons_spellbooks:alchemist_cauldron")) {
            ListTag results = saved.getList("Results", Tag.TAG_COMPOUND);
            ctx.check(results.size()).as("炼药锅的液体条目数").isEqualTo(1);
            if (!results.isEmpty()) {
                ctx.check(results.getCompound(0).getString("id")).as("炼药锅液体").isEqualTo("irons_spellbooks:uncommon_ink");
                ctx.check(results.getCompound(0).getInt("amount")).as("炼药锅液体量").isEqualTo(250);
            }
        }
    }

    /** 泰加房屋展示台上的紫晶细剑保留名字、锋利 II 和召唤剑刃 2 级。 */
    private static void rapierKeepsData(Stage ctx) {
        for (CompoundTag saved : savedBlockEntities(ctx, placeHouse(ctx, "taiga"), "irons_spellbooks:pedestal")) {
            CompoundTag components = saved.getCompound("heldItem").getCompound("components");
            ctx.check(saved.getCompound("heldItem").getString("id")).as("展示台物品").isEqualTo("irons_spellbooks:amethyst_rapier");
            ctx.check(components.getString("minecraft:custom_name")).as("细剑名字").contains("item.touhou_little_maid_spell.loot.wanderlust_rapier");
            ctx.check(components.getCompound("minecraft:enchantments").getCompound("levels").getInt("minecraft:sharpness"))
                    .as("细剑锋利等级").isEqualTo(2);
            ListTag spells = components.getCompound("irons_spellbooks:spell_container").getList("data", Tag.TAG_COMPOUND);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < spells.size(); i++) {
                ids.add(spells.getCompound(i).getString("id") + "@" + spells.getCompound(i).getInt("level"));
            }
            ctx.check(ids).as("细剑法术").isEqualTo(List.of("irons_spellbooks:summon_swords@2"));
        }
    }

    /** 平原房屋模板里的椅子不嵌在实心方块里，也不在同一位置重复。 */
    private static void chairsNotInBlocks(Stage ctx) {
        StructureStage.Placed placed = placeHouse(ctx, "plains");
        List<String> embedded = new ArrayList<>();
        List<String> positions = new ArrayList<>();
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (StructureTemplate.StructureEntityInfo info : piece.entities()) {
                if (!info.nbt.getString("id").equals(CHAIR)) {
                    continue;
                }
                BlockPos pos = BlockPos.containing(info.pos);
                positions.add(info.pos.toString());
                if (ctx.level().getBlockState(pos).isCollisionShapeFullBlock(ctx.level(), pos)) {
                    embedded.add(pos + " " + BuiltInRegistries.BLOCK.getKey(ctx.level().getBlockState(pos).getBlock()));
                }
            }
        }
        ctx.check(embedded).as("嵌在实心方块里的椅子").isEmpty();
        ctx.check(positions.stream().distinct().count()).as("椅子位置去重后的数量（共 " + positions.size() + " 把）")
                .isEqualTo((long) positions.size());
    }

    /** 只在对应联动模组在场时才会被选用的模板（几个模组互斥，不在场时它的方块本就未注册）；移植版的方块只有移植版有，以它内嵌的 danmaku_api 为准。 */
    private static final Map<String, String> TEMPLATE_REQUIRED_MOD = Map.of(
            Checks.NS + "fairy_maid_cafe/fairy_maid_cafe", "youkaishomecoming",
            Checks.NS + "fairy_maid_cafe/fairy_maid_cafe_yh_port", "danmaku_api",
            Checks.NS + "fairy_maid_cafe/fairy_maid_cafe_youkaisfeasts", "youkaisfeasts");

    /** 装了对应模组时改用别的模板：装了妖怪们的归家移植版就不用官方版的模板（其中有移植版没有的方块）。 */
    private static final Map<String, String> TEMPLATE_REPLACED_BY_MOD = Map.of(
            Checks.NS + "fairy_maid_cafe/fairy_maid_cafe", "danmaku_api");

    /** 当前模组组合下不会选用该模板的原因；会选用时为 null。 */
    private static String unusedTemplateReason(String template) {
        String requiredMod = TEMPLATE_REQUIRED_MOD.get(template);
        if (requiredMod != null && !ModList.get().isLoaded(requiredMod)) {
            return "没装 " + requiredMod + "，不会选用这个模板";
        }
        String replacedBy = TEMPLATE_REPLACED_BY_MOD.get(template);
        if (replacedBy != null && ModList.get().isLoaded(replacedBy)) {
            return "装了 " + replacedBy + "，改用别的模板";
        }
        return null;
    }

    /** 模板里的方块都来自已注册的方块（没有声明依赖的模组方块会变成空气）；列为可选装饰的模组没装时除外。 */
    private static void onlyRegisteredBlocks(Stage ctx) {
        List<String> missing = new ArrayList<>();
        for (String template : StructureSnapshots.TEMPLATES.keySet()) {
            if (unusedTemplateReason(template) != null) {
                continue;
            }
            ResourceLocation id = ResourceLocation.parse(template);
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
            try (var stream = ctx.server().getResourceManager().getResourceOrThrow(file).open()) {
                CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                List<ListTag> palettes = new ArrayList<>();
                palettes.add(nbt.getList("palette", Tag.TAG_COMPOUND));
                for (Tag alternative : nbt.getList("palettes", Tag.TAG_LIST)) palettes.add((ListTag) alternative);
                for (ListTag palette : palettes) {
                    for (Tag entry : palette) {
                        String block = ((CompoundTag) entry).getString("Name");
                        if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(block)) && !WorldExtract.absentOptional(block)) {
                            missing.add(template + ": " + block);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        ctx.check(missing).as("引用了未注册方块的模板").isEmpty();
    }

    /** 必须出现在模板女仆来源标记里的结构。 */
    private static final Set<String> REQUIRED_MAID_SOURCES = Set.of(Checks.NS + "elven_realm", Checks.NS + "fairy_maid_cafe",
            Checks.NS + "fallen_sanctum", Checks.NS + "relic_sanctum", MaidOriginData.HIDDEN_RETREAT_SOURCE);

    /** 各版咖啡厅模板都要带咖啡厅来源的女仆。 */
    private static final List<String> CAFE_TEMPLATES = List.of("structure/fairy_maid_cafe/fairy_maid_cafe.nbt",
            "structure/fairy_maid_cafe/fairy_maid_cafe_yh_port.nbt", "structure/fairy_maid_cafe/fairy_maid_cafe_youkaisfeasts.nbt");

    /**
     * 本模组每个模板里的女仆都带来源标记和预设效果快照；带永久效果的声明保留永久效果；
     * 隐世之境的等待女仆带隐世女仆标记且没有主人。
     */
    private static void templateMaidsDeclareOrigin(Stage ctx) {
        List<String> problems = new ArrayList<>();
        Set<String> sources = new TreeSet<>();
        Map<String, Set<String>> sourcesByFile = new TreeMap<>();
        var files = ctx.server().getResourceManager().listResources("structure",
                rl -> rl.getNamespace().equals(MaidSpellMod.MOD_ID) && rl.getPath().endsWith(".nbt"));
        for (var file : files.entrySet()) {
            String label = file.getKey().getPath();
            CompoundTag template;
            try (var stream = file.getValue().open()) {
                template = net.minecraft.nbt.NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            int index = 0;
            for (Tag element : template.getList("entities", Tag.TAG_COMPOUND)) {
                CompoundTag maid = ((CompoundTag) element).getCompound("nbt");
                if (!"touhou_little_maid:maid".equals(maid.getString("id"))) {
                    continue;
                }
                String where = label + " 第 " + index++ + " 个女仆";
                CompoundTag origin = MaidOriginData.getOrigin(maid);
                String source = origin.getString("Source");
                if (origin.getInt("Version") != MaidOriginData.ORIGIN_VERSION || source.isEmpty()) {
                    problems.add(where + " 来源标记不完整: " + origin);
                    continue;
                }
                sources.add(source);
                sourcesByFile.computeIfAbsent(label, key -> new TreeSet<>()).add(source);
                boolean permanent = maid.getList("active_effects", Tag.TAG_COMPOUND).stream()
                        .anyMatch(effect -> ((CompoundTag) effect).getInt("duration") == -1);
                if (permanent && !MaidOriginData.preservesPermanentEffects(maid)) {
                    problems.add(where + " 带永久效果却没声明保留");
                }
                if (!origin.contains(MaidOriginData.PRESET_EFFECTS, Tag.TAG_LIST)) {
                    problems.add(where + " 缺预设效果快照");
                } else if (!Reflect.<ListTag>call(null, MaidOriginData.class, "collectPresetEffects", new Class<?>[]{ListTag.class},
                        maid.getList("active_effects", Tag.TAG_COMPOUND)).equals(origin.get(MaidOriginData.PRESET_EFFECTS))) {
                    problems.add(where + " 预设效果快照与初始无限效果不一致");
                }
                if (MaidOriginData.HIDDEN_RETREAT_SOURCE.equals(source)) {
                    if (!maid.getCompound("NeoForgeData").getBoolean(MaidOriginData.HIDDEN_RETREAT_MAID)) {
                        problems.add(where + " 缺隐世女仆标记");
                    }
                    if (maid.contains("Owner")) {
                        problems.add(where + " 等待中的隐世女仆不应有主人");
                    }
                }
            }
        }
        ctx.record("来源", sources);
        ctx.check(problems).as("模板女仆的来源标记问题").isEmpty();
        ctx.check(sources.containsAll(REQUIRED_MAID_SOURCES)).as("来源覆盖 " + REQUIRED_MAID_SOURCES + "，实际 " + sources).isTrue();
        for (String cafe : CAFE_TEMPLATES) {
            ctx.check(sourcesByFile.getOrDefault(cafe, Set.of())).as(cafe + " 里女仆的来源").isEqualTo(Set.of(Checks.NS + "fairy_maid_cafe"));
        }
    }

    /** 本模组的全部结构模板，跳过所需联动模组不在场的替代模板。 */
    private static Map<String, CompoundTag> loadedTemplates(Stage ctx) {
        Map<String, CompoundTag> templates = new TreeMap<>();
        var files = ctx.server().getResourceManager().listResources("structure",
                rl -> rl.getNamespace().equals(MaidSpellMod.MOD_ID) && rl.getPath().endsWith(".nbt"));
        for (var file : files.entrySet()) {
            String path = file.getKey().getPath();
            String id = Checks.NS + path.substring("structure/".length(), path.length() - ".nbt".length());
            if (unusedTemplateReason(id) != null) {
                continue;
            }
            try (var stream = file.getValue().open()) {
                templates.put(id, net.minecraft.nbt.NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap()));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return templates;
    }

    /** 模板里物品框、容器、实体背包等处的物品都已注册（没有声明依赖的模组物品读档时会被丢掉）。 */
    private static void onlyRegisteredItems(Stage ctx) {
        Set<String> missing = new TreeSet<>();
        loadedTemplates(ctx).forEach((id, template) -> {
            for (String list : List.of("entities", "blocks")) {
                for (Tag element : template.getList(list, Tag.TAG_COMPOUND)) {
                    collectUnknownItems(id, ((CompoundTag) element).getCompound("nbt"), missing);
                }
            }
        });
        ctx.check(missing).as("引用了未注册物品的模板").isEmpty();
    }

    /** 带字符串 id 和数量的复合标签按物品堆叠处理（1.21 的 count 或旧格式的 Count）。 */
    private static void collectUnknownItems(String template, Tag tag, Set<String> missing) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING)
                    && (compound.contains("count", Tag.TAG_ANY_NUMERIC) || compound.contains("Count", Tag.TAG_ANY_NUMERIC))) {
                ResourceLocation item = ResourceLocation.tryParse(compound.getString("id"));
                if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) {
                    missing.add(template + ": " + compound.getString("id"));
                }
            }
            for (String key : compound.getAllKeys()) {
                collectUnknownItems(template, compound.get(key), missing);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) {
                collectUnknownItems(template, element, missing);
            }
        }
    }
}
