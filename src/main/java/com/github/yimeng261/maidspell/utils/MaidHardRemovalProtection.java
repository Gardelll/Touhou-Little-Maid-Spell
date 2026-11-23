package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.coremod.AnchorSectionMembership;
import com.github.yimeng261.maidspell.mixin.accessor.AnchorEntityCallbackAccessor;
import com.github.yimeng261.maidspell.mixin.accessor.AnchorPersistentManagerAccessor;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.network.message.MaidClientRemovalGuardMessage;
import com.github.yimeng261.maidspell.network.message.MaidEntityRestoreMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 锚定女仆的通用硬移除守卫。
 *
 * <p>守卫作用在原版的实体移除原语与实体索引上，不按外部模组或实体编号判断；
 * 女仆按 UUID 登记，登记期间未经授权的移除与索引改动一律原地拦下，不重建实体。
 */
public final class MaidHardRemovalProtection {
    private static final int REGISTRATION_CLEANUP_INTERVAL_TICKS = 20;
    private static final int WARN_INTERVAL_TICKS = 100;
    private static final int CLIENT_RESTORE_RESEND_TICKS = 10;

    private static final Map<UUID, EntityMaid> TRACKED_MAIDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PENDING_CLIENT_RESTORE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WARN_TICK = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> ALLOW_HARD_REMOVAL = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<EntityMaid> AUTHORIZED_MAID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE_PROTECTION_CHECK = ThreadLocal.withInitial(() -> false);

    private MaidHardRemovalProtection() {
    }

    public static boolean shouldBlockSetRemoved(Entity entity, Entity.RemovalReason reason) {
        if (!(entity instanceof EntityMaid maid)) {
            return false;
        }
        if (isRemovalAuthorized(maid)) {
            return false;
        }
        if (shouldDenyRegisteredRemoval(maid, reason)) {
            if (isWhitelistedCaller()) {
                return false;
            }
            handleBlockedRemoveCall(maid, reason, "setRemoved");
            return true;
        }
        if (!isProtectedMaid(maid) || !isHardRemovalReason(reason)) {
            return false;
        }
        if (isWhitelistedCaller()) {
            return false;
        }

        rememberProtected(maid);
        queueClientRestore(maid);
        logBlockedAttempt(maid, reason, "setRemoved");
        return true;
    }

    public static boolean shouldBlockUntrustedHardRemoval(Entity entity, Entity.RemovalReason reason) {
        final boolean[] blocked = {false};
        runAsUntrustedHardRemoval(() -> blocked[0] = shouldBlockSetRemoved(entity, reason));
        return blocked[0];
    }

    public static boolean isProtectedAgainstHardRemoval(Entity entity) {
        return entity instanceof EntityMaid maid && isProtectedMaid(maid);
    }

    /** 索引写入不能信任被改动对象自身的生命值或移除标记，只看登记。 */
    public static boolean isRegisteredForIndexGuard(EntityMaid maid) {
        return TRACKED_MAIDS.get(maid.getUUID()) == maid;
    }

    public static boolean isRegisteredForIndexGuard(UUID uuid) {
        return TRACKED_MAIDS.containsKey(uuid);
    }

    public static boolean shouldDenyRegisteredRemoval(EntityMaid maid, Entity.RemovalReason reason) {
        return isHardRemovalReason(reason) && !isRemovalAuthorized(maid) && isRegisteredForIndexGuard(maid);
    }

    /**
     * 调用栈全部在锚核白名单内即视为授权：已登记的女仆同样适用，否则相机拍照、搬运和外部模组登记的调用者都删不掉她。
     * 只按调用栈判定，所以移除路径上的各层（含索引写入）各自核对都会得到同一结论。
     */
    public static boolean isWhitelistedCaller() {
        return !isForcingProtectionCheck() && AnchorCoreBauble.findIllegalCaller() == null;
    }

    public static boolean isRemovalAuthorized(EntityMaid maid) {
        return AUTHORIZED_MAID.get() == maid || ALLOW_HARD_REMOVAL.get();
    }

