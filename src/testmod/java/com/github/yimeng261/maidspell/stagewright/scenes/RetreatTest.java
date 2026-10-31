package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.Found;
import com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.Search;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Batch;
import com.github.yimeng261.maidspell.stagewright.support.ChatTap;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.mojang.authlib.GameProfile;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.BELLS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.BELL_KEY;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.ENTERED;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.ENTER_TICKS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.HIDDEN_RETREAT;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.HIDDEN_RETREAT_SET;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.NOTICE_TICKS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.RETURNED;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.WAITING;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.cancelSearches;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.enterFromOverworld;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.enterRetreat;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.found;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.holdBells;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.locate;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.mainHandCount;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.profile;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.search;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.walker;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.whenReady;

/**
 * 寻风之铃与归隐之地（集成服，私人维度模式，宿主玩家实际收到的提示经 {@link ChatTap} 核对）：
 * 进入/返回、搜索隐世之境（等待提示、坐标消息与点击填入的传送命令、飞铃、只消耗一个）、
 * 每个私人维度最多一个隐世之境、重复查看、缓存丢失后重新搜索、驯服结构女仆的提示。
 * <p>测试按 {@link Order} 顺序运行并共享宿主玩家的私人维度：第一次进入时生成的隐世之境会被后续测试复用。
 */
