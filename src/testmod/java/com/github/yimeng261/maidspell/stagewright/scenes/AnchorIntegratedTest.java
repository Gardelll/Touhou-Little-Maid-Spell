package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.player.ChunkLoadingData;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.List;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.scenes.AnchorTest.ANCHOR;
import static com.github.yimeng261.maidspell.stagewright.scenes.AnchorTest.here;

/** 锚定核心的强加载记录（集成服，需要真实主人在线）：记录写在主人身上，女仆死亡后删除，主人在别的维度时也跟着更新。 */
@Scene("tlms.integrated")
@DisplayName("锚定核心：强加载记录（需真实主人在线）")
class AnchorIntegratedTest {
    /** 主人在线：记录写在主人身上；女仆被杀后立刻删掉。 */
    @DisplayName("主人在线时记录写在主人身上，女仆被杀后立刻删除")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void onlineOwnerRecordOnPlayer(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid owned = Owners.maid(ctx, player, 3, 0, 0);
            Actors.equipBauble(owned, ANCHOR);
            UUID id = owned.getUUID();
            ctx.cleanup(() -> player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().remove(id));
            return owned;
        });
        UUID id = maid.getUUID();
        ctx.await(10, "记录写到主人身上", () -> here(maid).equals(player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().get(id)));
        ctx.run(() -> Maids.kill(maid));
        ctx.await(10, "女仆死亡后记录删除", () -> !player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().containsKey(id));
        ctx.record("note", "女仆死亡后主人身上的记录已删");
    }

    /** 主人在主世界，锚定女仆在下界换区块：主人身上的记录跟着更新。 */
    @DisplayName("主人在别的维度时，下界女仆换区块后主人身上的记录跟着更新")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void recordUpdatesWhileOwnerInOtherDimension(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host,
                                                 TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ChunkPos first = new ChunkPos(40, 40);
        ChunkPos second = new ChunkPos(42, 40);
        EntityMaid maid = ctx.get(() -> {
            ServerLevel nether = ctx.server().getLevel(Level.NETHER);
            for (ChunkPos chunk : List.of(first, second)) {
                nether.setChunkForced(chunk.x, chunk.z, true);
                ctx.cleanup(() -> nether.setChunkForced(chunk.x, chunk.z, false));
            }
            EntityMaid created = (EntityMaid) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(Actors.MAID)).create(nether);
            created.setTame(true, false);
            created.setOwnerUUID(player.getUUID());
            created.setNoAi(true);
            created.moveTo(first.getMiddleBlockX() + 0.5, 128, first.getMiddleBlockZ() + 0.5);
            nether.addFreshEntity(created);
            ctx.cleanup(() -> Actors.cleanupEntity(created));
            Actors.equipBauble(created, ANCHOR);
            UUID id = created.getUUID();
            ctx.cleanup(() -> player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().remove(id));
            return created;
        });
        UUID id = maid.getUUID();
        ctx.await(10, "记录写到主人身上", () -> here(maid).equals(player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().get(id)));
        ctx.run(() -> maid.teleportTo(second.getMiddleBlockX() + 0.5, 128, second.getMiddleBlockZ() + 0.5));
        ctx.await(10, "记录跟到新区块", () -> here(maid).equals(player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().get(id)));
        ctx.record("note", "主人在别的维度时记录跟着换区块");
    }
}
