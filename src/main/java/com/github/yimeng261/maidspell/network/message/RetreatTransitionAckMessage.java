package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.RetreatTransition;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

import java.util.UUID;

/** 客户端回报转场画面已遮满屏幕，或目标维度的地形已经可以显示。 */
public record RetreatTransitionAckMessage(UUID token, Stage stage) implements CustomPacketPayload {
    public enum Stage { COVERED, TERRAIN_READY }

    public static final Type<RetreatTransitionAckMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "retreat_transition_ack"));

    public static final StreamCodec<FriendlyByteBuf, RetreatTransitionAckMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RetreatTransitionAckMessage::token,
            NeoForgeStreamCodecs.enumCodec(Stage.class),
            RetreatTransitionAckMessage::stage,
            RetreatTransitionAckMessage::new
    );

    @Override
    public Type<RetreatTransitionAckMessage> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        RetreatTransition.onAcknowledged(player, this);
    }
}
