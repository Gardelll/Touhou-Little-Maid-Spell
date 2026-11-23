package com.github.yimeng261.maidspell.coremod;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.mixin.accessor.AnchorEntityCallbackAccessor;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** 把原字节码操作与锚定检查链接在一起，不检查调用者身份。 */
public final class AnchorWriteHooks {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Map<VarHandle, String> HANDLES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final ThreadLocal<EntityAccess> SECTION_LIFECYCLE_ENTRY = new ThreadLocal<>();

    private AnchorWriteHooks() {}

    public static boolean blockSectionEntryRemoval(Object entity) {
        return entity instanceof EntityMaid maid && SECTION_LIFECYCLE_ENTRY.get() != maid
                && blocksExternalMaidCleanup(maid);
    }

    public static boolean blocksExternalMaidCleanup(EntityMaid maid) {
        return !maid.level().isClientSide() && !MaidHardRemovalProtection.isRemovalAuthorized(maid)
                && (MaidHardRemovalProtection.isRegisteredForIndexGuard(maid) || AnchorCoreProtection.hasAnchorCore(maid));
    }

    public static boolean containsProtectedSectionEntries(EntitySection<?> section) {
        return section != null && section.getEntities().anyMatch(AnchorWriteHooks::blockSectionEntryRemoval);
    }

    /** 原版在移动或已确认的移除中会把实体从旧区段摘下，这里放行这一次。 */
    public static <T extends EntityAccess> boolean removeSectionEntryForLifecycle(EntitySection<T> section, T entity) {
        if (!blockSectionEntryRemoval(entity)) return section.remove(entity);
        EntityAccess previous = SECTION_LIFECYCLE_ENTRY.get();
        SECTION_LIFECYCLE_ENTRY.set(entity);
        try {
            return section.remove(entity);
        } finally {
            if (previous == null) SECTION_LIFECYCLE_ENTRY.remove();
            else SECTION_LIFECYCLE_ENTRY.set(previous);
        }
    }

    public static boolean allowDirectRemovalFlagWrite(Entity entity, Entity.RemovalReason reason) {
        if (entity instanceof EntityMaid maid && blocksExternalMaidCleanup(maid)) {
            MaidHardRemovalProtection.logBlockedDirectRemovalFlagWrite(maid, reason);
            return false;
        }
        return !AnchorIndexWriteGuard.protects(entity);
    }

    public static boolean allowDirectLevelCallbackWrite(Entity entity, EntityInLevelCallback next) {
        return !(entity instanceof EntityMaid maid && blocksExternalMaidCleanup(maid))
                || ((AnchorEntityCallbackAccessor) entity).maidspell$levelCallback() == next;
    }

    public static boolean allowLevelCallbackTransition(Entity entity, EntityInLevelCallback current, EntityInLevelCallback next) {
        if (current == next || !(entity instanceof EntityMaid maid && blocksExternalMaidCleanup(maid))) return true;
        // 原版先挂上回调再发布实体，确认移除后才摘下回调。
        return (current == EntityInLevelCallback.NULL && !entity.isRemoved() && next instanceof AnchorSectionMembership)
                || (entity.isRemoved() && next == EntityInLevelCallback.NULL);
    }

    public static CallSite link(MethodHandles.Lookup caller, String kind, MethodType type, int opcode,
                                String owner, String name) throws ReflectiveOperationException {
        Class<?> receiver = type.parameterType(0);
        MethodHandle original = switch (kind) {
            case "fieldRead" -> caller.findGetter(receiver, name, type.returnType());
            case "fieldWrite" -> caller.findSetter(receiver, name, type.parameterType(1));
            default -> caller.findVirtual(receiver, name, type.dropParameterTypes(0, 1));
        };
        original = original.asType(type);

        if (kind.equals("handleRead")) {
            return new ConstantCallSite(guardReceiver(type, original, observe(original, type, kind, name), "knownHandle"));
        }
        if (kind.equals("view")) {
            return new ConstantCallSite(guardReceiver(type, original, observe(original, type, kind, name), "knownIndex"));
        }
        if (kind.equals("fieldRead") || kind.equals("lookup")) {
            return new ConstantCallSite(observe(original, type, kind, name));
        }

        MethodHandle getter = kind.equals("fieldWrite") && !name.equals("removalReason")
                ? caller.findGetter(receiver, name, type.parameterType(1)) : null;
        MethodHandle test = LOOKUP.findStatic(AnchorWriteHooks.class, "allowed",
                MethodType.methodType(boolean.class, MethodHandle.class, String.class, String.class, Object[].class));
        test = MethodHandles.insertArguments(test, 0, getter, kind, name).asCollector(Object[].class, type.parameterCount());
        MethodHandle guarded = MethodHandles.guardWithTest(test.asType(type.changeReturnType(boolean.class)),
                original, MethodHandles.empty(type));
        if (kind.equals("mutation") || kind.equals("handleWrite")) {
            guarded = guardReceiver(type, original, guarded, kind.equals("mutation") ? "knownIndex" : "knownHandle");
        }
        return new ConstantCallSite(guarded);
    }

