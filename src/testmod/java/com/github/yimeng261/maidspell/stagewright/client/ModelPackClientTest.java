package com.github.yimeng261.maidspell.stagewright.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
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
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.List;
import java.util.Set;

/**
 * 客户端的车万女仆模型表：1.5.3 自带的两个酒狐模型与星之魔女酒狐都在，已移除的 winefox_saint_black 不在；
 * 旧存档里仍用这个模型 ID 的女仆出现在玩家面前时客户端照常渲染、不掉线。
 */
@Scene("tlms.integrated")
class ModelPackClientTest {
    private static final String REMOVED_MODEL = "geckolib:winefox_saint_black";

    @Test
    @Requires(Requires.Capability.CLIENT)
    void clientModelList(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        Set<String> models = ClientSide.call(ctx, () -> Set.copyOf(CustomPackLoader.MAID_MODELS.getModelIdSet()));
        for (String model : List.of("geckolib:winefox_elf", "geckolib:winefox_saint", "touhou_little_maid_spell:stellar_witch")) {
            ctx.check(models.contains(model)).as("客户端模型表含 " + model).isTrue();
        }
        ctx.check(models.contains(REMOVED_MODEL)).as("客户端模型表含 " + REMOVED_MODEL).isFalse();
    }

    /** 把女仆放到玩家视线正前方，渲染 3 秒后客户端仍在世界里、能看到这只女仆。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void removedModelIdStillRenders(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid still = Actors.stillMaid(ctx, 0, 0, 0);
            still.setModelId(REMOVED_MODEL);
            Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(3));
            still.moveTo(front.x, player.getY(), front.z, player.getYRot() + 180, 0);
            return still;
        });
        ctx.ticks(60);
        boolean fine = ClientSide.call(ctx, () -> {
            Minecraft mc = Minecraft.getInstance();
            Entity seen = mc.level == null ? null : mc.level.getEntity(maid.getId());
            return mc.getConnection() != null && seen instanceof EntityMaid;
        });
        ctx.check(fine).as("客户端仍连着并看得到这只女仆").isTrue();
    }
}