    public static void runAuthorizedRemoval(EntityMaid maid, Runnable action) {
        EntityMaid previous = AUTHORIZED_MAID.get();
        AUTHORIZED_MAID.set(maid);
        try {
            action.run();
        } finally {
            if (previous == null) AUTHORIZED_MAID.remove();
            else AUTHORIZED_MAID.set(previous);
        }
    }

    public static boolean hasRegisteredAnchors() {
        return !TRACKED_MAIDS.isEmpty();
    }

    public static boolean isOnWorldThread(Entity entity) {
        return entity.level() instanceof ServerLevel level ? level.getServer().isSameThread()
                : entity.level().isClientSide() && com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard.isOnClientThread();
    }

    public static void forgetRegistration(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        EntityMaid remaining = TRACKED_MAIDS.computeIfPresent(maidId,
                (id, registered) -> registered == maid ? null : registered);
        if (remaining == null) {
            PENDING_CLIENT_RESTORE.remove(maidId);
            LAST_WARN_TICK.remove(maidId);
        }
    }

    public static void handleBlockedRemoveCall(EntityMaid maid, Entity.RemovalReason reason, String path) {
        if (!isProtectedMaid(maid) || !isHardRemovalReason(reason)) {
            return;
        }

        rememberProtected(maid);
        queueClientRestore(maid);
        logBlockedAttempt(maid, reason, path);
    }

    public static void rememberProtected(EntityMaid maid) {
        TRACKED_MAIDS.put(maid.getUUID(), maid);
    }

    public static void rememberIfProtected(EntityMaid maid) {
        if (isProtectedMaid(maid)) {
            rememberProtected(maid);
        } else {
            forgetRegistration(maid);
        }
    }

    public static void tick(MinecraftServer server) {
        syncPendingClientRestores(server);
        if (server.getTickCount() % REGISTRATION_CLEANUP_INTERVAL_TICKS != 0) {
            return;
        }

        for (EntityMaid maid : TRACKED_MAIDS.values()) {
            if (!(maid.level() instanceof ServerLevel level) || level.getServer() != server
                    || !AnchorCoreProtection.hasAnchorCore(maid)
                    || (maid.isRemoved() && level.getEntity(maid.getUUID()) != maid && level.getEntity(maid.getId()) != maid)) {
                forgetRegistration(maid);
            }
        }
    }

    public static void restoreSectionsBeforeSave(PersistentEntitySectionManager<?> manager) {
        var getter = manager.getEntityGetter();
        var storage = ((AnchorPersistentManagerAccessor<?>) manager).maidspell$sectionStorage();
        for (EntityMaid maid : TRACKED_MAIDS.values()) {
            if (!isOnWorldThread(maid) || maid.isRemoved() || isRemovalAuthorized(maid)
                    || getter.get(maid.getUUID()) != maid || getter.get(maid.getId()) != maid
                    || !(maid instanceof AnchorEntityCallbackAccessor accessor)
                    || !(accessor.maidspell$levelCallback() instanceof AnchorSectionMembership membership)) continue;
            if (membership.maidspell$restoreSectionMembership(storage)) {
                Global.LOGGER.warn("[MaidSpell] Repaired persistence section before save maid={} position={}",
                        maid.getUUID(), maid.blockPosition());
            }
        }
    }

    public static void clear() {
        TRACKED_MAIDS.clear();
        PENDING_CLIENT_RESTORE.clear();
        LAST_WARN_TICK.clear();
    }

    public static void runAllowingHardRemoval(Runnable action) {
        boolean previous = ALLOW_HARD_REMOVAL.get();
        ALLOW_HARD_REMOVAL.set(true);
        try {
            action.run();
        } finally {
            ALLOW_HARD_REMOVAL.set(previous);
        }
    }

    public static void runAsUntrustedHardRemoval(Runnable action) {
        boolean previous = FORCE_PROTECTION_CHECK.get();
        FORCE_PROTECTION_CHECK.set(true);
        try {
            action.run();
        } finally {
            FORCE_PROTECTION_CHECK.set(previous);
        }
    }

    public static boolean isForcingProtectionCheck() {
        return FORCE_PROTECTION_CHECK.get();
    }

