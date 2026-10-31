package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.player.AnchorPendingChangeData;
import com.github.yimeng261.maidspell.player.ChunkLoadingData;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import com.github.yimeng261.maidspell.utils.PersistentEntityLifecycleGuard;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 锚定核心（专用服）：强加载记录跟随女仆所在区块（主人离线时记为待变更）、
 * 死亡或卸下核心时删记录；抓捕、转化、雷击拿不走锚定女仆；卸载再加载、换维度后数据完整。
 * 以及普通生物的交互与转化不受这些保护影响。主人在线的记录见 {@link AnchorIntegratedTest}。
 */
@Scene("tlms")
@DisplayName("锚定核心：强加载记录与保护（专用服）")
class AnchorTest {
    static final String ANCHOR = NS + "anchor_core";

    /** 挂在一个不在线的主人名下、戴锚定核心的无 AI 女仆；服务端线程。 */
    private static EntityMaid anchoredFor(Stage ctx, UUID owner, int dx, int dz) {
        EntityMaid maid = Actors.spawn(ctx, Actors.MAID, dx, 0, dz, true, (EntityMaid m) -> {
            m.setTame(true, false);
            m.setOwnerUUID(owner);
        });
        Actors.equipBauble(maid, ANCHOR);
        return maid;
    }

    static ChunkLoadingData.LevelAndChunkPos here(Entity entity) {
        ChunkPos chunk = entity.chunkPosition();
        return new ChunkLoadingData.LevelAndChunkPos(entity.level().dimension(), chunk.x, chunk.z);
    }

    private static Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>> pending(MinecraftServer server, UUID owner) {
        Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>> out = new HashMap<>();
        AnchorPendingChangeData.get(server).forEach((o, changes) -> {
            if (o.equals(owner)) {
                out.putAll(changes);
            }
        });
        return out;
    }

    /** 离线主人的记录与待变更跟着女仆换区块（女仆从 z=-8 移到 z=8，相隔一个区块宽，区块必然不同）。 */
    @DisplayName("主人离线时，强加载记录与待变更跟着女仆换区块")
    @Test
    void offlineOwnerRecordFollowsChunk(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MinecraftServer server = ctx.server();
        UUID owner = UUID.randomUUID();
        EntityMaid maid = ctx.get(() -> anchoredFor(ctx, owner, 0, -8));
        ctx.await(10, "记录跟到女仆所在区块", () -> here(maid).equals(ChunkLoadingData.getRecord(server, owner, maid.getUUID())));
        ctx.run(() -> {
            ctx.check(pending(server, owner).get(maid.getUUID())).as("离线主人的待变更").isEqualTo(Optional.of(here(maid)));
            BlockPos next = ctx.rel(0, 0, 8);
            maid.teleportTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
        });
        ctx.await(10, "换区块后记录跟上", () -> here(maid).equals(ChunkLoadingData.getRecord(server, owner, maid.getUUID())));
        ctx.run(() -> ctx.check(pending(server, owner).get(maid.getUUID())).as("换区块后的待变更").isEqualTo(Optional.of(here(maid))));
    }

    @DisplayName("主人离线时，锚定女仆死亡后记录被删除")
    @Test
    void offlineOwnerDeathDeletesRecord(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        offlineRecordDeleted(Stage.flat(game, plot, reporter), true);
    }

    @DisplayName("主人离线时，女仆卸下锚定核心后记录被删除")
    @Test
    void offlineOwnerTakeOffDeletesRecord(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        offlineRecordDeleted(Stage.flat(game, plot, reporter), false);
    }

    private static void offlineRecordDeleted(Stage ctx, boolean byDeath) {
        MinecraftServer server = ctx.server();
        UUID owner = UUID.randomUUID();
        EntityMaid maid = ctx.get(() -> anchoredFor(ctx, owner, 0, 0));
        UUID id = maid.getUUID();
        ctx.await(10, "记录建立", () -> ChunkLoadingData.getRecord(server, owner, id) != null);
        ctx.run(() -> {
            if (byDeath) {
                Maids.kill(maid);
            } else {
                Maids.takeOffAll(maid);
            }
        });
        ctx.await(byDeath ? 30 : 10, "记录删除", () -> ChunkLoadingData.getRecord(server, owner, id) == null);
        ctx.run(() -> ctx.check(pending(server, owner).get(id)).as((byDeath ? "死亡" : "卸下核心") + "后的待变更")
                .isEqualTo(Optional.empty()));
    }

