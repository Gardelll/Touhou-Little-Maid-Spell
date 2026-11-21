package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/** 服务端通知客户端开始、揭开或取消进入归隐之地的转场；只有开始阶段带目标维度。 */
public record RetreatTransitionMessage(UUID token, Stage stage, @Nullable ResourceLocation target)
        implements CustomPacketPayload {
    public enum Stage { BEGIN, REVEAL, CANCEL }

    public static final Type<RetreatTransitionMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "retreat_transition"));

    public static final StreamCodec<FriendlyByteBuf, RetreatTransitionMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RetreatTransitionMessage::token,
            NeoForgeStreamCodecs.enumCodec(Stage.class),
            RetreatTransitionMessage::stage,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
            message -> Optional.ofNullable(message.target()),
            (token, stage, target) -> new RetreatTransitionMessage(token, stage, target.orElse(null))
    );

    @Override
    public Type<RetreatTransitionMessage> type() {
        return TYPE;
    }
}
