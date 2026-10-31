package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidModelPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidTaskPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.RequestEffectPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SendNameTagPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SetMaidSoundIdPackage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketOpenInventory;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketHudUpdate;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidList;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Packets;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import io.netty.buffer.Unpooled;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 末影腰包（专用服）：按女仆 UUID 请求的授权校验、远程会话的建立与失效、请求限流、列表与名字的编码上限、
 * 远程界面里的车万女仆操作（切任务、改配置、换模型、改名、换音效、看效果）作用到远处或其他维度的女仆，
 * 别人伪造实体 ID 打不中。
 *
 * <p>另含其它末影腰包测试类共用的工具：造戴腰包的女仆、读远程会话、走完远程打开流程。
 */
@DisplayName("末影腰包：授权、远程会话与编码上限")
@Scene("tlms")
class EnderPocketTest {
    static final String POCKET = NS + "ender_pocket";
    static final String ANCHOR = NS + "anchor_core";
    /** 远程女仆与玩家的距离：超出视距与实体追踪范围。 */
    static final int REMOTE = 1000;

    // ---- 工具 ----

    /** 在 level 的 pos 处放一只主人为 owner、戴着末影腰包的女仆（强加载所在区块，测试结束时解除）；服务端线程。 */
    static EntityMaid pocketMaid(Stage ctx, ServerLevel level, BlockPos pos, Player owner, String... extraBaubles) {
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        ctx.cleanup(() -> level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false));
        if (level.getBlockState(pos.below()).isAir()) {
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        }
        EntityMaid maid = (EntityMaid) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(Actors.MAID)).create(level);
        maid.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        maid.setNoAi(true);
        maid.setPersistenceRequired();
        maid.setTame(true, false);
        maid.setOwnerUUID(owner.getUUID());
        level.addFreshEntity(maid);
        ctx.cleanup(() -> Actors.cleanupEntity(maid));
        List<String> baubles = new ArrayList<>(List.of(POCKET));
        baubles.addAll(List.of(extraBaubles));
        Maids.equip(maid, baubles.toArray(String[]::new));
        return maid;
    }

    /** 相对原点 (dx, dy, dz) 处的末影腰包女仆；服务端线程。 */
    static EntityMaid pocketMaid(Stage ctx, Player owner, int dx, int dy, int dz, String... extraBaubles) {
        return pocketMaid(ctx, ctx.level(), ctx.rel(dx, dy, dz), owner, extraBaubles);
    }

    /** 下界里与场地同 x、z 的落点（y=70）。 */
    static BlockPos netherSpot(Stage ctx) {
        return new BlockPos(ctx.originX(), 70, ctx.originZ());
    }

    static List<UUID> listed(ServerPlayer player) {
        return EnderPocketService.getPlayerEnderPocketMaids(player).stream().map(EnderPocketService.EnderPocketMaidInfo::getMaidUUID).toList();
    }

    /** 当前远程会话（没有时为 null）。 */
    static Object session(ServerPlayer player) {
        Map<UUID, ?> sessions = Reflect.field(null, EnderPocketService.class, "REMOTE_SESSIONS");
        return sessions.get(player.getUUID());
    }

    private static UUID sessionId(Object session) {
        return Reflect.field(session, session.getClass(), "sessionId");
    }

    /** 走完"请求打开 → 客户端确认代理就绪"：模拟玩家没有客户端，这里直接用会话 ID 确认；服务端线程。 */
    static boolean openRemote(ServerPlayer player, EntityMaid maid) {
        if (!EnderPocketService.openMaidInventory(player, maid.getUUID())) {
            return false;
        }
        Object session = session(player);
        return session != null && EnderPocketService.completeRemoteOpen(player, sessionId(session));
    }

    static MutableComponent nestedName(int depth) {
        MutableComponent root = Component.literal("n");
        MutableComponent current = root;
        for (int i = 0; i < depth; i++) {
            MutableComponent next = Component.literal("n");
            current.append(next);
            current = next;
        }
        return root;
    }

    // ---- 测试 ----

    /** 列表只有自己的、存活的、戴末影腰包的女仆；别人的和没戴的打不开。 */
    @DisplayName("列表只含自己的、存活的、戴末影腰包的女仆，别人的和没戴的打不开")
    @Test
    void listShowsOwnLivingPocketMaids(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketOwner", 0, 0, -3);
            ServerPlayer stranger = Owners.visitor(ctx, "TlmsPocketStranger", 0, 0, -5);
            EntityMaid listed = pocketMaid(ctx, owner, 2, 0, 0);
            EntityMaid bare = Owners.maid(ctx, owner, -2, 0, 0);
            EntityMaid dead = pocketMaid(ctx, owner, 0, 0, 2);
            EntityMaid others = pocketMaid(ctx, stranger, 0, 0, 4);
            Maids.kill(dead);
            ctx.check(listed(owner)).as("主人的末影腰包列表").isEqualTo(List.of(listed.getUUID()));
            ctx.check(EnderPocketService.openMaidInventory(owner, bare.getUUID())).as("打开没戴末影腰包的自有女仆").isFalse();
            ctx.check(EnderPocketService.openMaidInventory(owner, dead.getUUID())).as("打开已死亡的女仆").isFalse();
            ctx.check(EnderPocketService.openMaidInventory(owner, others.getUUID())).as("打开别人的女仆").isFalse();
            ctx.check(EnderPocketService.openMaidInventory(owner, listed.getUUID())).as("打开自己的末影腰包女仆").isTrue();
        });
    }

    @DisplayName("女仆摘下末影腰包后，远程会话失效且再点按钮打不开")
    @Test
    void sessionEndsWhenPocketRemoved(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sessionEnds(Stage.flat(game, plot, reporter), "pocket");
    }

    @DisplayName("女仆睡觉后，远程会话失效且再点按钮打不开")
    @Test
    void sessionEndsWhenMaidSleeps(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sessionEnds(Stage.flat(game, plot, reporter), "sleep");
    }

    @DisplayName("女仆死亡后，远程会话失效且再点按钮打不开")
    @Test
    void sessionEndsWhenMaidDies(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sessionEnds(Stage.flat(game, plot, reporter), "death");
    }

    @DisplayName("女仆被别人收养后，远程会话失效且再点按钮打不开")
    @Test
    void sessionEndsWhenMaidAdopted(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sessionEnds(Stage.flat(game, plot, reporter), "adopted");
    }

    @DisplayName("女仆被收进魂符后，远程会话失效且再点按钮打不开")
    @Test
    void sessionEndsWhenMaidInSlab(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        sessionEnds(Stage.flat(game, plot, reporter), "slab");
    }

    /** 已打开的远程会话在女仆摘下腰包、睡觉、死亡、被别人收养、被收进魂符后失效；再点残留按钮打不开，也不报错。 */
    private static void sessionEnds(Stage ctx, String how) {
        ServerPlayer owner = ctx.get(() -> Owners.visitor(ctx, "TlmsPocketOwner", 0, 0, -3));
        ServerLevel nether = ctx.get(() -> ctx.server().getLevel(Level.NETHER));
        EntityMaid maid = ctx.get(() -> pocketMaid(ctx, nether, netherSpot(ctx), owner));
        UUID id = maid.getUUID();
        ctx.await(30, "下界女仆加载并出现在列表里", () -> nether.getEntity(maid.getUUID()) == maid && listed(owner).contains(id));
        ctx.run(() -> {
            ctx.check(openRemote(owner, maid)).as("建立远程会话").isTrue();
            ctx.check(EnderPocketService.isGuiStillValid(owner, maid)).as("会话建立后界面有效").isTrue();
            switch (how) {
                case "pocket" -> Maids.takeOffAll(maid);
                case "sleep" -> maid.startSleeping(maid.blockPosition());
                case "death" -> Maids.kill(maid);
                case "adopted" -> maid.setOwnerUUID(UUID.nameUUIDFromBytes("TlmsPocketAdopter".getBytes()));
                default -> Players.storeInSlab(owner, maid);
            }
            ctx.check(EnderPocketService.resolveRemoteMaid(owner, maid.getId())).as(how + " 后远程会话仍解析到女仆").isNull();
            ctx.check(EnderPocketService.isGuiStillValid(owner, maid)).as(how + " 后界面仍有效").isFalse();
            ctx.check(EnderPocketService.openMaidInventory(owner, id)).as(how + " 后再点列表按钮打开").isFalse();
            ctx.check(EnderPocketService.teleportToMaid(owner, id)).as(how + " 后再点传送按钮").isFalse();
        });
    }

    /** 会话过期（10 分钟不操作）后不再解析到女仆。 */
    @DisplayName("远程会话 10 分钟不操作后过期，不再解析到女仆")
    @Test
    void sessionExpires(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketOwner", 0, 0, -3);
            EntityMaid maid = pocketMaid(ctx, owner, 0, 0, 3);
            ctx.check(openRemote(owner, maid)).as("建立远程会话").isTrue();
            Object session = session(owner);
            try {
                var field = session.getClass().getDeclaredField("expiresAtNanos");
                field.setAccessible(true);
                field.setLong(session, System.nanoTime() - 1);
            } catch (ReflectiveOperationException e) {
                throw ctx.fail("无法改写会话截止时间: " + e);
            }
            ctx.check(EnderPocketService.resolveRemoteMaid(owner, maid.getId())).as("过期后远程会话仍解析到女仆").isNull();
        });
    }

    /** 1 秒内连发打开请求：只有第一次真的建立会话，其余被限流丢弃，不报错。 */
    @DisplayName("1 秒内连发打开请求被限流，只有第一次建立会话")
    @Test
    void openRequestsRateLimited(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketRate", 0, 0, -3);
            EntityMaid maid = pocketMaid(ctx, owner, 0, 0, 3);
            C2SEnderPocketOpenInventory request = new C2SEnderPocketOpenInventory(maid.getUUID());
            request.handle(owner);
            Object first = session(owner);
            ctx.check(first).as("第一次请求建立的会话").isNotNull();
            for (int i = 0; i < 20; i++) {
                request.handle(owner);
            }
            ctx.check(session(owner) == first).as("连发 20 次后会话未被替换").isTrue();
        });
    }

    /** 70 只戴腰包的女仆：列表消息截到 64 条，编码解码不出错。 */
    @DisplayName("70 只戴腰包的女仆，列表与状态栏消息截到 64 条且编解码正常")
    @Test
    void listCappedAt64(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketMany", 0, 0, -3);
            for (int i = 0; i < 70; i++) {
                pocketMaid(ctx, owner, i % 10 - 5, 0, i / 10);
            }
            List<EnderPocketService.EnderPocketMaidInfo> infos = EnderPocketService.getPlayerEnderPocketMaids(owner);
            ctx.check(infos.size()).as("服务端收集到的女仆数").isEqualTo(70);
            S2CEnderPocketMaidList list = roundTrip(ctx, S2CEnderPocketMaidList.STREAM_CODEC, new S2CEnderPocketMaidList(infos, false));
            ctx.check(list.maidInfos().size()).as("列表消息解码后的条数").isEqualTo(64);
            S2CEnderPocketHudUpdate hud = roundTrip(ctx, S2CEnderPocketHudUpdate.STREAM_CODEC, new S2CEnderPocketHudUpdate(infos));
            ctx.check(hud.maidInfos().size()).as("状态栏消息解码后的条数").isEqualTo(64);
        });
    }

    /** 名字超过 64 个字符：编码解码不出错，名字原样保留。 */
    @DisplayName("超过 64 个字符的女仆名编解码不出错，名字原样保留")
    @Test
    void longNameKept(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketName", 0, 0, -3);
            EntityMaid maid = pocketMaid(ctx, owner, 0, 0, 3);
            maid.setCustomName(Component.literal("名".repeat(100)));
            S2CEnderPocketMaidList list = roundTrip(ctx, S2CEnderPocketMaidList.STREAM_CODEC,
                    new S2CEnderPocketMaidList(EnderPocketService.getPlayerEnderPocketMaids(owner), false));
            ctx.check(list.maidInfos().size()).as("解码后的条数").isEqualTo(1);
            ctx.check(list.maidInfos().getFirst().maidName.getString()).as("解码后的名字").isEqualTo("名".repeat(100));
        });
    }

    /** 名字是翻译组件：编码解码后仍是同一个翻译键，由客户端按自身语言解析，而不是服务端解析好的文本。 */
    @DisplayName("翻译组件的女仆名编解码后仍是同一个翻译键")
    @Test
    void translatableNameStaysTranslatable(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketI18n", 0, 0, -3);
            EntityMaid maid = pocketMaid(ctx, owner, 0, 0, 3);
            String key = "model.touhou_little_maid_spell.test_only.name";
            maid.setCustomName(Component.translatable(key));
            S2CEnderPocketMaidList list = roundTrip(ctx, S2CEnderPocketMaidList.STREAM_CODEC,
                    new S2CEnderPocketMaidList(EnderPocketService.getPlayerEnderPocketMaids(owner), false));
            ctx.check(list.maidInfos().size()).as("解码后的条数").isEqualTo(1);
            ctx.check(list.maidInfos().getFirst().maidName.getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals(key)).as("解码后的名字仍是翻译键 " + key).isTrue();
        });
    }

    /** 嵌套 300 层的名字超过客户端解码的 512 层上限：退化为纯文本后能正常解码；不深的带样式名字保持原样。 */
    @DisplayName("嵌套 300 层的女仆名退化为纯文本，不深的名字保持原样")
    @Test
    void deepNameFlattened(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketDeep", 0, 0, -3);
            EntityMaid maid = pocketMaid(ctx, owner, 0, 0, 3);
            maid.setCustomName(nestedName(300));
            S2CEnderPocketMaidList list = roundTrip(ctx, S2CEnderPocketMaidList.STREAM_CODEC,
                    new S2CEnderPocketMaidList(EnderPocketService.getPlayerEnderPocketMaids(owner), false));
            Component decoded = list.maidInfos().getFirst().maidName;
            ctx.check(decoded.getSiblings().isEmpty()).as("过深名字退化为纯文本").isTrue();
            ctx.check(decoded.getString()).as("退化后的文本").isEqualTo("n".repeat(256));

            Component shallow = nestedName(10);
            Component kept = EnderPocketService.EnderPocketMaidInfo.syncName(shallow, ctx.level().registryAccess());
            ctx.check(kept).as("10 层名字原样保留").isEqualTo(shallow);
        });
    }

    private static <T> T roundTrip(Stage ctx, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), ctx.level().registryAccess());
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    /**
     * 主人在主世界、女仆在下界：远程会话里发来的车万女仆操作包（切任务、改拾取/骑乘配置、换模型、改名、换音效、
     * 请求药水效果）都作用到下界的女仆。
     */
    @DisplayName("跨维度远程会话里的车万女仆操作包都作用到下界的女仆")
    @Test
    void remoteOperationsApplyAcrossDimensions(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer owner = ctx.get(() -> Owners.visitor(ctx, "TlmsPocketRemote", 0, 0, -3));
        ServerLevel nether = ctx.get(() -> ctx.server().getLevel(Level.NETHER));
        EntityMaid maid = ctx.get(() -> pocketMaid(ctx, nether, netherSpot(ctx), owner));
        ctx.await(30, "下界女仆加载并出现在列表里", () -> nether.getEntity(maid.getUUID()) == maid && listed(owner).contains(maid.getUUID()));
        ctx.run(() -> {
            ctx.check(ctx.level().getEntity(maid.getId())).as("主世界按实体 ID 找到下界女仆").isNull();
            ctx.check(openRemote(owner, maid)).as("建立跨维度远程会话").isTrue();
            var context = Packets.from(owner);
            int id = maid.getId();
            MaidTaskPackage.handle(new MaidTaskPackage(id, SpellCombatFarTask.UID), context);
            ctx.check(maid.getTask().getUid()).as("切换后的任务").isEqualTo(SpellCombatFarTask.UID);
            MaidConfigPackage.handle(new MaidConfigPackage(id, maid.isHomeModeEnable(), !maid.isPickup(), !maid.isRideable(), maid.getSchedule()), context);
            ctx.record("pickup/ride", maid.isPickup() + "/" + maid.isRideable());
            ResourceLocation model = ResourceLocation.parse("touhou_little_maid:hakurei_reimu");
            MaidModelPackage.handle(new MaidModelPackage(id, model), context);
            ctx.check(maid.getModelId()).as("换模型后的模型").isEqualTo(model.toString());
            Players.hold(owner, new ItemStack(Items.NAME_TAG));
            SendNameTagPackage.handle(new SendNameTagPackage(id, "远程改名", false), context);
            ctx.check(maid.getName().getString()).as("改名后的名字").isEqualTo("远程改名");
            String sound = maid.getSoundPackId().equals("touhou_little_maid") ? "maidspell_test" : "touhou_little_maid";
            SetMaidSoundIdPackage.handle(new SetMaidSoundIdPackage(id, sound), context);
            ctx.check(maid.getSoundPackId()).as("换音效包后的音效包").isEqualTo(sound);
            RequestEffectPackage.handle(new RequestEffectPackage(id), context);
            ctx.record("note", "请求药水效果未抛异常");
        });
    }

    /** 另一名玩家伪造 Dev 远程女仆的实体 ID 发包：打不中这只女仆，也不给 Dev 的会话续期。 */
    @DisplayName("他人伪造远程女仆的实体 ID 发包打不中女仆，也不给会话续期")
    @Test
    void forgedEntityIdMissesOthersSession(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer owner = ctx.get(() -> Owners.visitor(ctx, "TlmsPocketDev", 0, 0, -3));
        ServerPlayer forger = ctx.get(() -> Owners.visitor(ctx, "TlmsPocketDev2", 0, 0, -5));
        ServerLevel nether = ctx.get(() -> ctx.server().getLevel(Level.NETHER));
        EntityMaid maid = ctx.get(() -> pocketMaid(ctx, nether, netherSpot(ctx), owner));
        ctx.await(30, "下界女仆加载并出现在列表里", () -> nether.getEntity(maid.getUUID()) == maid && listed(owner).contains(maid.getUUID()));
        ctx.run(() -> {
            ctx.check(openRemote(owner, maid)).as("建立跨维度远程会话").isTrue();
            Object session = session(owner);
            if (session == null) {
                throw ctx.fail("跨维度会话未建立");
            }
            long expiry = Reflect.field(session, session.getClass(), "expiresAtNanos");
            ResourceLocation before = maid.getTask().getUid();
            MaidTaskPackage.handle(new MaidTaskPackage(maid.getId(), SpellCombatFarTask.UID), Packets.from(forger));
            ctx.check(maid.getTask().getUid()).as("伪造包之后女仆的任务").isEqualTo(before);
            long after = Reflect.field(session, session.getClass(), "expiresAtNanos");
            ctx.check(after).as("伪造包之后会话的截止时间").isEqualTo(expiry);
        });
    }
}
