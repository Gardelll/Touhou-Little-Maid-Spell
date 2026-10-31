package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 跨存档流程：进入归隐之地后退出到标题、立即新建另一个存档再用寻风之铃；刚摇铃（维度还在创建）就退出，
 * 重新打开同一存档再进归隐之地。两者都要求客户端不崩溃、能照常进入归隐之地。
 * <p>切换存档期间不能有别的测试在跑，也不能租 plot；测试结束时框架重新打开场景原来的存档。
 */
@Scene("tlms.integrated")
@Isolated
class CrossSaveClientTest {
    private static final int ENTER_TICKS = 600;
    /** 铃每次使用后有 20 tick 冷却，等它结束的上限留一倍余量。 */
    private static final int COOLDOWN_TICKS = 40;
    /** 客户端刚进世界时还在加载资源，线程可能连续几秒不响应。 */
    private static final int IDLE_TICKS = 600;
    /** 主世界里摇铃的位置（x = z）。 */
    private static final int NEAR_ORIGIN = 8;

    /** 当前存档进入归隐之地 → 退出到标题 → 新建另一个存档 → 在新存档里摇铃照常进入归隐之地。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void retreatAfterSwitchingSaves(Game game) {
        enterRetreat(game);
        game.leaveWorld();
        game.openWorld("crossSaveFresh");
        enterRetreat(game);
        awaitClientIdle(game);
    }

    /**
     * 新建存档后摇铃，维度创建挂起时退出，随后确认挂起的归隐维度创建请求已了结（取消，或在服务器停下前建完），不会悬着；
     * 重新打开同一存档、传到主世界出生点附近再摇铃，照常进入归隐之地。
     */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void quitWhileRetreatCreating(Game game, TestReporter reporter) {
        String world = "crossSaveQuitWhileCreating";
        game.openWorld(world);
        CompletableFuture<ServerLevel> creating = game.onServer(server -> {
            ServerPlayer player = host(server);
            useBell(player);
            return PlayerRetreatManager.getOrCreatePlayerRetreatAsync(server, player.getUUID());
        });
        boolean pendingAtQuit = !creating.isDone();
        // 退出存档时服务器还会再跑几个 tick，挂起的创建可能赶在停服前完成，所以只要求请求不悬着
        game.leaveWorld();
        reporter.publishEntry("pendingAtQuit", String.valueOf(pendingAtQuit));
        reporter.publishEntry("creationAfterQuit", !creating.isDone() ? "悬着"
                : creating.isCompletedExceptionally() ? "已取消" : "已建完");
        game.check(creating.isDone(), "退出存档后，摇铃发起的归隐维度创建请求已了结");
        game.openWorld(world);
        enterRetreat(game);
        awaitClientIdle(game);
    }

    /** 唯一玩家（集成服的宿主）在服务端的实体（服务端线程）。 */
    private static ServerPlayer host(net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    /** 手持一个寻风之铃右键（服务端线程）。 */
    private static void useBell(ServerPlayer player) {
        player.setShiftKeyDown(false);
        Players.hold(player, new ItemStack(MaidSpellItems.WIND_SEEKING_BELL.get()));
        Players.use(player);
    }

    /** 传到主世界原点附近的地面上，摇铃并等到进入归隐之地（测试线程）。 */
    private static void enterRetreat(Game game) {
        game.runOnServer(server -> {
            ServerPlayer player = host(server);
            ServerLevel overworld = server.overworld();
            overworld.getChunk(NEAR_ORIGIN >> 4, NEAR_ORIGIN >> 4);
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, NEAR_ORIGIN, NEAR_ORIGIN);
            player.teleportTo(overworld, NEAR_ORIGIN + 0.5, y, NEAR_ORIGIN + 0.5, Set.of(), 0, 0);
        });
        game.await().within(COOLDOWN_TICKS).as("寻风之铃冷却结束")
                .until(() -> game.onServer(server -> !host(server).getCooldowns()
                        .isOnCooldown(MaidSpellItems.WIND_SEEKING_BELL.get())));
        game.runOnServer(server -> useBell(host(server)));
        game.await().within(ENTER_TICKS).as("进入归隐之地")
                .until(() -> game.onServer(server -> TheRetreatDimension.isInRetreat(host(server))));
    }

    /** 客户端线程仍在处理任务（测试线程）。 */
    private static void awaitClientIdle(Game game) {
        CompletableFuture<Boolean> idle = Minecraft.getInstance().submit(() -> Boolean.TRUE);
        game.await().within(IDLE_TICKS).as("客户端线程仍在响应").until(idle::isDone);
    }
}
