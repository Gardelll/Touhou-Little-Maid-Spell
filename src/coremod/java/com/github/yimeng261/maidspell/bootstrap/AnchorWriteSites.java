package com.github.yimeng261.maidspell.bootstrap;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Map;
import java.util.Set;

/**
 * 实体索引写入站点表：识别模组代码里直接读写实体索引字段、经 VarHandle 或集合方法改动这些索引的指令，
 * 改写成以 {@code AnchorWriteHooks.link} 为引导方法的 invokedynamic，由钩子在原操作前做锚定检查。
 */
final class AnchorWriteSites {
    private static final String VAR_HANDLE_OWNER = "java/lang/invoke/VarHandle";
    private static final String LOOKUP_OWNER = "java/lang/invoke/MethodHandles$Lookup";
    private static final Handle LINK = new Handle(Opcodes.H_INVOKESTATIC,
            "com/github/yimeng261/maidspell/coremod/AnchorWriteHooks", "link",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;ILjava/lang/String;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false);

    private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
            Map.entry("net/minecraft/world/entity/Entity", Set.of("removalReason", "levelCallback")),
            Map.entry("net/minecraft/world/level/entity/EntityTickList", Set.of("active", "passive", "iterated")),
            Map.entry("net/minecraft/world/level/entity/EntityLookup", Set.of("byId", "byUuid")),
            Map.entry("net/minecraft/world/level/entity/EntitySection", Set.of("storage")),
            Map.entry("net/minecraft/util/ClassInstanceMultiMap", Set.of("allInstances", "byClass")),
            Map.entry("net/minecraft/world/level/entity/EntitySectionStorage", Set.of("sections", "sectionIds")),
            Map.entry("net/minecraft/world/level/entity/PersistentEntitySectionManager", Set.of("knownUuids", "visibleEntityStorage", "sectionStorage")),
            Map.entry("net/minecraft/world/level/entity/TransientEntitySectionManager", Set.of("entityStorage", "sectionStorage")),
            Map.entry("net/minecraft/server/level/ChunkMap", Set.of("entityMap")),
            Map.entry("net/minecraft/server/level/ServerLevel", Set.of("entityTickList", "entityManager", "navigatingMobs")),
            Map.entry("net/minecraft/client/multiplayer/ClientLevel", Set.of("tickingEntities", "entityStorage")));
    private static final Set<String> MUTATORS = Set.of("add", "addAll", "remove", "removeFirst", "removeLast",
            "removeFirstInt", "removeLastInt", "removeFirstLong", "removeLastLong", "rem", "removeAll", "removeIf", "retainAll", "clear", "put", "putAll",
            "putIfAbsent", "putAndMoveToFirst", "putAndMoveToLast", "replace", "replaceAll", "compute", "computeIfAbsent",
            "computeIfPresent", "merge", "setValue", "set");
    private static final Set<String> VIEWS = Set.of("keySet", "entrySet", "values", "int2ObjectEntrySet",
            "long2ObjectEntrySet", "iterator", "fastIterator", "next", "previous", "subList", "listIterator", "subSet", "headSet", "tailSet");

    private AnchorWriteSites() {}

    static boolean selectsClass(String kind) {
        return kind != null && !kind.equals("mutation") && !kind.equals("view");
    }

    static boolean indexesReferencedBy(String descriptor) {
        return FIELDS.keySet().stream().anyMatch(owner -> descriptor.contains("L" + owner + ";"));
    }

    static boolean indexesOwnedBy(String owner) {
        return FIELDS.containsKey(owner);
    }

    static String kind(AbstractInsnNode insn, boolean inConstructor) {
        if (insn instanceof FieldInsnNode field && FIELDS.getOrDefault(field.owner, Set.of()).contains(field.name)) {
            if (field.getOpcode() == Opcodes.GETFIELD && field.desc.startsWith("L")) return "fieldRead";
            if (!inConstructor && field.getOpcode() == Opcodes.PUTFIELD) return "fieldWrite";
        }
        if (!(insn instanceof MethodInsnNode call) || call.getOpcode() == Opcodes.INVOKESTATIC) return null;
        if (call.desc.equals("()V") && call.name.equals("onClientRemoval")) return "clientRemoval";
        if (LOOKUP_OWNER.equals(call.owner) && (call.name.equals("findVarHandle") || call.name.equals("unreflectVarHandle"))) return "lookup";
        if (VAR_HANDLE_OWNER.equals(call.owner)) return call.name.equals("get") || call.name.equals("getAcquire")
                || call.name.equals("getOpaque") || call.name.equals("getVolatile") ? "handleRead" : "handleWrite";
        if (call.owner.startsWith("java/util/") || call.owner.startsWith("it/unimi/dsi/fastutil/")
                || call.owner.equals("net/minecraft/util/ClassInstanceMultiMap")) {
            if (MUTATORS.contains(call.name)) return "mutation";
            if (VIEWS.contains(call.name) && Type.getReturnType(call.desc).getSort() == Type.OBJECT) return "view";
        }
        return null;
    }

    static boolean isCandidateClass(String name) {
        // 原版自己的写入就是生命周期的权威；库和本模组自己的钩子不改写，测试类除外。
        return !name.startsWith("net/minecraft/") && !name.startsWith("net/neoforged/")
                && !name.startsWith("java/") && !name.startsWith("javax/") && !name.startsWith("jdk/")
                && !name.startsWith("it/unimi/") && !name.startsWith("org/spongepowered/")
                && !name.startsWith("org/objectweb/")
                && (!name.startsWith("com/github/yimeng261/maidspell/") || name.startsWith("com/github/yimeng261/maidspell/stagewright/"));
    }

    static ClassNode rewrite(ClassNode node) {
        node.methods.forEach(method -> {
            for (var insn : method.instructions.toArray()) {
                String kind = kind(insn, method.name.equals("<init>"));
                if (kind == null) continue;
                if (insn instanceof FieldInsnNode field) {
                    String receiver = "L" + field.owner + ";";
                    String desc = kind.equals("fieldRead") ? "(" + receiver + ")" + field.desc : "(" + receiver + field.desc + ")V";
                    method.instructions.set(insn, new InvokeDynamicInsnNode(kind, desc, LINK,
                            field.getOpcode(), field.owner, field.name));
                } else if (insn instanceof MethodInsnNode call && call.getOpcode() != Opcodes.INVOKESPECIAL) {
                    method.instructions.set(insn, new InvokeDynamicInsnNode(kind,
                            "(L" + call.owner + ";" + call.desc.substring(1), LINK, call.getOpcode(), call.owner, call.name));
                }
            }
        });
        return node;
    }
}
