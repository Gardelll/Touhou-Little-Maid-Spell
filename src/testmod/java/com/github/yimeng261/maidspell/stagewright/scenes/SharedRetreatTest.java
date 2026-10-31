package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.client.ClientSide;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatDimensionData;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.Found;
import com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.Search;
import com.github.yimeng261.maidspell.stagewright.support.Batch;
import com.github.yimeng261.maidspell.stagewright.support.ChatTap;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.BELLS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.BELL_KEY;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.ENTERED;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.ENTER_TICKS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.HIDDEN_RETREAT;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.NOTICE_TICKS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.SEARCH_TICKS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.WAITING;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.cancelSearches;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.enterFromOverworld;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.enterRetreat;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.found;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.holdBells;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.mainHandCount;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.profile;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.search;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.toOverworldNearOrigin;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.useBellInOverworld;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.walker;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.whenReady;

/**
 * 共享归隐之地模式（关闭私人维度、开启配额）：首次进入的提示与配额、搜索期间换手不吞铃、
 * 已找到后重复查看、没有配额时的提示、不同玩家各自找到自己的隐世之境。
 * <p>测试按 {@link Order} 顺序运行并共享宿主玩家在 RetreatDimensionData 里的记录。
 */
@DisplayName("共享归隐之地：首次进入、配额与寻风之铃搜索")
@Scene("tlms.integratedShared")
@ResourceLock(RetreatFixtures.HOST_PLAYER)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SharedRetreatTest {
    private static final String FIRST_ENTRY = BELL_KEY + "first_entry_shared";
    private static final String NO_QUOTA = BELL_KEY + "no_quota";
    private static final ResourceLocation SHARED = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "the_retreat");
    /** 动作栏提示在客户端停留约 60 tick；首次进入提示至少要这么久不被别的动作栏消息顶掉才看得到。 */
    private static final int OVERLAY_VISIBLE_TICKS = 40;

    /** 首次进入共享归隐之地的"已分配专属隐世之境"动作栏提示要能被看到：之后一段时间内不被别的动作栏消息顶掉。 */
    @DisplayName("首次进入的“已分配专属隐世之境”动作栏提示不被别的动作栏消息顶掉，能被看到")
    @Test
    @Order(1)
    @Requires(Requires.Capability.CLIENT)
    void firstEntryNoticeVisible(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        resetFirstEntry(ctx, host);
        // 本类第一个测试时客户端刚进世界、还在加载，收到提示会比服务端发出晚上一截，先等它空闲
        ClientSide.awaitIdle(ctx);
        // 提示的可见时间从提示到达客户端起算：不等传送落定，免得把计时拖过去
        long mark = useBellInOverworld(ctx, host);
        ctx.await(ENTER_TICKS, "收到首次进入提示", () -> ChatTap.find(mark, FIRST_ENTRY, true).isPresent());
        ctx.ticks(OVERLAY_VISIBLE_TICKS);
        List<String> overlays = ChatTap.keys(mark, true);
        ctx.record("overlays", overlays);
        ctx.check(overlays.getLast()).as("首次进入后 " + OVERLAY_VISIBLE_TICKS
                + " tick 内最后显示的动作栏提示（依次收到：" + overlays + "）").isEqualTo(FIRST_ENTRY);
        ctx.await(ENTER_TICKS, "进入归隐之地", () -> TheRetreatDimension.isInRetreat(host));
    }

    /** 主世界右键进入共享归隐之地：首次进入提示、登记 1 个配额、不消耗铃。 */
    @DisplayName("主世界右键进入共享归隐之地：首次进入提示、登记 1 个配额、不消耗铃")
    @Test
    @Order(2)
    @Requires(Requires.Capability.CLIENT)
    void firstEntry(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        resetFirstEntry(ctx, host);
        long mark = enterFromOverworld(ctx, host);
        ctx.await(NOTICE_TICKS, "收到进入提示", () -> ChatTap.find(mark, ENTERED, true).isPresent());
        ctx.run(() -> {
            ctx.check(host.level().dimension().location()).as("进入的维度").isEqualTo(SHARED);
            ctx.check(ChatTap.find(mark, FIRST_ENTRY, true).isPresent())
                    .as("收到首次进入提示（动作栏：" + ChatTap.keys(mark, true) + "）").isTrue();
            ctx.check(quota(host)).as("首次进入后的搜索配额").isEqualTo(1);
            ctx.check(host.getMainHandItem().getCount()).as("进入后手上铃的数量").isEqualTo(BELLS);
        });
    }

    /** 搜索期间把铃从手上换走：仍给出坐标，不吞铃、不飞铃，配额照常消耗并记住找到的位置。 */
    @DisplayName("搜索期间把铃换走仍给出坐标，不吞铃、不飞铃，配额照常消耗并记住位置")
    @Test
    @Order(3)
    @Requires(Requires.Capability.CLIENT)
    void switchItemDuringSearch(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        ctx.run(() -> RetreatManager.clearPlayerCache(host.getUUID()));
        // 搜索分帧进行，结果最早下一 tick 才回来；右键后立刻把铃挪到背包别的格子
        Search result = search(ctx, host, () -> {
            ItemStack bells = host.getMainHandItem().copy();
            Players.hold(host, new ItemStack(Items.STICK));
            host.getInventory().setItem(host.getInventory().selected == 8 ? 7 : 8, bells);
        });
        Found found = found(ctx, result.mark());
        ctx.run(() -> {
            ctx.check(host.getInventory().countItem(MaidSpellItems.WIND_SEEKING_BELL.get())).as("背包里铃的总数").isEqualTo(BELLS);
            ctx.check(result.flyingBells().size()).as("飞出的寻风之铃实体数").isEqualTo(0);
            ctx.check(quota(host)).as("找到后剩余配额").isEqualTo(0);
            RetreatDimensionData.DimensionInfo info = info(host);
            BlockPos saved = info != null ? info.foundStructurePos : null;
            if (found != null) {
                ctx.check(saved == null ? "<未记录>" : saved.getX() + "," + saved.getZ()).as("记住的结构位置")
                        .isEqualTo(found.x() + "," + found.z());
            }
        });
    }

    @DisplayName("已找到后再次右键，直接给出记住的坐标且不再进入等待搜索")
    @Test
    @Order(4)
    @Requires(Requires.Capability.CLIENT)
    void reshowSameCoordinates(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        reshow(ctx, Players.host(ctx, player), false);
    }

    /** 重复查看复用坐标，仍消耗一只铃。 */
    @DisplayName("已找到后再次右键复用坐标，仍消耗一只铃")
    @Test
    @Order(5)
    @Requires(Requires.Capability.CLIENT)
    void reshowConsumesBell(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        reshow(ctx, Players.host(ctx, player), true);
    }

    /** 没有配额、也没有找到过的结构时右键：提示"你只能搜索一次！"，不搜索、不消耗铃。 */
    @DisplayName("没有配额也没找到过结构时右键，提示“你只能搜索一次！”，不搜索、不消耗铃")
    @Test
    @Order(6)
    @Requires(Requires.Capability.CLIENT)
    void noQuota(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        boolean registered = ctx.get(() -> {
            RetreatDimensionData.DimensionInfo info = info(host);
            ctx.check(info != null).as("玩家已登记").isTrue();
            if (info == null) {
                return false;
            }
            BlockPos saved = info.foundStructurePos;
            int quota = info.structureQuota;
            ctx.cleanup(() -> {
                info.foundStructurePos = saved;
                info.structureQuota = quota;
                RetreatDimensionData.get(ctx.server()).setDirty();
            });
            info.foundStructurePos = null;
            info.structureQuota = 0;
            RetreatManager.clearPlayerCache(host.getUUID());
            return true;
        });
        if (!registered) {
            return;
        }
        whenReady(ctx, host);
        long mark = ChatTap.mark();
        ctx.run(() -> Players.use(host));
        ctx.await(40, "收到没有配额提示", () -> ChatTap.find(mark, NO_QUOTA, true).isPresent());
        ctx.ticks(10);
        ctx.check(ChatTap.keys(mark, true).contains(WAITING)).as("没有配额时进入等待搜索").isFalse();
        ctx.check(mainHandCount(ctx, host)).as("没有配额时手上铃的数量").isEqualTo(BELLS);
    }

    /**
     * 两个模拟玩家（NeoForge FakePlayer，直接放在共享归隐之地同一位置）先后用铃搜索：各自找到一个隐世之境，
     * 位置互不相同、也不同于宿主玩家的；再次搜索不会得到新的。模拟玩家收不到消息，从服务器记录读结果。
     */
    @DisplayName("两个模拟玩家先后搜索，各自找到互不相同的隐世之境，再次搜索不会得到新的")
    @Test
    @Order(7)
    @Requires(Requires.Capability.CLIENT)
    void playersFindOwnRetreats(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        BlockPos hostFound = ctx.get(() -> RetreatDimensionData.get(ctx.server())
                .getFoundStructurePos(Stage.serverPlayer(player).getUUID()));
        CompletableFuture<ServerLevel> shared = ctx.get(() -> PlayerRetreatManager.getOrCreateSharedRetreatAsync(ctx.server()));
        ctx.await(ENTER_TICKS, "共享归隐之地创建完成", shared::isDone);
        ServerLevel level = shared.join();
        ctx.cleanup(() -> cancelSearches(level));
        List<ServerPlayer> walkers = ctx.get(() -> {
            List<ServerPlayer> created = new ArrayList<>();
            for (String name : List.of("TlmsWalkerA", "TlmsWalkerB")) {
                created.add(walker(level, profile(name), new BlockPos(ctx.originX(), 100, ctx.originZ())));
            }
            return created;
        });
        Map<String, BlockPos> found = new LinkedHashMap<>();
        searchInTurn(ctx, walkers, found);
        ctx.record("found", found.toString());
        ctx.record("hostFound", String.valueOf(hostFound));
        List<BlockPos> positions = new ArrayList<>(found.values());
        ctx.check(positions.size()).as("找到隐世之境的模拟玩家数").isEqualTo(walkers.size());
        if (positions.size() != walkers.size()) {
            return;
        }
        ctx.check(new HashSet<>(positions).size()).as("模拟玩家找到的不同位置数").isEqualTo(walkers.size());
        if (hostFound != null) {
            ctx.check(positions.contains(hostFound)).as("模拟玩家找到了宿主玩家的隐世之境").isFalse();
        }
        for (ServerPlayer walker : walkers) {
            ctx.check(ctx.get(() -> walker.getMainHandItem().getCount())).as(walker.getGameProfile().getName() + " 搜索后手上铃的数量")
                    .isEqualTo(BELLS - 1);
        }
        Structure structure = ctx.get(() -> Worldgen.structure(level, HIDDEN_RETREAT).value());
        Map<String, Boolean> hasStart = Collections.synchronizedMap(new LinkedHashMap<>());
        Batch.runAsync(ctx, List.copyOf(found.entrySet()), entry -> Worldgen.startAt(level, structure, new ChunkPos(entry.getValue()))
                .thenApply(start -> {
                    hasStart.put(entry.getKey(), start != null);
                    return false;
                }), 600);
        ctx.check(Map.copyOf(hasStart)).as("模拟玩家找到的位置上有隐世之境起点")
                .isEqualTo(Map.of("TlmsWalkerA", true, "TlmsWalkerB", true));
        // 同一玩家再次搜索：不会得到新的隐世之境
        ServerPlayer again = walkers.getFirst();
        BlockPos first = found.get(again.getGameProfile().getName());
        ctx.run(() -> {
            again.getCooldowns().removeCooldown(MaidSpellItems.WIND_SEEKING_BELL.get());
            RetreatManager.clearPlayerCache(again.getUUID());
            Players.use(again);
        });
        ctx.ticks(40);
        ctx.run(() -> {
            ctx.check(RetreatDimensionData.get(ctx.server()).getFoundStructurePos(again.getUUID())).as("再次搜索后记住的位置")
                    .isEqualTo(first);
            ctx.check(quota(again)).as("再次搜索后的配额").isEqualTo(0);
        });
    }

    // ---- 工具 ----

    /** 已找到后再次右键：直接给出记住的坐标，不再进入等待搜索；checkConsumption 时还检查消耗一只铃。 */
    private static void reshow(Stage ctx, ServerPlayer host, boolean checkConsumption) {
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        BlockPos saved = ctx.get(() -> RetreatDimensionData.get(ctx.server()).getFoundStructurePos(host.getUUID()));
        ctx.check(saved != null).as("已记住找到的结构位置（前面的测试已找到）").isTrue();
        if (saved == null) {
            return;
        }
        Search result = search(ctx, host);
        Found found = found(ctx, result.mark());
        ctx.check(ChatTap.keys(result.mark(), true).contains(WAITING)).as("重复查看时进入等待搜索").isFalse();
        if (found != null) {
            ctx.check(found.x() + "," + found.z()).as("重复查看给出的坐标").isEqualTo(saved.getX() + "," + saved.getZ());
        }
        if (checkConsumption) {
            ctx.check(mainHandCount(ctx, host)).as("重复查看后手上铃的数量").isEqualTo(BELLS - 1);
        }
    }

    /** 依次让模拟玩家右键搜索，把找到的位置（服务器记录）放进 found（测试线程）。 */
    private static void searchInTurn(Stage ctx, List<ServerPlayer> walkers, Map<String, BlockPos> found) {
        RetreatDimensionData data = ctx.get(() -> RetreatDimensionData.get(ctx.server()));
        for (ServerPlayer walker : walkers) {
            String name = walker.getGameProfile().getName();
            ctx.run(() -> {
                Players.use(walker);
                ctx.record(name + ".quotaAfterFirstUse", quota(walker));
            });
            ctx.await(SEARCH_TICKS, name + " 搜索出结果", () -> data.getFoundStructurePos(walker.getUUID()) != null);
            found.put(name, ctx.get(() -> data.getFoundStructurePos(walker.getUUID())));
            // 扣铃和发铃在找到后的下一 tick，等它完成再比较铃的数量
            ctx.ticks(2);
        }
    }

    private static RetreatDimensionData.DimensionInfo info(ServerPlayer player) {
        return RetreatDimensionData.get(player.server).getDimensionInfo(player.getUUID());
    }

    /** 剩余搜索配额；没有登记时为 -1（服务端线程）。 */
    private static int quota(ServerPlayer player) {
        RetreatDimensionData.DimensionInfo info = info(player);
        return info != null ? info.structureQuota : -1;
    }

    /** 回到主世界并删掉玩家在共享归隐之地的登记，让下一次进入重新算作首次进入（测试线程）。 */
    private static void resetFirstEntry(Stage ctx, ServerPlayer player) {
        toOverworldNearOrigin(ctx, player);
        ctx.run(() -> {
            RetreatDimensionData.get(ctx.server()).removeDimension(player.getUUID());
            RetreatManager.clearPlayerCache(player.getUUID());
        });
    }
}