    private static MethodHandle guardReceiver(MethodType type, MethodHandle fallback, MethodHandle guarded, String test)
            throws ReflectiveOperationException {
        MethodHandle check = LOOKUP.findStatic(AnchorWriteHooks.class, test, MethodType.methodType(boolean.class, Object.class))
                .asType(MethodType.methodType(boolean.class, type.parameterType(0)));
        return MethodHandles.guardWithTest(check, guarded, fallback);
    }

    private static boolean knownIndex(Object receiver) { return AnchorIndexWriteGuard.isObserved(receiver); }
    private static boolean knownHandle(Object receiver) { return HANDLES.containsKey(receiver); }

    private static MethodHandle observe(MethodHandle operation, MethodType type, String kind, String name)
            throws ReflectiveOperationException {
        MethodHandle bridge = LOOKUP.findStatic(AnchorWriteHooks.class, "observed",
                MethodType.methodType(Object.class, MethodHandle.class, String.class, String.class, Object[].class));
        return MethodHandles.insertArguments(bridge, 0, operation, kind, name)
                .asCollector(Object[].class, type.parameterCount()).asType(type);
    }

    private static Object observed(MethodHandle operation, String kind, String name, Object[] args) throws Throwable {
        Object value = operation.invokeWithArguments(args);
        switch (kind) {
            case "lookup" -> {
                // 只识别调用方已经拿到的字段，这里绝不自行查找或打开字段。
                String slot = args[1] instanceof Field field
                        ? AnchorIndexWriteGuard.slot(field.getDeclaringClass(), field.getName())
                        : AnchorIndexWriteGuard.slot((Class<?>) args[1], (String) args[2]);
                if (slot != null) HANDLES.put((VarHandle) value, slot);
            }
            case "fieldRead" -> AnchorIndexWriteGuard.observe(args[0], name, value);
            case "handleRead" -> AnchorIndexWriteGuard.observe(args[1], HANDLES.get(args[0]), value);
            case "view" -> AnchorIndexWriteGuard.observeView(args[0], value);
            default -> throw new AssertionError(kind);
        }
        return value;
    }

    private static boolean allowed(MethodHandle getter, String kind, String name, Object[] args) throws Throwable {
        return switch (kind) {
            case "fieldWrite" -> AnchorIndexWriteGuard.allowFieldWrite(args[0], name, args[1], true,
                    getter == null || args[0] == null ? null : getter.invoke(args[0]));
            case "handleWrite" -> {
                if (args.length < 3 || args[1] == null) yield true;
                String slot = HANDLES.get(args[0]);
                boolean plainSet = name.equals("set") || name.equals("setVolatile") || name.equals("setRelease") || name.equals("setOpaque");
                Object current = slot == null || slot.equals("removalReason") || args[1] instanceof AnchorTickIndex
                        ? null : ((VarHandle) args[0]).toMethodHandle(VarHandle.AccessMode.GET).invoke(args[1]);
                yield AnchorIndexWriteGuard.allowFieldWrite(args[1], slot, args[args.length - 1], plainSet, current);
            }
            case "mutation" -> AnchorIndexWriteGuard.allowMutation(name, args);
            case "clientRemoval" -> !AnchorIndexWriteGuard.protects(args[0]);
            default -> throw new AssertionError(kind);
        };
    }
}