@Scene("tlms.integrated")
@ResourceLock(RetreatFixtures.HOST_PLAYER)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RetreatTest {
    private static final String FIRST_USE = BELL_KEY + "first_use";
    private static final String NO_STRUCTURE = BELL_KEY + "no_structure";
    private static final String TAMED_IN_RETREAT = "item.touhou_little_maid_spell.maid_tamed_event.maid_in_hidden_retreat";
    /** 检查"每个维度最多一个"时，找到的结构周围取几圈放置网格。 */
    private static final int OTHER_CANDIDATE_RINGS = 2;
    /** 远距离进入的缺陷测试里，等搜索出结果的上限。 */
    private static final int FAR_SEARCH_TICKS = 1500;

    /** 主世界右键进入自己的私人归隐之地并提示；Shift+右键返回主世界并提示；两次都不消耗铃。 */
    @Test
    @Order(1)
    @Requires(Requires.Capability.CLIENT)
    void enterAndReturn(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        long mark = enterFromOverworld(ctx, host);
        ctx.await(NOTICE_TICKS, "收到进入提示", () -> ChatTap.find(mark, ENTERED, true).isPresent());
        BlockPos inRetreat = ctx.get(() -> {
            ctx.check(host.level().dimension()).as("进入的维度")
                    .isEqualTo(TheRetreatDimension.getPlayerRetreatDimension(host.getUUID()));
            ctx.check(host.getMainHandItem().getCount()).as("进入后手上铃的数量").isEqualTo(BELLS);
            return host.blockPosition();
        });
        whenReady(ctx, host);
        long back = ChatTap.mark();
        ctx.run(() -> {
            host.setShiftKeyDown(true);
            Players.use(host);
            host.setShiftKeyDown(false);
        });
        ctx.await(200, "返回主世界并收到提示",
                () -> host.level().dimension() == Level.OVERWORLD && ChatTap.find(back, RETURNED, true).isPresent());
        ctx.run(() -> {
            ctx.check(host.getMainHandItem().getCount()).as("返回后手上铃的数量").isEqualTo(BELLS);
            double horizontal = Math.hypot(host.getX() - inRetreat.getX() - 0.5, host.getZ() - inRetreat.getZ() - 0.5);
            ctx.record("returnHorizontalOffset", horizontal);
            ctx.check(horizontal).as("返回主世界的落点与归隐之地里所站位置的水平距离").isAtMost(2.0);
        });
    }

    /**
     * 归隐之地右键：先在动作栏提示等待，找到后聊天栏给出坐标、距离（点击填入 /tp 命令）、搜索耗时与首次使用说明；
     * 铃飞出、只消耗一个；坐标所在区块确有隐世之境起点，周围其它候选区块没有第二个。
     */
    @Test
    @Order(2)
    @Requires(Requires.Capability.CLIENT)
    void searchFindsHiddenRetreat(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        BlockPos from = ctx.get(() -> {
            RetreatManager.clearPlayerCache(host.getUUID());
            return host.blockPosition();
        });
        Search result = search(ctx, host);
        long mark = result.mark();
        Found found = found(ctx, mark);
        ctx.check(ChatTap.keys(mark, true).contains(WAITING))
                .as("动作栏提示过等待搜索（实际：" + ChatTap.keys(mark, true) + "）").isTrue();
        ctx.check(ChatTap.find(mark, FIRST_USE, false).isPresent()).as("收到首次使用说明").isTrue();
        ctx.check(result.flyingBells().size()).as("飞出的寻风之铃实体数").isEqualTo(1);
        ctx.check(mainHandCount(ctx, host)).as("搜索后手上铃的数量").isEqualTo(BELLS - 1);
        if (found == null) {
            return;
        }
        int distance = (int) Math.sqrt(Math.pow(found.x() - from.getX(), 2) + Math.pow(found.z() - from.getZ(), 2));
        ctx.check(found.distance()).as("消息里的距离").isEqualTo(distance);
        checkTpCommand(ctx, found);
        checkOnlyOne(ctx, ctx.get(host::serverLevel), found);
    }

    /** 已找到后再次右键：直接给出同一坐标。 */
    @Test
    @Order(3)
    @Requires(Requires.Capability.CLIENT)
    void reshowSameCoordinates(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        Found first = locate(ctx, host);
        if (first == null) {
            return;
        }
        int afterFirst = mainHandCount(ctx, host);
        int started = ctx.tick();
        Search result = search(ctx, host);
        Found again = found(ctx, result.mark());
        ctx.record("reshowTicks", ctx.tick() - started);
        ctx.record("bellsConsumedByReshow", afterFirst - mainHandCount(ctx, host));
        ctx.check(ChatTap.keys(result.mark(), true).contains(WAITING)).as("重复查看时又进入等待搜索").isFalse();
        if (again != null) {
            ctx.check(again.x() + "," + again.z()).as("重复查看给出的坐标").isEqualTo(first.x() + "," + first.z());
        }
    }

    /**
     * 内存里的查找缓存丢失（服务器重启即如此）后重新搜索，仍应找到本维度里已生成的那一个隐世之境。
     * 宿主玩家从主世界原点附近进入，结构生成在进入点附近。
     */
    @Test
    @Order(4)
    @Requires(Requires.Capability.CLIENT)
    void researchAfterCacheLoss(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        Found first = locate(ctx, host);
        if (first == null) {
            return;
        }
        ctx.run(() -> RetreatManager.clearPlayerCache(host.getUUID()));
        Search result = search(ctx, host);
        ctx.check(ChatTap.find(result.mark(), NO_STRUCTURE, true).isPresent()).as("提示附近没有隐世之境").isFalse();
        Found again = found(ctx, result.mark());
        if (again != null) {
            ctx.check(again.x() + "," + again.z()).as("缓存丢失后重新搜索给出的坐标").isEqualTo(first.x() + "," + first.z());
        }
    }

    /**
     * 驯服隐世之境里坐着的结构女仆时收到"您的手艺，一如既往呢"；在结构外驯服坐着的女仆不会收到。
     */
    @Test
    @Order(5)
    @Requires(Requires.Capability.CLIENT)
    void tameMessage(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        EntityMaid outside = ctx.get(() -> Actors.sittingMaid(ctx, 2, 0, 0));
        long controlMark = ChatTap.mark();
        ctx.check(ctx.get(() -> Players.tame(host, outside))).as("驯服结构外的女仆").isTrue();
        ctx.ticks(10);
        ctx.check(ChatTap.find(controlMark, TAMED_IN_RETREAT, false).isPresent()).as("在结构外驯服坐着的女仆收到隐世之境提示").isFalse();
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        Found found = locate(ctx, host);
        if (found == null) {
            return;
        }
        ServerLevel level = ctx.get(host::serverLevel);
        ChunkPos chunk = new ChunkPos(new BlockPos(found.x(), 0, found.z()));
        CompletableFuture<StructureStart> pending = ctx.get(() -> {
            Structure structure = Worldgen.structure(level, HIDDEN_RETREAT).value();
            return Worldgen.startAt(level, structure, chunk);
        });
        ctx.await(600, "隐世之境起点生成", pending::isDone);
        StructureStart start = pending.exceptionally(error -> null).join();
        ctx.check(start != null).as("找到的坐标所在区块 " + chunk + " 有隐世之境起点").isTrue();
        if (start == null) {
            return;
        }
        BoundingBox box = start.getBoundingBox();
        AABB area = AABB.of(box);
        // 飞到结构上空等区块生成出结构女仆，避免落到屋顶上摔伤
        ctx.run(() -> {
            boolean flying = host.getAbilities().flying;
            boolean mayfly = host.getAbilities().mayfly;
            ctx.cleanup(() -> {
                host.getAbilities().flying = flying;
                host.getAbilities().mayfly = mayfly;
                host.onUpdateAbilities();
            });
            host.getAbilities().mayfly = true;
            host.getAbilities().flying = true;
            host.onUpdateAbilities();
            host.teleportTo(level, box.getCenter().getX() + 0.5, box.maxY() + 2, box.getCenter().getZ() + 0.5, Set.of(), 0, 0);
        });
        ctx.await(1200, "结构女仆生成", () -> !structureMaids(level, area).isEmpty());
        EntityMaid maid = ctx.get(() -> structureMaids(level, area).getFirst());
        ctx.run(() -> {
            ctx.record("structureMaid", maid.blockPosition().toShortString());
            ctx.check(maid.isOrderedToSit()).as("结构女仆坐着").isTrue();
        });
        long mark = ChatTap.mark();
        ctx.check(ctx.get(() -> Players.tame(host, maid))).as("驯服结构女仆").isTrue();
        int at = ctx.tick();
        ctx.await(60, "收到驯服提示或等满 40 tick",
                () -> ChatTap.find(mark, TAMED_IN_RETREAT, false).isPresent() || ctx.tick() >= at + 40);
        ctx.check(ChatTap.find(mark, TAMED_IN_RETREAT, false).isPresent())
                .as("驯服结构女仆后收到\"您的手艺，一如既往呢\"（实际聊天：" + ChatTap.keys(mark, false) + "）")
                .isTrue();
    }

    /**
     * 远离原点进入私人归隐之地：隐世之境随落点附近的区块生成而生成，之后第一次用铃搜索应能找到它。
     * 用一个全新 UUID 的私人维度和 NeoForge 模拟玩家（直接放在该维度的远处落点）走真实的右键流程；
     * 模拟玩家收不到消息，结果从服务器的查找缓存读取。
     */
    @Test
    @Order(6)
    void searchFindsStructureAtFarEntry(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        GameProfile profile = profile("TlmsFarEntry");
        CompletableFuture<ServerLevel> created = ctx.get(
                () -> PlayerRetreatManager.getOrCreatePlayerRetreatAsync(ctx.server(), profile.getId()));
        ctx.await(ENTER_TICKS, "私人归隐之地创建完成", created::isDone);
        ServerLevel level = created.join();
        ctx.cleanup(() -> cancelSearches(level));
        // 落点与主世界场地同坐标
        BlockPos entryPos = new BlockPos(ctx.originX(), 100, ctx.originZ());
        ChunkPos entry = new ChunkPos(entryPos);
        ctx.record("entry", entryPos.toShortString());
        Structure structure = ctx.get(() -> Worldgen.structure(level, HIDDEN_RETREAT).value());
        // 与真实玩家到达时一样：落点周围的候选区块推进到 STRUCTURE_STARTS，第一个成功的就是本维度唯一的隐世之境
        List<ChunkPos> around = ctx.get(
                () -> Worldgen.candidates(level, Worldgen.structureSet(level, HIDDEN_RETREAT_SET).value(), entry, 8));
        List<ChunkPos> generated = Collections.synchronizedList(new ArrayList<>());
        Batch.runAsync(ctx, around, chunk -> Worldgen.startAt(level, structure, chunk).thenApply(start -> {
            if (start != null) {
                generated.add(start.getChunkPos());
            }
            return start != null;
        }), 600);
        ctx.record("generatedNearEntry", generated.toString());
        ctx.check(generated.size()).as("落点附近生成的隐世之境数").isEqualTo(1);
        if (generated.size() != 1) {
            return;
        }
        ctx.run(() -> {
            FakePlayer walker = walker(level, profile, entryPos);
            RetreatManager.clearPlayerCache(profile.getId());
            Players.use(walker);
        });
        int started = ctx.tick();
        ctx.await(FAR_SEARCH_TICKS + 10, "搜索出结果",
                () -> RetreatManager.checkCache(level, profile.getId()).hasCache || ctx.tick() >= started + FAR_SEARCH_TICKS);
        ctx.run(() -> {
            RetreatManager.CacheResult result = RetreatManager.checkCache(level, profile.getId());
            ctx.record("searchTicks", ctx.tick() - started);
            String got = !result.hasCache ? "<" + FAR_SEARCH_TICKS + " tick 内没有结果>"
                    : result.position == null ? "<没有找到>" : new ChunkPos(result.position).toString();
            ctx.check(got).as("搜索结果所在区块").isEqualTo(generated.getFirst().toString());
        });
    }

    // ---- 工具 ----

    /** 坐标消息可点击，填入的 /tp 命令指向结构坐标上方 3 格，并有悬停提示。 */
    private static void checkTpCommand(Stage ctx, Found found) {
        ClickEvent click = found.message().message().getStyle().getClickEvent();
        HoverEvent hover = found.message().message().getStyle().getHoverEvent();
        ctx.check(hover != null).as("坐标消息有悬停提示").isTrue();
        ctx.check(click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND).as("坐标消息点击后填入命令").isTrue();
        if (click == null) {
            return;
        }
        ctx.record("tpCommand", click.getValue());
        String[] parts = click.getValue().split(" ");
        ctx.check(parts.length).as("填入命令的段数（" + click.getValue() + "）").isEqualTo(4);
        if (parts.length != 4) {
            return;
        }
        ctx.check(parts[0]).as("填入的命令").isEqualTo("/tp");
        try {
            ctx.check(Double.parseDouble(parts[1])).as("/tp 的 x").isCloseTo(found.x() + 0.5, 1e-6);
            ctx.check(Double.parseDouble(parts[2])).as("/tp 的 y").isCloseTo(found.height() + 3, 1e-6);
            ctx.check(Double.parseDouble(parts[3])).as("/tp 的 z").isCloseTo(found.z() + 0.5, 1e-6);
        } catch (NumberFormatException e) {
            ctx.check(click.getValue()).as("/tp 命令里的坐标不是合法数字").isEqualTo("<合法坐标>");
        }
    }

    /** 坐标所在区块有隐世之境起点；周围几圈放置网格的其它候选区块没有第二个（测试线程）。 */
    private static void checkOnlyOne(Stage ctx, ServerLevel level, Found found) {
        ChunkPos foundChunk = new ChunkPos(new BlockPos(found.x(), 0, found.z()));
        Structure structure = ctx.get(() -> Worldgen.structure(level, HIDDEN_RETREAT).value());
        List<ChunkPos> chunks = new ArrayList<>();
        chunks.add(foundChunk);
        ctx.get(() -> Worldgen.candidates(level, Worldgen.structureSet(level, HIDDEN_RETREAT_SET).value(), foundChunk,
                OTHER_CANDIDATE_RINGS)).stream().filter(chunk -> !chunk.equals(foundChunk)).forEach(chunks::add);
        Map<ChunkPos, Boolean> hasStart = Collections.synchronizedMap(new LinkedHashMap<>());
        Batch.runAsync(ctx, chunks, chunk -> Worldgen.startAt(level, structure, chunk).thenApply(start -> {
            hasStart.put(chunk, start != null);
            return false;
        }), 1200);
        ctx.record("otherCandidates", chunks.size() - 1);
        ctx.check(hasStart.get(foundChunk)).as("坐标所在区块 " + foundChunk + " 有隐世之境起点").isEqualTo(Boolean.TRUE);
        List<String> others;
        synchronized (hasStart) {
            others = hasStart.entrySet().stream()
                    .filter(e -> !e.getKey().equals(foundChunk) && e.getValue()).map(e -> e.getKey().toString()).toList();
        }
        ctx.check(others).as("同一私人维度里的其它隐世之境").isEmpty();
    }

    private static List<EntityMaid> structureMaids(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(EntityMaid.class, area, maid -> maid.getOwnerUUID() == null);
    }
}
