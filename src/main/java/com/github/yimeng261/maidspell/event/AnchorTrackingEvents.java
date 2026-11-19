package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.message.MaidClientRemovalGuardMessage;
import com.github.yimeng261.maidspell.network.message.MaidClientRemovalGuardMessage.Action;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 按玩家的跟踪状态同步客户端移除守卫：开始跟踪戴锚定核心的女仆时声明受保护，
 * 停止跟踪时放行客户端移除，穿戴或卸下锚定核心时通知正在跟踪的玩家。
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class AnchorTrackingEvents {
    private AnchorTrackingEvents() {}

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof EntityMaid maid
                && AnchorCoreProtection.hasAnchorCore(maid)) send(player, maid, Action.PROTECT);
    }

    @SubscribeEvent
    public static void stopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof EntityMaid maid) {
            send(player, maid, Action.STOP_TRACKING);
        }
    }

    public static void updateEquipped(EntityMaid maid, boolean equipped) {
        if (!maid.level().isClientSide() && maid.isAddedToLevel()) {
            PacketDistributor.sendToPlayersTrackingEntity(maid,
                    new MaidClientRemovalGuardMessage(maid.getId(), maid.getUUID(), equipped ? Action.PROTECT : Action.UNPROTECT));
        }
    }

    private static void send(ServerPlayer player, EntityMaid maid, Action action) {
        PacketDistributor.sendToPlayer(player, new MaidClientRemovalGuardMessage(maid.getId(), maid.getUUID(), action));
    }
}
