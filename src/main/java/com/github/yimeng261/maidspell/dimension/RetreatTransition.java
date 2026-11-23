package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.message.RetreatTransitionAckMessage;
import com.github.yimeng261.maidspell.network.message.RetreatTransitionMessage;
import com.github.yimeng261.maidspell.network.message.RetreatTransitionMessage.Stage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** 在服务端线程上协调寻风之铃进入归隐之地时的转场画面与实际传送。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class RetreatTransition {
    private static final int COVER_TIMEOUT_TICKS = 100;
    private static final int ARRIVAL_TIMEOUT_TICKS = 700;
    private static final Map<UUID, PendingEntry> PENDING = new HashMap<>();

    private RetreatTransition() {
    }

    /** 等待客户端遮屏、传送进行中（传送结果返回前不重复触发）、已到达等待地形就绪三个阶段。 */
    private enum Phase { COVERING, TRANSFERRING, ARRIVED }

    private record PendingEntry(UUID token, ServerPlayer player, ServerLevel target, BlockPos position,
                                int deadline, Phase phase, Consumer<ServerPlayer> announceArrival) {
        private PendingEntry advance(Phase next, int nextDeadline) {
            return new PendingEntry(token, player, target, position, nextDeadline, next, announceArrival);
        }
    }

    public static boolean isPending(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    public static void enter(ServerPlayer player, ServerLevel target) {
        enter(player, target, RetreatTransition::announceEntered);
    }

    /** {@code announceArrival} 在玩家确实进入目标维度后调用，负责给出进入提示。 */
    public static void enter(ServerPlayer player, ServerLevel target, Consumer<ServerPlayer> announceArrival) {
        if (isPending(player)) {
            return;
        }
        BlockPos position = player.blockPosition();
        if (!Level.OVERWORLD.equals(player.level().dimension())) {
            TheRetreatDimension.teleportToRetreat(player, target, position).thenAccept(entered ->
                    announceResult(player, entered, announceArrival));
            return;
        }

        PendingEntry entry = new PendingEntry(UUID.randomUUID(), player, target, position,
                player.server.getTickCount() + COVER_TIMEOUT_TICKS, Phase.COVERING, announceArrival);
        PENDING.put(player.getUUID(), entry);
        send(player, new RetreatTransitionMessage(entry.token(), Stage.BEGIN, target.dimension().location()));
    }

    public static void onAcknowledged(ServerPlayer player, RetreatTransitionAckMessage message) {
        PendingEntry entry = PENDING.get(player.getUUID());
        if (entry == null || entry.player() != player || !entry.token().equals(message.token())) {
            return;
        }
        if (entry.phase() == Phase.COVERING && message.stage() == RetreatTransitionAckMessage.Stage.COVERED) {
            transfer(entry);
        } else if (entry.phase() == Phase.ARRIVED && message.stage() == RetreatTransitionAckMessage.Stage.TERRAIN_READY
                && player.isAlive() && player.level() == entry.target()) {
            PENDING.remove(player.getUUID());
            // 纠正目标地形加载期间客户端自行预测的移动
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            send(player, new RetreatTransitionMessage(entry.token(), Stage.REVEAL, null));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (PendingEntry entry : List.copyOf(PENDING.values())) {
            boolean expired = event.getServer().getTickCount() - entry.deadline() >= 0;
            switch (entry.phase()) {
                case COVERING -> {
                    if (expired || !canTravel(entry)) {
                        // 客户端停止渲染、一直不回报遮屏时也照常传送
                        transfer(entry);
                    }
                }
                case TRANSFERRING -> {
                    if (expired) {
                        cancel(entry);
                    }
                }
                case ARRIVED -> {
                    if (expired || !entry.player().isAlive() || entry.player().level() != entry.target()) {
                        cancel(entry);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private static boolean canTravel(PendingEntry entry) {
        ServerPlayer player = entry.player();
        return player.isAlive() && Level.OVERWORLD.equals(player.level().dimension())
                && player.server.getLevel(entry.target().dimension()) == entry.target();
    }

    private static void transfer(PendingEntry entry) {
        ServerPlayer player = entry.player();
        if (!canTravel(entry)) {
            cancel(entry);
            return;
        }
        // 传送结果的 future 完成前不再重复触发
        int deadline = player.server.getTickCount() + ARRIVAL_TIMEOUT_TICKS;
        PendingEntry transferring = entry.advance(Phase.TRANSFERRING, deadline);
        PENDING.replace(player.getUUID(), entry, transferring);
        TheRetreatDimension.teleportToRetreat(player, entry.target(), entry.position()).thenAccept(entered -> {
            announceResult(player, entered, entry.announceArrival());
            if (!entered) {
                cancel(transferring);
            } else {
                PENDING.replace(player.getUUID(), transferring, transferring.advance(Phase.ARRIVED, deadline));
            }
        });
    }

    private static void cancel(PendingEntry entry) {
        if (PENDING.remove(entry.player().getUUID(), entry)) {
            send(entry.player(), new RetreatTransitionMessage(entry.token(), Stage.CANCEL, null));
        }
    }

    private static void announceResult(ServerPlayer player, boolean entered, Consumer<ServerPlayer> announceArrival) {
        if (entered) {
            announceArrival.accept(player);
        } else {
            player.displayClientMessage(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.teleport_failed")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static void announceEntered(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.entered_retreat")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void send(ServerPlayer player, RetreatTransitionMessage message) {
        PacketDistributor.sendToPlayer(player, message);
    }
}
