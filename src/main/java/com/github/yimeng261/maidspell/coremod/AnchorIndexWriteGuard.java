package com.github.yimeng261.maidspell.coremod;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AnchorIndexWriteGuard {
    private static final ReferenceQueue<Object> COLLECTED = new ReferenceQueue<>();
    private static final Map<Identity, Index> INDEXES = new ConcurrentHashMap<>();

    private AnchorIndexWriteGuard() {}

    /** 运行时字段名即 Mojang 名；返回受监视的槽位名，不受监视时返回 null。 */
    static String slot(Class<?> owner, String field) {
        String type = owner.getName();
        return switch (type) {
            case "net.minecraft.world.entity.Entity" -> matches(field, "removalReason", "levelCallback");
            case "net.minecraft.world.level.entity.EntityTickList" -> matches(field, "active", "passive", "iterated");
            case "net.minecraft.world.level.entity.EntityLookup" -> matches(field, "byId", "byUuid");
            case "net.minecraft.world.level.entity.EntitySection" -> matches(field, "storage");
            case "net.minecraft.world.level.entity.EntitySectionStorage" -> matches(field, "sections", "sectionIds");
            case "net.minecraft.util.ClassInstanceMultiMap" -> matches(field, "allInstances", "byClass");
            case "net.minecraft.world.level.entity.PersistentEntitySectionManager" -> matches(field, "knownUuids", "visibleEntityStorage", "sectionStorage");
            case "net.minecraft.world.level.entity.TransientEntitySectionManager" -> matches(field, "entityStorage", "sectionStorage");
            case "net.minecraft.server.level.ChunkMap" -> matches(field, "entityMap");
            case "net.minecraft.server.level.ServerLevel" -> matches(field, "entityTickList", "entityManager", "navigatingMobs");
            case "net.minecraft.client.multiplayer.ClientLevel" -> matches(field, "tickingEntities", "entityStorage");
            default -> null;
        };
    }

    private static String matches(String value, String... names) {
        for (String name : names) {
            if (value.equals(name)) return name;
        }
        return null;
    }

    static boolean protects(Object value) {
        if (value instanceof AnchorTrackedIndexEntry entry) value = entry.maidspell$trackedEntity();
        if (value instanceof EntityMaid maid) {
            return maid.level().isClientSide() ? ClientMaidRemovalGuard.protectsIndex(maid)
                    : MaidHardRemovalProtection.isRegisteredForIndexGuard(maid) && !MaidHardRemovalProtection.isRemovalAuthorized(maid);
        }
        if (value instanceof EntitySection<?> section) {
            return section.getEntities().anyMatch(entry -> protects(entry) || AnchorWriteHooks.blockSectionEntryRemoval(entry));
        }
        if (value instanceof AnchorSectionList<?> entries) return entries.stream().anyMatch(AnchorWriteHooks::blockSectionEntryRemoval);
        return value instanceof UUID uuid && MaidHardRemovalProtection.isRegisteredForIndexGuard(uuid);
    }

    static boolean allowFieldWrite(Object owner, String field, Object candidate, boolean plainSet, Object current) {
        if (owner == null || field == null) return true;
        String slot = slot(owner instanceof Entity ? Entity.class : owner.getClass(), field);
        if (slot == null) return true;
        if (slot.equals("removalReason")) {
            return AnchorWriteHooks.allowDirectRemovalFlagWrite((Entity) owner, (Entity.RemovalReason) candidate);
        }
        if (slot.equals("levelCallback")) {
            return AnchorWriteHooks.allowDirectLevelCallbackWrite((Entity) owner, (EntityInLevelCallback) candidate);
        }
        if (owner instanceof AnchorTickIndex tick) return allowTickWrite(tick, slot, candidate, plainSet);
        if (owner instanceof AnchorSectionContents<?> contents && contents.maidspell$isSectionContents()) {
            return candidate == current;
        }
        if (slot.equals("storage") && current instanceof AnchorSectionContents<?> contents
                && contents.maidspell$isSectionContents()) {
            return candidate == current;
        }

        // 叶子索引只要锚定条目（及其身份）在新值里原样保留，仍允许整体替换。
        if (current instanceof Map<?, ?> map) {
            if (containsProtected(map) && (!(candidate instanceof Map<?, ?> replacement) || map.entrySet().stream()
                    .anyMatch(entry -> protects(entry.getValue()) && replacement.get(entry.getKey()) != entry.getValue()))) return false;
            if (plainSet) observe(owner, slot, candidate);
            return true;
        }
        if (current instanceof Collection<?> collection) {
            if (collection.stream().filter(value -> protectsElement(owner, slot, value))
                    .anyMatch(value -> !(candidate instanceof Collection<?> replacement) || !replacement.contains(value))) return false;
            if (plainSet) observe(owner, slot, candidate);
            return true;
        }

        // 有锚定女仆时不允许外部代码替换世界持有的索引对象；原版的初始化与生命周期写入不经过这个调用点。
        if (!MaidHardRemovalProtection.hasRegisteredAnchors()
                && !(net.neoforged.fml.loading.FMLEnvironment.dist.isClient() && ClientMaidRemovalGuard.hasProtectedIndexes())) {
            if (plainSet) observe(owner, slot, candidate);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean allowTickWrite(AnchorTickIndex tick, String slot, Object candidate, boolean plainSet) {
        var previous = tick.maidspell$activeIndex();
        if (!containsProtected(previous)) {
            if (plainSet) observe(tick, slot, candidate);
            return true;
        }
        return switch (slot) {
            case "passive" -> candidate == tick.maidspell$passiveIndex();
            case "iterated" -> false;
            case "active" -> {
                if (candidate == previous) yield true;
                if (!plainSet || candidate != tick.maidspell$passiveIndex() || candidate == tick.maidspell$iteratedIndex()
                        || !(candidate instanceof Int2ObjectMap<?> staging)) yield false;
                Int2ObjectMap<Entity> next = (Int2ObjectMap<Entity>) staging;
                if (retainProtectedTicks(previous, next)) {
                    // 暂存表到这里之前只有调用方持有：在世界线程上一次发布两个引用，调用方随后对 passive 的赋值就成了空操作。
                    tick.maidspell$publishTickIndexes(next, previous);
                    observe(tick, "active", next);
                    observe(tick, "passive", previous);
                }
                yield false;
            }
            default -> true;
        };
    }

    static <T> boolean retainEntries(Int2ObjectMap<T> previous, Int2ObjectMap<T> next,
                                     java.util.function.Predicate<? super T> protectedEntry) {
        for (var entry : previous.int2ObjectEntrySet()) {
            if (protectedEntry.test(entry.getValue()) && next.containsKey(entry.getIntKey())
                    && next.get(entry.getIntKey()) != entry.getValue()) return false;
        }
        for (var entry : previous.int2ObjectEntrySet()) {
            if (protectedEntry.test(entry.getValue()) && !next.containsKey(entry.getIntKey())) {
                next.put(entry.getIntKey(), entry.getValue());
            }
        }
        return true;
    }

    private static boolean retainProtectedTicks(Int2ObjectMap<Entity> previous, Int2ObjectMap<Entity> next) {
        for (Entity entity : previous.values()) {
            if (protects(entity) && !MaidHardRemovalProtection.isOnWorldThread(entity)) return false;
        }
        return retainEntries(previous, next, AnchorIndexWriteGuard::protects);
    }

    static void observe(Object owner, String field, Object value) {
        if (owner == null || field == null || !(value instanceof Map<?, ?> || value instanceof Collection<?>)) return;
        String slot = slot(owner.getClass(), field);
        if (slot == null) return;
        expunge();
        INDEXES.put(new Identity(value, COLLECTED), new Index(new WeakReference<>(owner), new WeakReference<>(value), slot, false));
    }

    static void observeView(Object parent, Object value) {
        if (!(value instanceof Collection<?> || value instanceof Iterator<?> || value instanceof Map.Entry<?, ?>)) return;
        Index from = find(parent);
        if (from != null && parent != value) {
            INDEXES.put(new Identity(value, COLLECTED), new Index(from.owner(), from.root(), from.slot(), true));
        }
    }

    static boolean isObserved(Object value) { return !(value instanceof AnchorSectionList<?>) && find(value) != null; }

    static boolean allowMutation(String name, Object[] args) {
        Index index = find(args[0]);
        if (index == null) return true;
        Object root = index.root().get();
        if (root == null) return true;
        if (index.owner().get() instanceof AnchorTickIndex tick) {
            if (tick.maidspell$activeIndex() != root && tick.maidspell$iteratedIndex() != root) return true;
        }
        if (!index.view() && args.length > 1 && root instanceof Map<?, ?> map) {
            if (name.equals("remove") || name.equals("put") || name.equals("replace") || name.equals("compute")
                    || name.equals("computeIfPresent") || name.equals("merge")) {
                Object existing = map.get(args[1]);
                return !protects(existing);
            }
            if (name.equals("putIfAbsent") || name.equals("computeIfAbsent")) return true;
        }
        Object owner = index.owner().get();
        if (!index.view() && root instanceof Collection<?> collection) {
            if ((name.equals("remove") || name.equals("rem")) && args.length > 1
                    && !(args[1] instanceof Integer && root instanceof java.util.List<?>)) {
                return !collection.contains(args[1]) || !protectsElement(owner, index.slot(), args[1]);
            }
            if (name.equals("add") || name.equals("addAll")) return true;
        }
        // 批量修改或经集合视图的修改可能波及传入键以外的条目。
        return root instanceof Collection<?> collection
                ? collection.stream().noneMatch(value -> protectsElement(owner, index.slot(), value))
                : !containsProtected(root);
    }

    private static boolean protectsElement(Object owner, String slot, Object value) {
        if (owner instanceof EntitySectionStorage<?> storage && slot.equals("sectionIds") && value instanceof Long key) {
            return protects(storage.getSection(key));
        }
        return protects(value);
    }

    private static boolean containsProtected(Object index) {
        if (index instanceof Map<?, ?> map) return map.values().stream().anyMatch(AnchorIndexWriteGuard::protects);
        if (index instanceof Collection<?> collection) return collection.stream().anyMatch(AnchorIndexWriteGuard::protects);
        return false;
    }

    private static Index find(Object value) {
        if (value == null || INDEXES.isEmpty()) return null;
        expunge();
        return INDEXES.get(new Identity(value, null));
    }

    private static void expunge() {
        Identity key;
        while ((key = (Identity) COLLECTED.poll()) != null) INDEXES.remove(key);
    }

    private record Index(WeakReference<Object> owner, WeakReference<Object> root, String slot, boolean view) {}

    private static final class Identity extends WeakReference<Object> {
        private final int hash;

        private Identity(Object value, ReferenceQueue<Object> queue) {
            super(value, queue);
            this.hash = System.identityHashCode(value);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            return other == this || other instanceof Identity identity && get() != null && get() == identity.get();
        }
    }
}
