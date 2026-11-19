package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

public record MaidClientRemovalGuardMessage(int entityId, UUID uuid, Action action) implements CustomPacketPayload {
    public static final Type<MaidClientRemovalGuardMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "maid_client_removal_guard"));

    public static final StreamCodec<ByteBuf, MaidClientRemovalGuardMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            MaidClientRemovalGuardMessage::entityId,
            UUIDUtil.STREAM_CODEC,
            MaidClientRemovalGuardMessage::uuid,
            ByteBufCodecs.idMapper(index -> Action.values()[index], Action::ordinal),
            MaidClientRemovalGuardMessage::action,
            MaidClientRemovalGuardMessage::new
    );

    public enum Action { PROTECT, UNPROTECT, STOP_TRACKING }

    @Override
    public Type<MaidClientRemovalGuardMessage> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle() {
        switch (action) {
            case PROTECT -> ClientMaidRemovalGuard.markProtected(entityId, uuid);
            case UNPROTECT -> ClientMaidRemovalGuard.unmark(entityId, uuid);
            case STOP_TRACKING -> ClientMaidRemovalGuard.allowRemovalNow(entityId, uuid);
        }
    }
}
