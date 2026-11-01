package com.github.yimeng261.maidspell.stagewright.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Logs;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.List;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 客户端对戴锚定核心的女仆调 discard()：客户端实体只是服务端的镜像，照常移除、不留幽灵女仆，也不记拦截日志；
 * 服务端那只不受影响。
 */
@Scene("tlms.integrated")
@DisplayName("锚定核心：客户端移除女仆")
class AnchorClientDiscardTest {
    @DisplayName("客户端对戴锚定核心的女仆调 discard 后客户端实体消失，不记拦截日志，服务端女仆仍在")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void clientDiscardNotBlocked(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid owned = Owners.maid(ctx, player, 3, 0, 0);
            Actors.equipBauble(owned, NS + "anchor_core");
            return owned;
        });
        UUID id = maid.getUUID();
        int logBefore = logSize();
        ctx.await(100, "客户端看到这只女仆", () -> Minecraft.getInstance().submit(() -> clientMaid(id) != null).join());
        ClientSide.awaitIdle(ctx);
        String result = ClientSide.call(ctx, () -> {
            Entity entity = clientMaid(id);
            if (entity == null) {
                return "客户端没有这只女仆";
            }
            entity.discard();
            return "removed=" + entity.isRemoved() + " stillInLevel=" + (clientMaid(id) != null);
        });
        ctx.check(result).as("客户端 discard 之后").isEqualTo("removed=true stillInLevel=false");
        ctx.check(ctx.get(() -> maid.isAlive() && !maid.isRemoved())).as("服务端女仆仍在").isTrue();
        List<String> lines = Logs.lines();
        List<String> blocked = lines == null ? List.of() : lines.subList(Math.min(logBefore, lines.size()), lines.size()).stream()
                .filter(line -> line.contains("anchor_core protection") || line.contains("untrusted caller"))
                .toList();
        ctx.check(blocked).as("客户端 discard 期间的拦截日志").isEmpty();
    }

    private static Entity clientMaid(UUID id) {
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    private static int logSize() {
        List<String> lines = Logs.lines();
        return lines == null ? 0 : lines.size();
    }
}
