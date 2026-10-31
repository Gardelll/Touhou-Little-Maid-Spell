package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.BELLS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.enterRetreat;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.holdBells;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatFixtures.locate;

/**
 * 归隐之地的搜索缓存与维度：摇铃后不留强加载、reset 命令清缓存、找不到时的负缓存约 5 分钟后过期、
 * 不同玩家的私人归隐之地地形不同。
 */
@Scene("tlms.integrated")
@ResourceLock(RetreatFixtures.HOST_PLAYER)
class RetreatCacheTest {
    /** 负缓存有效期（游戏刻），与寻风之铃「约 5 分钟内再用直接提示没有」对应。 */
    private static final long NEGATIVE_TTL = 6000;

    @Test
    @Requires(Requires.Capability.CLIENT)
    void searchLeavesNoForcedChunks(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        if (locate(ctx, host) == null) {
            return;
        }
        ctx.check(ctx.get(() -> host.serverLevel().getForcedChunks().size())).as("摇铃找到结构后归隐之地的强加载区块数").isEqualTo(0);
    }

    @Test
    @Requires(Requires.Capability.CLIENT)
    void resetCommandClearsCache(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        if (locate(ctx, host) == null) {
            return;
        }
        ServerLevel level = ctx.get(host::serverLevel);
        ctx.check(ctx.get(() -> RetreatManager.checkCache(level, host.getUUID()).position != null)).as("找到后有缓存坐标").isTrue();
        String error = ctx.get(() -> {
            try {
                ctx.server().getCommands().getDispatcher().execute("maidspell retreat reset " + host.getScoreboardName(),
                        ctx.server().createCommandSourceStack().withSuppressedOutput().withPermission(4));
                return null;
            } catch (CommandSyntaxException e) {
                return e.getMessage();
            }
        });
        if (error != null) {
            throw ctx.fail("reset 命令执行失败：" + error);
        }
        ctx.check(ctx.get(() -> RetreatManager.checkCache(level, host.getUUID()).hasCache)).as("reset 后仍有缓存").isFalse();
    }

    /** 负缓存在记下后 6000 刻内有效，之后当作没有缓存重新搜索（临时把主世界游戏时间往后拨再拨回）。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void negativeCacheExpires(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        holdBells(ctx, host, BELLS);
        enterRetreat(ctx, host);
        ctx.run(() -> {
            ServerLevel level = host.serverLevel();
            UUID probe = UUID.randomUUID();
            RetreatManager.updateCache(level, probe, null);
            ctx.check(RetreatManager.checkCache(level, probe).isNegative).as("刚记下的负缓存").isTrue();
            ServerLevelData data = (ServerLevelData) ctx.server().overworld().getLevelData();
            long now = data.getGameTime();
            data.setGameTime(now + NEGATIVE_TTL - 100);
            boolean stillNegative = RetreatManager.checkCache(level, probe).isNegative;
            data.setGameTime(now + NEGATIVE_TTL + 100);
            boolean expired = !RetreatManager.checkCache(level, probe).hasCache;
            data.setGameTime(now);
            ctx.check(stillNegative).as("快到 5 分钟时负缓存仍有效").isTrue();
            ctx.check(expired).as("过了 5 分钟负缓存失效").isTrue();
        });
    }

    /** 宿主和另一名玩家的私人归隐之地，同一批坐标上的地表高度不全相同。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void privateTerrainDiffersPerPlayer(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer host = Players.host(ctx, player);
        UUID other = UUID.nameUUIDFromBytes("TlmsRetreatNeighbor".getBytes());
        CompletableFuture<ServerLevel> mine = ctx.get(
                () -> PlayerRetreatManager.getOrCreatePlayerRetreatAsync(ctx.server(), host.getUUID()));
        CompletableFuture<ServerLevel> theirs = ctx.get(() -> PlayerRetreatManager.getOrCreatePlayerRetreatAsync(ctx.server(), other));
        ctx.await(1100, "两个私人归隐之地创建完成", () -> mine.isDone() && theirs.isDone());
        ServerLevel a = mine.join();
        ServerLevel b = theirs.join();
        List<Integer> ha = new ArrayList<>();
        List<Integer> hb = new ArrayList<>();
        ctx.run(() -> {
            for (int i = 0; i < 16; i++) {
                BlockPos p = new BlockPos(i * 97 - 700, 0, i * 53 - 400);
                ha.add(height(a, p));
                hb.add(height(b, p));
            }
        });
        ctx.record("mine", ha);
        ctx.record("theirs", hb);
        ctx.check(ha.equals(hb)).as("两名玩家归隐之地同批坐标的地表高度完全相同").isFalse();
    }

    private static int height(ServerLevel level, BlockPos p) {
        return level.getChunkSource().getGenerator().getBaseHeight(p.getX(), p.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                level, level.getChunkSource().randomState());
    }
}