    /**
     * 离线主人的锚定女仆过了一轮周期校验仍保持强加载；之后女仆绕过正常移除凭空消失（不触发死亡和卸下，票据和记录都留着），
     * 周期校验在区块实体已加载时判定她不在，释放票据、区块不再被实体票据强加载，并把记录记为待删。
     */
    @DisplayName("锚定女仆绕过正常移除凭空消失后，周期校验释放强加载票据并标记记录待删")
    @Test
    void vanishedMaidTicketReleased(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MinecraftServer server = ctx.server();
        ServerLevel level = ctx.level();
        UUID owner = UUID.randomUUID();
        EntityMaid maid = ctx.get(() -> anchoredFor(ctx, owner, 0, 0));
        UUID id = maid.getUUID();
        ChunkPos chunk = maid.chunkPosition();
        ctx.await(10, "记录建立", () -> ChunkLoadingData.getRecord(server, owner, id) != null);
        ctx.ticks(25);
        ctx.run(() -> {
            ctx.check(AnchorCoreBauble.hasTicket(level.dimension(), id, chunk)).as("离线主人的女仆校验后仍有票据").isTrue();
            ctx.check(entityForced(level, chunk)).as("离线主人的女仆所在区块仍被强加载").isTrue();
            ctx.check(ChunkLoadingData.getRecord(server, owner, id)).as("离线主人的记录").isEqualTo(here(maid));
            // 测试线程的调用栈带有测试引擎的帧，不在锚定核心的可信调用者白名单里，这里按可信的硬移除执行
            MaidHardRemovalProtection.runAllowingHardRemoval(() -> maid.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK));
        });
        ctx.await(30, "票据释放", () -> !AnchorCoreBauble.hasTicket(level.dimension(), id, chunk));
        ctx.run(() -> {
            ctx.check(entityForced(level, chunk)).as("女仆消失后区块仍被实体票据强加载").isFalse();
            ctx.check(ChunkLoadingData.getRecord(server, owner, id)).as("女仆消失后的记录").isNull();
            ctx.check(pending(server, owner).get(id)).as("女仆消失后的待变更").isEqualTo(Optional.empty());
        });
    }

    /** 区块是否挂着任何实体票据的强加载（场地里只有锚定核心会挂实体票据）。 */
    private static boolean entityForced(ServerLevel level, ChunkPos chunk) {
        ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        if (data == null) {
            return false;
        }
        var tickets = data.getEntityForcedChunks();
        return tickets.getTickingChunks().values().stream().anyMatch(chunks -> chunks.contains(chunk.toLong()))
                || tickets.getChunks().values().stream().anyMatch(chunks -> chunks.contains(chunk.toLong()));
    }

    /** A 离线时对这只女仆留了待删记录，同一只女仆改由 B 锚定：B 的记录照常生效。 */
    @DisplayName("原主人 A 留下的待删记录不影响改由新主人 B 锚定后的记录")
    @Test
    void otherOwnersPendingRemovalIgnored(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MinecraftServer server = ctx.server();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        EntityMaid maid = ctx.get(() -> anchoredFor(ctx, a, 0, 0));
        UUID id = maid.getUUID();
        ctx.await(10, "A 的记录建立", () -> ChunkLoadingData.getRecord(server, a, id) != null);
        ctx.run(() -> Maids.takeOffAll(maid));
        ctx.await(10, "A 的记录删除", () -> ChunkLoadingData.getRecord(server, a, id) == null);
        ctx.run(() -> {
            maid.setOwnerUUID(b);
            Actors.equipBauble(maid, ANCHOR);
        });
        ctx.await(10, "B 的记录建立", () -> here(maid).equals(ChunkLoadingData.getRecord(server, b, id)));
        ctx.run(() -> {
            ctx.check(pending(server, b).get(id)).as("B 的待变更").isEqualTo(Optional.of(here(maid)));
            ctx.check(pending(server, a).get(id)).as("A 的待删记录").isEqualTo(Optional.empty());
        });
    }

    /** 抓捕类物品：Goety 秘法超立方左键锚定女仆被拦下，女仆还在、物品没记下实体。 */
    @DisplayName("Goety 秘法超立方左键锚定女仆被拦下，女仆还在、物品没记下实体")
    @Test
    void captureBlocked(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsAnchorOwner", 2, 0, 0);
            EntityMaid maid = Owners.maid(ctx, owner, 0, 0, 0);
            Actors.equipBauble(maid, ANCHOR);
            ctx.check(PersistentEntityLifecycleGuard.shouldBlockCapture(maid)).as("锚定女仆受抓捕保护").isTrue();
            ItemStack tesseract = Actors.stack("goety:esoteric_tesseract");
            if (tesseract.isEmpty()) {
                ctx.record("note", "没装 Goety，只核对保护判定");
                return;
            }
            String before = tesseract.getComponentsPatch().toString();
            boolean handled = tesseract.getItem().onLeftClickEntity(tesseract, owner, maid);
            ctx.check(handled).as("秘法超立方左键被拦下").isTrue();
            ctx.check(maid.isRemoved()).as("女仆被移除").isFalse();
            ctx.check(tesseract.getComponentsPatch().toString()).as("秘法超立方的数据").isEqualTo(before);
        });
    }

    @DisplayName("锚定女仆无法被转化，被雷劈后仍在且保留锚定核心")
    @Test
    void conversionAndLightningBlocked(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = anchoredFor(ctx, UUID.randomUUID(), 0, 0);
            ctx.check(maid.convertTo(EntityType.WITCH, true)).as("锚定女仆转化结果").isNull();
            ServerLevel level = ctx.level();
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            bolt.moveTo(maid.position());
            maid.thunderHit(level, bolt);
            ctx.check(maid.isRemoved()).as("雷击后女仆被移除").isFalse();
            ctx.check(maid.getMaidBauble().getStackInSlot(0).getItem().builtInRegistryHolder().getRegisteredName())
                    .as("雷击后第一格饰品").isEqualTo(ANCHOR);
        });
    }

    /** 背包放一件东西，卸载再加载后饰品、背包都在。 */
    @DisplayName("锚定女仆卸载再加载后，饰品与背包数据完整")
    @Test
    void unloadReloadKeepsData(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid anchored = anchoredFor(ctx, UUID.randomUUID(), 0, 0);
            anchored.getMaidInv().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
            ctx.check(PersistentEntityLifecycleGuard.shouldBlockRemoval(anchored, Entity.RemovalReason.UNLOADED_TO_CHUNK))
                    .as("拦下随区块卸载").isFalse();
            return anchored;
        });
        Entity reloaded = Maids.reload(ctx, maid, 5);
        ctx.run(() -> {
            if (!(reloaded instanceof EntityMaid again)) {
                throw ctx.fail("重新加载后不是女仆：" + reloaded);
            }
            ctx.check(again.getMaidBauble().getStackInSlot(0).getItem().builtInRegistryHolder().getRegisteredName())
                    .as("重新加载后第一格饰品").isEqualTo(ANCHOR);
            ctx.check(again.getMaidInv().getStackInSlot(0).getCount()).as("重新加载后背包里的钻石").isEqualTo(3);
        });
    }

    @DisplayName("锚定女仆换维度后，饰品与背包数据完整，记录跟到下界")
    @Test
    void dimensionChangeKeepsData(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MinecraftServer server = ctx.server();
        UUID owner = UUID.randomUUID();
        EntityMaid there = ctx.get(() -> {
            EntityMaid maid = anchoredFor(ctx, owner, 0, 0);
            maid.getMaidInv().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
            ServerLevel nether = server.getLevel(Level.NETHER);
            ChunkPos target = new ChunkPos(new BlockPos(ctx.rel(0, 0, 0).getX() / 8, 64, ctx.rel(0, 0, 0).getZ() / 8));
            nether.setChunkForced(target.x, target.z, true);
            ctx.cleanup(() -> nether.setChunkForced(target.x, target.z, false));
            Vec3 pos = new Vec3(target.getMiddleBlockX() + 0.5, 128, target.getMiddleBlockZ() + 0.5);
            Entity moved = Maids.copyToDimension(ctx, maid, nether, pos);
            if (!(moved instanceof EntityMaid copied)) {
                throw ctx.fail("换维度后不是女仆：" + moved);
            }
            ctx.cleanup(() -> Actors.cleanupEntity(copied));
            ctx.check(copied.getMaidBauble().getStackInSlot(0).getItem().builtInRegistryHolder().getRegisteredName())
                    .as("换维度后第一格饰品").isEqualTo(ANCHOR);
            ctx.check(copied.getMaidInv().getStackInSlot(0).getCount()).as("换维度后背包里的钻石").isEqualTo(3);
            return copied;
        });
        ctx.await(20, "记录跟到下界", () -> here(there).equals(ChunkLoadingData.getRecord(server, owner, there.getUUID())));
    }

    /** 普通生物：拴绳、命名牌、刷怪蛋右键照常。 */
    @DisplayName("普通生物的拴绳、命名牌、刷怪蛋交互不受锚定保护影响")
    @Test
    void mobInteractionsUnaffected(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer player = Owners.visitor(ctx, "TlmsFarmer", 2, 0, 0);
            Pig pig = Actors.spawn(ctx, "minecraft:pig", 0, 0, 0, true);
            use(player, pig, new ItemStack(Items.LEAD));
            ctx.check(pig.isLeashed()).as("拴绳拴上").isTrue();
            ItemStack tag = new ItemStack(Items.NAME_TAG);
            tag.set(DataComponents.CUSTOM_NAME, Component.literal("TlmsPig"));
            use(player, pig, tag);
            ctx.check(pig.getCustomName() == null ? null : pig.getCustomName().getString()).as("命名牌命名").isEqualTo("TlmsPig");
            int before = ctx.level().getEntitiesOfClass(Pig.class, new AABB(pig.blockPosition()).inflate(4)).size();
            use(player, pig, new ItemStack(Items.PIG_SPAWN_EGG));
            int after = ctx.level().getEntitiesOfClass(Pig.class, new AABB(pig.blockPosition()).inflate(4)).size();
            ctx.check(after).as("对猪用猪刷怪蛋后附近的猪数").isEqualTo(before + 1);
        });
    }

    private static void use(ServerPlayer player, Entity target, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.interactOn(target, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** 普通生物被雷劈照常转化：猪变僵尸猪灵，村民变女巫。 */
    @DisplayName("普通生物被雷劈照常转化：猪变僵尸猪灵，村民变女巫")
    @Test
    void mobConversionsUnaffected(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        if (ctx.get(() -> ctx.level().getDifficulty()) == Difficulty.PEACEFUL) {
            throw ctx.skip("和平难度下村民不会被雷劈成女巫");
        }
        ctx.run(() -> {
            ServerLevel level = ctx.level();
            Pig pig = Actors.spawn(ctx, "minecraft:pig", 0, 0, 0, true);
            Villager villager = Actors.spawn(ctx, "minecraft:villager", 4, 0, 0, true);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            bolt.moveTo(pig.position());
            pig.thunderHit(level, bolt);
            villager.thunderHit(level, bolt);
            AABB box = new AABB(ctx.rel(0, 0, 0)).inflate(8);
            ctx.check(level.getEntities(EntityType.ZOMBIFIED_PIGLIN, box, Mob::isAlive).size()).as("猪转化出的僵尸猪灵").isEqualTo(1);
            ctx.check(level.getEntitiesOfClass(Witch.class, box).size()).as("村民转化出的女巫").isEqualTo(1);
            level.getEntities(EntityType.ZOMBIFIED_PIGLIN, box, Mob::isAlive).forEach(Entity::discard);
            level.getEntitiesOfClass(Witch.class, box).forEach(Entity::discard);
        });
    }
}
