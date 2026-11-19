package com.github.yimeng261.maidspell.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端的锚定女仆移除守卫：按客户端世界记下服务端声明受保护的实体编号与 UUID，
 * 只有服务端声明停止跟踪时才放行移除。登记随客户端世界一起回收。
 */
public final class ClientMaidRemovalGuard {
    private static final Map<ClientLevel, Map<Integer, UUID>> TRACKED = new WeakHashMap<>();
    private static final ThreadLocal<Entity> AUTHORIZED = new ThreadLocal<>();

    private ClientMaidRemovalGuard() {}

    private static synchronized Map<Integer, UUID> tracked(ClientLevel level, boolean create) {
        return create ? TRACKED.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>()) : TRACKED.get(level);
    }

    public static void markProtected(int entityId, UUID uuid) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) tracked(level, true).put(entityId, uuid);
    }

    public static void unmark(int entityId, UUID uuid) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Map<Integer, UUID> tracking = tracked(level, false);
        if (tracking != null) tracking.remove(entityId, uuid);
    }

    public static void allowRemovalNow(int entityId, UUID uuid) {
        ClientLevel level = Minecraft.getInstance().level;
        unmark(entityId, uuid);
        if (level == null || !(level.getEntity(entityId) instanceof EntityMaid maid) || !maid.getUUID().equals(uuid)) return;
        Entity previous = AUTHORIZED.get();
        AUTHORIZED.set(maid);
        try {
            level.removeEntity(entityId, Entity.RemovalReason.DISCARDED);
        } finally {
            if (previous == null) AUTHORIZED.remove();
            else AUTHORIZED.set(previous);
        }
    }

    public static boolean shouldBlockRemoval(Entity entity, int entityId) {
        return entity instanceof EntityMaid maid && protectsIndex(maid);
    }

    public static boolean protectsIndex(EntityMaid maid) {
        if (!(maid.level() instanceof ClientLevel level) || AUTHORIZED.get() == maid) return false;
        Map<Integer, UUID> tracking = tracked(level, false);
        return tracking != null && maid.getUUID().equals(tracking.get(maid.getId())) || AnchorCoreProtection.hasAnchorCore(maid);
    }

    public static boolean hasProtectedIndexes() {
        ClientLevel level = Minecraft.getInstance().level;
        Map<Integer, UUID> tracking = level == null ? null : tracked(level, false);
        return tracking != null && !tracking.isEmpty();
    }

    public static boolean isOnClientThread() {
        return Minecraft.getInstance().isSameThread();
    }
}
