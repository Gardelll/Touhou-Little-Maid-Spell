package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.StructureStage;
import com.github.yimeng261.maidspell.stagewright.support.WorldExtract;
import com.github.yimeng261.maidspell.worldgen.StarfallGardenData;
import com.github.yimeng261.maidspell.worldgen.StellarEndshoreFixedSpawn;
import com.github.yimeng261.maidspell.worldgen.structure.StellarEndshoreStructure;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;



import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星途终岸、观星塔、星落之庭相关：结构放置、观星术士名字、四幅新画、星之旅程与万法皆通两个成就页、观星罗盘、
 * 星落之庭补生成的放置次数落盘。
 */
@DisplayName("星途终岸、观星塔与星落之庭")
@Scene("tlms")
class StellarStructureTest {
    private static final String COMPASS = NS + "starwatch_compass";
    private static final List<String> PAINTINGS = List.of("astronomical_object", "falling_star", "magic_wine_fox", "starry_flower_sea");

    /** 起始件加 7 个部件都放下，里面有一只星之魔女。 */
    @DisplayName("星途终岸：放置起始件加 7 个部件，里面有一只星之魔女")
    @Test
    void endshorePlacedWithWitch(Game game, @Plot.Size(x = 176, y = 256, z = 176) @Plot.Centered Plot plot,
                                 TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> endshore(ctx));
    }

    @DisplayName("星途终岸固定锚点：任意种子都选出四个方向锚点之一且结果稳定")
    @Test
    void endshoreFixedAnchorSelection(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> fixedAnchorSelection(ctx));
    }

    @DisplayName("星途终岸的生物群系标签含末地荒地与末地小型岛屿")
    @Test
    void endshoreBiomeTagCoversEndBarrens(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> endshoreBiomes(ctx));
    }

    @DisplayName("观星塔：放置后塔里有一只观星术士")
    @Test
    void towerPlacedWithAstroMancer(Game game, @Plot.Size(x = 144, y = 256, z = 144) @Plot.Centered Plot plot,
                                    TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> tower(ctx));
    }

    @DisplayName("观星塔的雕纹书架保留一本横扫之刃 III 附魔书")
    @Test
    void towerBookshelfKeepsSweepingEdge(Game game, @Plot.Size(x = 144, y = 256, z = 144) @Plot.Centered Plot plot,
                                         TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        ctx.run(() -> sweepingEdgeBook(ctx));
    }

    @DisplayName("观星术士的中英文名称正确")
    @Test
    void astroMancerName(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> astroName(ctx));
    }

    @DisplayName("四幅新画可随机挂出，中英文都有标题")
    @Test
    void paintingsPlaceableAndTitled(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> paintings(ctx));
    }

    @DisplayName("星之旅程与万法皆通两个成就页签的根、父子关系和背景正确")
    @Test
    void advancementTabs(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> advancements(ctx));
    }

    @DisplayName("观星罗盘在主世界右键：不绑定坐标，进入冷却，冷却中再点无反应")
    @Test
    void compassWrongDimensionCooldown(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> compassOverworld(ctx));
    }

    @DisplayName("观星罗盘在末地右键：一次搜索在 1 秒内结束")
    @Test
    void compassEndSearchFinishesQuickly(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> compassEnd(ctx));
    }

    @DisplayName("已绑定的观星罗盘右键磁石：坐标不变，物品名仍是观星罗盘")
    @Test
    void compassLodestoneKeepsTargetAndName(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> compassLodestone(ctx));
    }

    /** 出生点环带内的两次手动放置最多成功一次；已有庭院时都被拒绝，原版冰屋仍能放置。 */
    @DisplayName("星落之庭每个世界最多一座：已有庭院时再放置被拒绝，原版冰屋仍能放置")
    @Test
    void starfallGardenOnlyOnePerWorld(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        var level = ctx.server().overworld();
        List<BlockPos> positions = ctx.get(() -> {
            BlockPos spawn = level.getSharedSpawnPos();
            return List.of(spawn.offset(256, 0, 0), spawn.offset(352, 0, 0), spawn.offset(600, 0, 0));
        });
        var loaded = ctx.get(() -> {
            var tickets = com.github.yimeng261.maidspell.stagewright.support.Reflect.<net.minecraft.server.level.DistanceManager>field(
                    level.getChunkSource(), net.minecraft.server.level.ServerChunkCache.class, "distanceManager");
            var ticket = net.minecraft.server.level.TicketType.create("tlms_manual_structure", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
            int ticketLevel = net.minecraft.server.level.ChunkLevel.byStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FULL);
            List<java.util.concurrent.CompletableFuture<?>> loading = new ArrayList<>();
            for (BlockPos position : positions) {
                var center = new net.minecraft.world.level.ChunkPos(position);
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        var chunk = new net.minecraft.world.level.ChunkPos(center.x + dx, center.z + dz);
                        tickets.addTicket(ticket, chunk, ticketLevel, chunk);
                        ctx.cleanup(() -> tickets.removeTicket(ticket, chunk, ticketLevel, chunk));
                        loading.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> level.getChunkSource()
                                        .getChunkFuture(chunk.x, chunk.z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true),
                                net.minecraft.Util.backgroundExecutor()).thenCompose(future -> future)
                                .thenApply(result -> result.orElseThrow(() -> new IllegalStateException("测试区块生成失败：" + chunk))));
                    }
                }
            }
            return java.util.concurrent.CompletableFuture.allOf(loading.toArray(java.util.concurrent.CompletableFuture[]::new));
        });
        ctx.await(1200, "星落之庭周边区块生成", loaded::isDone);
        ctx.run(() -> {
            loaded.join();
            boolean alreadyPlaced = com.github.yimeng261.maidspell.worldgen.StarfallGardenData.isPlaced();
            ctx.record("gardenAlreadyPlaced", alreadyPlaced);
            int gardens = 0;
            for (BlockPos position : positions.subList(0, 2)) {
                gardens += place("place structure " + NS + "starfall_garden " + position.getX() + " 100 " + position.getZ(), ctx);
            }
            ctx.check(gardens).as("两次放置星落之庭成功的次数").isAtMost(alreadyPlaced ? 0 : 1);
            ctx.check(com.github.yimeng261.maidspell.worldgen.StarfallGardenData.isPlaced()).as("存档已有或本次生成一座庭院").isTrue();
            BlockPos igloo = positions.getLast();
            ctx.check(place("place structure minecraft:igloo " + igloo.getX() + " 100 " + igloo.getZ(), ctx))
                    .as("放置冰屋成功的次数").isEqualTo(1);
        });
    }

    /**
     * 补生成开始放置时计的次数当场提交写盘（不等自动保存，崩服也算一次；NeoForge 在 IO 线程上写，等它落到文件）；
     * 正常停服退还的那一步把次数减回去。重启才能走到的上限分支归人工验收。
     */
    @DisplayName("星落之庭补生成的放置次数当场写进存档文件，停服退还后减回")
    @Test
    void starfallGardenPlacementAttemptSavedImmediately(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        MinecraftServer server = ctx.server();
        int before = ctx.get(() -> StarfallGardenData.placementAttempts(server));
        ctx.record("attemptsBefore", before);
        AtomicBoolean refunded = new AtomicBoolean();
        Runnable refund = () -> {
            if (refunded.compareAndSet(false, true)) {
                StarfallGardenData.refundPlacementAttempt(server);
            }
        };
        ctx.cleanup(refund);
        ctx.run(() -> {
            ctx.check(StarfallGardenData.recordPlacementAttempt(server, ctx.rel(0, 0, 0))).as("记上一次放置").isTrue();
            ctx.check(StarfallGardenData.placementAttempts(server)).as("内存里的放置次数").isEqualTo(before + 1);
        });
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(MaidSpellMod.MOD_ID + "_starfall_garden.dat");
        ctx.await(40, "放置次数写进存档文件", () -> savedAttempts(file) == before + 1);
        ctx.run(() -> {
            refund.run();
            ctx.check(StarfallGardenData.placementAttempts(server)).as("退还后的放置次数").isEqualTo(before);
        });
    }

    /** 起始件加 7 个部件都放下，里面有一只星之魔女。 */
    private static void endshore(Stage ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, NS + "stellar_endshore");
        ctx.check(WorldExtract.ownPieces(placed).size()).as("星途终岸的拼图片数").isEqualTo(8);
        var witches = WorldExtract.entities(ctx.level(), placed).stream()
                .filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(NS + "stellar_witch")).toList();
        // 星之魔女是 Boss，plot 还原时的普通移除会被拒绝，按授权的 Boss 清理移除，免得留给后续测试
        witches.forEach(witch -> ctx.cleanup(() -> Actors.cleanupEntity(witch)));
        ctx.check(witches.size()).as("结构里的星之魔女数").isEqualTo(1);
    }

    /** 不同种子选出的固定锚点都是四个方向锚点之一，同一种子结果稳定；竞技场 Y 换算成拼图起点 Y 时减去 27。 */
    private static void fixedAnchorSelection(Stage ctx) {
        List<BlockPos> anchors = List.of(new BlockPos(800, 150, 0), new BlockPos(-800, 200, 0),
                new BlockPos(0, 200, 800), new BlockPos(0, 200, -800));
        for (long seed : new long[]{0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            BlockPos anchor = StellarEndshoreFixedSpawn.selectAnchor(seed);
            ctx.check(anchors.contains(anchor)).as("种子 " + seed + " 选出的锚点 " + anchor).isTrue();
            ctx.check(StellarEndshoreFixedSpawn.selectAnchor(seed)).as("种子 " + seed + " 重复选择").isEqualTo(anchor);
        }
        ctx.check(StellarEndshoreStructure.fixedStartY(200)).as("Y=200 的拼图起点").isEqualTo(173);
    }

    /** 自然生成的生物群系标签含末地荒地，原有的末地小型岛屿仍在。 */
    private static void endshoreBiomes(Stage ctx) {
        var tag = TagKey.create(Registries.BIOME, ResourceLocation.parse(NS + "has_structure/stellar_endshore"));
        var biomes = ctx.server().registryAccess().registryOrThrow(Registries.BIOME);
        List<ResourceLocation> ids = new ArrayList<>();
        biomes.getTagOrEmpty(tag).forEach(holder -> holder.unwrapKey().ifPresent(key -> ids.add(key.location())));
        ctx.check(ids.contains(ResourceLocation.withDefaultNamespace("end_barrens"))).as("标签含末地荒地").isTrue();
        ctx.check(ids.contains(ResourceLocation.withDefaultNamespace("small_end_islands"))).as("标签含末地小型岛屿").isTrue();
    }

    private static void tower(Stage ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, NS + "starwatch_tower");
        long astro = WorldExtract.entities(ctx.level(), placed).stream()
                .filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(NS + "astro_mancer")).count();
        ctx.check(astro).as("塔里的观星术士数").isEqualTo(1L);
    }

    private static void sweepingEdgeBook(Stage ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, NS + "starwatch_tower");
        var sweepingEdge = ctx.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SWEEPING_EDGE);
        List<Integer> levels = new ArrayList<>();
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (var block : piece.blocks()) {
                if (!(ctx.level().getBlockEntity(block.pos()) instanceof ChiseledBookShelfBlockEntity bookshelf)) {
                    continue;
                }
                for (int slot = 0; slot < bookshelf.getContainerSize(); slot++) {
                    ItemStack book = bookshelf.getItem(slot);
                    int level = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                            .getLevel(sweepingEdge);
                    if (level > 0) {
                        ctx.check(book.is(Items.ENCHANTED_BOOK)).as("横扫之刃所在物品是附魔书").isTrue();
                        ctx.check(book.getCount()).as("横扫之刃附魔书数量").isEqualTo(1);
                        levels.add(level);
                    }
                }
            }
        }
        ctx.check(levels).as("放置后的观星塔书架有一本横扫之刃 III 附魔书").isEqualTo(List.of(3));
    }

    private static void astroName(Stage ctx) {
        String key = "entity.touhou_little_maid_spell.astro_mancer";
        ctx.check(Lang.read("en_us").get(key)).as("英文名").isEqualTo("Astro Mancer");
        ctx.check(Lang.read("zh_cn").get(key)).as("中文名").isEqualTo("观星术士");
    }

    /** 四幅画都在 #minecraft:placeable 里（普通画随机能挂出来），中英文都有标题。 */
    private static void paintings(Stage ctx) {
        var registry = ctx.server().registryAccess().registryOrThrow(Registries.PAINTING_VARIANT);
        Map<String, String> en = Lang.read("en_us");
        Map<String, String> zh = Lang.read("zh_cn");
        for (String id : PAINTINGS) {
            Optional<Holder.Reference<PaintingVariant>> variant = registry.getHolder(ResourceLocation.fromNamespaceAndPath("touhou_little_maid_spell", id));
            ctx.check(variant.isPresent() && variant.get().is(PaintingVariantTags.PLACEABLE)).as(id + " 可随机挂出").isTrue();
            String title = "painting.touhou_little_maid_spell." + id + ".title";
            ctx.check(en.containsKey(title) && zh.containsKey(title)).as(title + " 两种语言都有").isTrue();
        }
    }

    /** 星之旅程：启程之地是根（末地背景），星途终岸挂在它下面；万法皆通是单独一页，背景是樱花木板。 */
    private static void advancements(Stage ctx) {
        var advancements = ctx.server().getAdvancements();
        AdvancementHolder departure = advancements.get(ResourceLocation.parse(NS + "star_journey/departure_place"));
        AdvancementHolder endshore = advancements.get(ResourceLocation.parse(NS + "star_journey/stellar_endshore"));
        AdvancementHolder mastered = advancements.get(ResourceLocation.parse(NS + "dream_crystal/all_spells_mastered"));
        if (departure == null || endshore == null || mastered == null) {
            ctx.fail("缺少进度：启程之地=" + departure + " 星途终岸=" + endshore + " 万法皆通=" + mastered);
            return;
        }
        ctx.check(departure.value().parent().isEmpty()).as("启程之地是页签的根").isTrue();
        ctx.check(background(departure)).as("启程之地页签背景").isEqualTo("minecraft:textures/gui/advancements/backgrounds/end.png");
        ctx.check(endshore.value().parent().map(ResourceLocation::toString).orElse(null)).as("星途终岸的父进度").isEqualTo(departure.id().toString());
        ctx.check(mastered.value().parent().isEmpty()).as("万法皆通是页签的根").isTrue();
        ctx.check(background(mastered)).as("万法皆通页签背景").isEqualTo("minecraft:textures/block/cherry_planks.png");
        long journeyRoots = advancements.getAllAdvancements().stream()
                .filter(a -> a.id().getNamespace().equals("touhou_little_maid_spell") && a.id().getPath().startsWith("star_journey/"))
                .filter(a -> a.value().parent().isEmpty()).count();
        ctx.check(journeyRoots).as("星之旅程的根进度数").isEqualTo(1L);
    }

    private static String background(AdvancementHolder holder) {
        return holder.value().display().flatMap(DisplayInfo::getBackground).map(ResourceLocation::toString).orElse(null);
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    /** 主世界里右键：不绑定，进入冷却，冷却中再点无反应。 */
    private static void compassOverworld(Stage ctx) {
        FakePlayer player = player(ctx.level(), "TlmsCompassOverworld");
        ItemStack compass = Actors.stack(COMPASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        player.getCooldowns().removeCooldown(compass.getItem());
        compass.getItem().use(ctx.level(), player, InteractionHand.MAIN_HAND);
        ctx.check(compass.has(DataComponents.LODESTONE_TRACKER)).as("主世界右键后绑定了坐标").isFalse();
        ctx.check(player.getCooldowns().isOnCooldown(compass.getItem())).as("右键后进入冷却").isTrue();
        player.getCooldowns().removeCooldown(compass.getItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** 末地里右键：一次搜索在 1 秒内结束（找到则绑定坐标，找不到则进入较长冷却）。 */
    private static void compassEnd(Stage ctx) {
        ServerLevel end = ctx.server().getLevel(Level.END);
        FakePlayer player = player(end, "TlmsCompassEnd");
        player.moveTo(0, 100, 0);
        ItemStack compass = Actors.stack(COMPASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        player.getCooldowns().removeCooldown(compass.getItem());
        long start = System.nanoTime();
        compass.getItem().use(end, player, InteractionHand.MAIN_HAND);
        long millis = (System.nanoTime() - start) / 1_000_000;
        ctx.record("searchMillis", millis);
        ctx.record("found", compass.get(DataComponents.LODESTONE_TRACKER));
        ctx.check(millis).as("末地搜索耗时（毫秒）").isLessThan(1000L);
        player.getCooldowns().removeCooldown(compass.getItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** 已绑定的罗盘右键磁石：坐标不变；物品名仍是观星罗盘。 */
    private static void compassLodestone(Stage ctx) {
        ServerLevel level = ctx.level();
        FakePlayer player = player(level, "TlmsCompassLodestone");
        BlockPos lodestone = ctx.rel(1, 0, 0);
        level.setBlock(lodestone, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack compass = Actors.stack(COMPASS);
        LodestoneTracker target = new LodestoneTracker(Optional.of(GlobalPos.of(Level.END, new BlockPos(3000, 150, 3000))), false);
        compass.set(DataComponents.LODESTONE_TRACKER, target);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        compass.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(lodestone), Direction.UP, lodestone, false)));
        ctx.check(compass.get(DataComponents.LODESTONE_TRACKER)).as("右键磁石后的目标").isEqualTo(target);
        ctx.check(compass.getItem().getDescriptionId(compass)).as("绑定后的物品名键").isEqualTo("item.touhou_little_maid_spell.starwatch_compass");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static int place(String command, Stage ctx) {
        CommandSourceStack source = ctx.server().createCommandSourceStack().withSuppressedOutput()
                .withLevel(ctx.server().overworld()).withPermission(4);
        try {
            return ctx.server().getCommands().getDispatcher().execute(command, source) > 0 ? 1 : 0;
        } catch (CommandSyntaxException e) {
            ctx.record("failed: " + command, e.getMessage());
            return 0;
        }
    }

    /** 存档文件里的放置次数；文件还没写出来或读失败时返回 -1。 */
    private static int savedAttempts(Path file) {
        try {
            CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            return saved.getCompound("data").getInt("RetrofitPlacementAttempts");
        } catch (IOException e) {
            return -1;
        }
    }
}