    /** 当前线程是否临时允许硬移除。 */
    static boolean isAllowingHardRemoval() {
        return ALLOW_HARD_REMOVAL.get();
    }

    public static void allowClientRemoval(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }

        double range = clientRestoreRange(maid);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(maid) <= range * range) {
                player.connection.send(new MaidClientRemovalGuardMessage(
                        maid.getId(), maid.getUUID(), MaidClientRemovalGuardMessage.Action.STOP_TRACKING));
            }
        }
    }

    private static boolean isProtectedMaid(EntityMaid maid) {
        try {
            if (maid == null || maid.level().isClientSide()) {
                return false;
            }
            if (maid.getHealth() <= 0.0F || maid.isDeadOrDying()) {
                return false;
            }
            return AnchorCoreProtection.hasAnchorCore(maid);
        } catch (Exception e) {
            Global.LOGGER.error("[MaidSpell] Failed to check anchored maid hard-removal protection", e);
            return false;
        }
    }

    static boolean isHardRemovalReason(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED;
    }

    private static void syncClientEntityRestore(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }

        MaidEntityRestoreMessage message;
        try {
            message = MaidEntityRestoreMessage.remoteSnapshot(maid);
        } catch (RuntimeException e) {
            Global.LOGGER.warn("[MaidSpell] Failed to serialize maid {} for client restore", maid.getUUID(), e);
            return;
        }
        double restoreRange = clientRestoreRange(maid);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(maid) <= restoreRange * restoreRange) {
                player.connection.send(message);
            }
        }
    }

    private static double clientRestoreRange(EntityMaid maid) {
        return Math.max(128.0D, maid.getType().clientTrackingRange() * 16.0D + 16.0D);
    }

    private static void syncPendingClientRestores(MinecraftServer server) {
        if (PENDING_CLIENT_RESTORE.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Integer> entry : PENDING_CLIENT_RESTORE.entrySet()) {
            UUID maidId = entry.getKey();
            EntityMaid maid = TRACKED_MAIDS.get(maidId);
            if (maid == null || !isProtectedMaid(maid) || maid.isRemoved() || !(maid.level() instanceof ServerLevel level) || level.getServer() != server
                    || level.getEntity(maid.getUUID()) != maid || level.getEntity(maid.getId()) != maid) {
                PENDING_CLIENT_RESTORE.remove(maidId);
                continue;
            }

            syncClientEntityRestore(maid);
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks <= 0) {
                PENDING_CLIENT_RESTORE.remove(maidId);
            } else {
                PENDING_CLIENT_RESTORE.replace(maidId, entry.getValue(), remainingTicks);
            }
        }
    }

    private static void queueClientRestore(EntityMaid maid) {
        PENDING_CLIENT_RESTORE.put(maid.getUUID(), CLIENT_RESTORE_RESEND_TICKS);
    }

    private static void logBlockedAttempt(EntityMaid maid, Entity.RemovalReason reason, String path) {
        if (shouldLog(maid)) {
            Global.LOGGER.warn("[MaidSpell] Blocked hard removal path={} maid={} reason={} (anchor_core protection)",
                    path, maid.getUUID(), reason);
        }
    }

    public static void logBlockedDirectRemovalFlagWrite(EntityMaid maid, Entity.RemovalReason attempted) {
        Entity.RemovalReason current = maid.getRemovalReason();
        if (current != attempted && shouldLog(maid)) {
            Global.LOGGER.warn("[MaidSpell] Blocked direct removalReason write maid={} old={} attempted={} (anchor_core protection)",
                    maid.getUUID(), current, attempted);
        }
    }

    private static boolean shouldLog(EntityMaid maid) {
        long gameTime = maid.level() instanceof ServerLevel level ? level.getGameTime() : 0L;
        Long lastWarnTick = LAST_WARN_TICK.get(maid.getUUID());
        if (lastWarnTick != null && gameTime - lastWarnTick < WARN_INTERVAL_TICKS) {
            return false;
        }
        LAST_WARN_TICK.put(maid.getUUID(), gameTime);
        return true;
    }
}
