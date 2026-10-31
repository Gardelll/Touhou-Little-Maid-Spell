package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidList;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.ProxyHealthProbe;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import static com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketTest.REMOTE;
import static com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketTest.nestedName;
import static com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketTest.netherSpot;
import static com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketTest.pocketMaid;

/** 末影腰包经真实网络（专用服 + 加入的客户端）：数据包真正编码、传输、解码，远程打开背包，客户端不掉线。 */
@Scene("tlms.withClient")
class EnderPocketNetworkTest {
    @Test
    @Requires(Requires.Capability.CLIENT)
    void openFarMaidOverNetwork(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        openOverNetwork(Stage.flat(game, plot, reporter), host, false);
    }

    @Test
    @Requires(Requires.Capability.CLIENT)
    void openCrossDimensionMaidOverNetwork(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        openOverNetwork(Stage.flat(game, plot, reporter), host, true);
    }

    /** 经网络：服务端发女仆快照，客户端建代理并回确认，服务端再打开车万女仆界面。 */
    private static void openOverNetwork(Stage ctx, Player host, boolean crossDimension) {
        ServerPlayer player = Players.host(ctx, host);
        EntityMaid maid = ctx.get(() -> crossDimension
                ? pocketMaid(ctx, ctx.server().getLevel(Level.NETHER), netherSpot(ctx), player)
                : pocketMaid(ctx, player, 0, 0, REMOTE));
        ctx.await(60, "女仆出现在列表里", () -> EnderPocketService.getPlayerEnderPocketMaids(player).stream()
                .anyMatch(info -> info.getMaidUUID().equals(maid.getUUID())));
        ctx.check(ctx.get(() -> EnderPocketService.openMaidInventory(player, maid.getUUID()))).as("请求打开").isTrue();
        ctx.await(100, "客户端确认后打开女仆界面",
                () -> player.containerMenu instanceof AbstractMaidContainer container && container.getMaid() == maid);
        ctx.run(() -> {
            ctx.check(EnderPocketService.isRemoteSessionActive(player, maid)).as("客户端确认后会话激活").isTrue();
            player.closeContainer();
        });
    }

    /** 经网络推送 70 只女仆的列表（截到 64 条）：客户端不断开。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void fullListStaysConnected(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ctx.run(() -> {
            for (int i = 0; i < 70; i++) {
                pocketMaid(ctx, player, i % 10 - 5, 0, 3 + i / 10);
            }
            player.connection.send(new S2CEnderPocketMaidList(EnderPocketService.getPlayerEnderPocketMaids(player), false));
        });
        ctx.ticks(40);
        ctx.run(() -> {
            ctx.check(ctx.server().getPlayerList().getPlayer(player.getUUID())).as("推送后客户端仍在线").isNotNull();
            ctx.check(player.hasDisconnected()).as("推送后客户端断开").isFalse();
        });
    }

    /** 远程界面开着时，客户端代理的生命值随服务端女仆变化。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void remoteDataPushedWhileOpen(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        EntityMaid maid = ctx.get(() -> pocketMaid(ctx, player, 0, 0, REMOTE));
        ctx.await(60, "女仆出现在列表里", () -> EnderPocketService.getPlayerEnderPocketMaids(player).stream()
                .anyMatch(info -> info.getMaidUUID().equals(maid.getUUID())));
        ctx.check(ctx.get(() -> EnderPocketService.openMaidInventory(player, maid.getUUID()))).as("请求打开").isTrue();
        ctx.await(100, "客户端确认后打开女仆界面", () -> player.containerMenu instanceof AbstractMaidContainer);
        float full = ctx.get(maid::getHealth);
        ProxyHealthProbe.awaitHealth(ctx, player, maid.getId(), full, 40);
        float half = ctx.get(() -> {
            float health = maid.getMaxHealth() / 2;
            Actors.setMaidHealth(maid, health);
            return health;
        });
        ProxyHealthProbe.awaitHealth(ctx, player, maid.getId(), half, 40);
        ctx.run(player::closeContainer);
    }

    /** 远处（客户端不追踪）女仆的名字嵌套 300 层：列表与 HUD 推送后客户端仍在线。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void deepNameStaysConnected(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ctx.run(() -> {
            EntityMaid maid = pocketMaid(ctx, player, 0, 0, REMOTE);
            maid.setCustomName(nestedName(300));
            player.connection.send(new S2CEnderPocketMaidList(EnderPocketService.getPlayerEnderPocketMaids(player), false));
        });
        ctx.ticks(40);
        ctx.run(() -> {
            ctx.check(ctx.server().getPlayerList().getPlayer(player.getUUID())).as("推送后客户端仍在线").isNotNull();
            ctx.check(player.hasDisconnected()).as("推送后客户端断开").isFalse();
        });
    }
}
