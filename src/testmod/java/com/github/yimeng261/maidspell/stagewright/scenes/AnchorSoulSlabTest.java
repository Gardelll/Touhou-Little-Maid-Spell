package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.network.message.MaidEntityRestoreMessage;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import io.netty.buffer.Unpooled;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 魂符与锚定女仆（专用服）：魂符收放由服务端说了算，收走时注销旧实例、放出时登记新实例；
 * 拦下的硬移除不再把女仆塞进魂符；恢复包带上属性，生命值不被截断。
 */
@Scene("tlms")
@DisplayName("锚定核心：魂符收放与恢复包（专用服）")
class AnchorSoulSlabTest {
    @DisplayName("主人用魂符收放锚定女仆：收走注销旧实例，放出登记新实例，UUID 被占用时放出失败并保留魂符")
    @Test
    void anchoredMaidSlabRoundTrip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            var level = ctx.level();
            ServerPlayer owner = Owners.visitor(ctx, "TlmsSlabOwner", 2, 0, 0);
            EntityMaid maid = InitEntities.MAID.get().create(level);
            if (maid == null) throw ctx.fail("无法创建女仆");
            BlockPos releasePos = ctx.rel(0, -1, 0);
            maid.moveTo(Vec3.atBottomCenterOf(releasePos.above()));
            maid.setNoAi(true);
            maid.tame(owner);
            maid.getMaidBauble().setStackInSlot(0, new ItemStack(MaidSpellItems.ANCHOR_CORE.get()));
            UUID uuid = maid.getUUID();
            try {
                ctx.check(level.addFreshEntity(maid)).as("女仆进入世界").isTrue();
                MaidHardRemovalProtection.rememberIfProtected(maid);
                ctx.check(maid.isOwnedBy(owner)).as("女仆认出世界里的测试主人").isTrue();
                ctx.check(MaidHardRemovalProtection.isRegisteredForIndexGuard(maid)).as("收走前锚定保护已登记").isTrue();
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(InitItems.SMART_SLAB_EMPTY.get()));
                ctx.check(maid.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction()).as("女仆处理主手交互").isTrue();

                ItemStack filledSlab = owner.getMainHandItem();
                ctx.check(filledSlab.is(InitItems.SMART_SLAB_HAS_MAID.get())).as("主人拿到装有女仆的魂符").isTrue();
                CustomData stored = filledSlab.get(InitDataComponent.MAID_INFO);
                ctx.check(stored == null ? null : stored.copyTag().getUUID("UUID")).as("魂符里的女仆 UUID").isEqualTo(uuid);
                ctx.check(maid.isRemoved() && level.getEntity(uuid) == null).as("收走后原实例离开世界").isTrue();
                ctx.check(MaidHardRemovalProtection.isRegisteredForIndexGuard(maid)).as("收走后旧实例仍登记").isFalse();

                ItemStack duplicate = filledSlab.copy();
                ctx.check(release(owner, releasePos).consumesAction()).as("主人放出女仆").isTrue();
                ctx.check(owner.getMainHandItem().is(InitItems.SMART_SLAB_EMPTY.get())).as("放出后魂符变空").isTrue();
                if (!(level.getEntity(uuid) instanceof EntityMaid released) || released == maid || !released.isOwnedBy(owner)) {
                    throw ctx.fail("放出没有重建主人名下的女仆：" + level.getEntity(uuid));
                }
                ctx.check(AnchorCoreProtection.hasAnchorCore(released)).as("收放一轮后仍戴锚定核心").isTrue();
                ctx.check(MaidHardRemovalProtection.isRegisteredForIndexGuard(released)).as("放出的新实例已登记").isTrue();

                released.discard();
                ctx.check(level.getEntity(uuid) == released && !released.isRemoved()).as("交互结束后收走授权不再生效").isTrue();

                owner.setItemInHand(InteractionHand.MAIN_HAND, duplicate);
                ctx.check(release(owner, releasePos)).as("UUID 被占用时放出的结果").isEqualTo(InteractionResult.FAIL);
                ctx.check(owner.getMainHandItem() == duplicate && level.getEntity(uuid) == released)
                        .as("放出失败后保留魂符与原实体").isTrue();
            } finally {
                if (level.getEntity(uuid) instanceof EntityMaid live) {
                    MaidHardRemovalProtection.runAuthorizedRemoval(live, live::discard);
                }
            }
        });
    }

    @DisplayName("已离开世界的锚定女仆再触发硬移除检查，不会被写进魂符")
    @Test
    void lostAnchoredMaidDoesNotGenerateSlab(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            var level = ctx.level();
            EntityMaid maid = InitEntities.MAID.get().create(level);
            if (maid == null) throw ctx.fail("无法创建女仆");
            BlockPos pos = ctx.rel(0, 0, 0);
            maid.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            maid.getMaidBauble().setStackInSlot(0, new ItemStack(MaidSpellItems.ANCHOR_CORE.get()));
            ctx.check(level.addFreshEntity(maid)).as("女仆进入世界").isTrue();
            MaidHardRemovalProtection.runAuthorizedRemoval(maid, maid::discard);
            ctx.check(maid.isRemoved() && level.getEntity(maid.getUUID()) == null).as("女仆已离开世界").isTrue();

            AtomicBoolean serializedToSlab = new AtomicBoolean();
            Consumer<MaidAndItemTransformEvent.ToItem> listener = event -> {
                if (event.getMaid() == maid && event.getItem().is(InitItems.SMART_SLAB_HAS_MAID.get())) {
                    serializedToSlab.set(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                // 模拟一次待处理的保护检查，而它跟踪的实体已经离开世界。
                MaidHardRemovalProtection.handleBlockedRemoveCall(maid, Entity.RemovalReason.DISCARDED, "stagewright");
                MaidHardRemovalProtection.tick(level.getServer());
                ctx.check(serializedToSlab.get()).as("硬移除检查把离开世界的女仆写进魂符").isFalse();
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
                MaidHardRemovalProtection.forgetRegistration(maid);
            }
        });
    }

    @DisplayName("恢复包带上属性与当前生命值，重复套用后最大生命值与当前生命值都不变")
    @Test
    void restorePacketPreservesMaidHealth(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            var level = ctx.level();
            EntityMaid original = InitEntities.MAID.get().create(level);
            EntityMaid replica = InitEntities.MAID.get().create(level);
            if (original == null || replica == null) throw ctx.fail("无法创建女仆");
            var maxHealth = original.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth == null) throw ctx.fail("女仆没有最大生命值属性");
            maxHealth.setBaseValue(60.0);
            maxHealth.addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "stagewright_affection_bonus"),
                    20.0, AttributeModifier.Operation.ADD_VALUE));
            original.setHealth(73.0F);
            ctx.check(original.getMaxHealth() == 80.0F && original.getHealth() == 73.0F).as("夹具的生命值不是默认值").isTrue();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                MaidEntityRestoreMessage.encode(buffer, MaidEntityRestoreMessage.remoteSnapshot(original));
                var decoded = MaidEntityRestoreMessage.decode(buffer);
                decoded.applySyncedState(replica);
                decoded.applySyncedState(replica);
                ctx.check(replica.getMaxHealth()).as("重复套用恢复包后的最大生命值").isEqualTo(80.0F);
                ctx.check(replica.getHealth()).as("重复套用恢复包后的当前生命值").isEqualTo(73.0F);
            } finally {
                buffer.release();
            }
        });
    }

    private static InteractionResult release(Player owner, BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return owner.getMainHandItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit));
    }
}
