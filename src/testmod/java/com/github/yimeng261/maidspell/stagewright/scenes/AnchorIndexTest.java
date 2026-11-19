package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.mixin.accessor.AnchorEntityCallbackAccessor;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntityTickList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.UUID;

/**
 * 锚定女仆的实体索引级原地保护（专用服）：本类里直接写字段、经 VarHandle 改索引的代码与第三方模组一样，
 * 要在启动时被 coremod 发现并改写成经锚定检查的调用。
 */
@Scene("tlms")
@DisplayName("锚定核心：实体索引写入保护（专用服）")
class AnchorIndexTest {
    @DisplayName("经 VarHandle 改移除标记、UUID 表与 tick 表时，登记的女仆条目原样保留，其他实体照常可改")
    @Test
    void protectsRawIndexWrites(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            var world = ctx.level();
            EntityMaid maid = InitEntities.MAID.get().create(world);
            Entity other = EntityType.PIG.create(world);
            if (maid == null || other == null) throw ctx.fail("无法创建测试实体");
            maid.setNoAi(true);
            BlockPos pos = ctx.rel(0, 0, 0);
            maid.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            ctx.check(world.addFreshEntity(maid)).as("女仆进入世界").isTrue();
            MaidHardRemovalProtection.rememberProtected(maid);
            try {
                VarHandle flag = handle(Entity.class, Entity.RemovalReason.class, "removalReason");
                flag.set(maid, Entity.RemovalReason.KILLED);
                ctx.check(maid.isRemoved()).as("直接写移除标记后女仆被标为移除").isFalse();
                var removalField = Entity.class.getDeclaredField("removalReason");
                var removalLookup = MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup());
                VarHandle fieldHandle = removalLookup.unreflectVarHandle(removalField);
                fieldHandle.set(maid, Entity.RemovalReason.DISCARDED);
                ctx.check(maid.isRemoved()).as("经字段反射出的 VarHandle 写移除标记后女仆被标为移除").isFalse();

                EntityLookup<Entity> lookup = new EntityLookup<>();
                lookup.add(maid);
                lookup.add(other);
                VarHandle uuidField = handle(EntityLookup.class, Map.class, "byUuid");
                @SuppressWarnings("unchecked") Map<UUID, Entity> byUuid = (Map<UUID, Entity>) uuidField.get(lookup);
                byUuid.remove(maid.getUUID());
                byUuid.remove(other.getUUID());
                ctx.check(lookup.getEntity(maid.getUUID())).as("直接从 UUID 表删除后的女仆条目").isSameAs(maid);
                ctx.check(lookup.getEntity(other.getUUID())).as("未受保护的条目删除后").isNull();
                byUuid.keySet().clear();
                ctx.check(lookup.getEntity(maid.getUUID())).as("经键集合视图清空后的女仆条目").isSameAs(maid);

                EntityTickList tickList = new EntityTickList();
                tickList.add(maid);
                tickList.add(other);
                VarHandle active = handle(EntityTickList.class, Int2ObjectMap.class, "active");
                VarHandle passive = handle(EntityTickList.class, Int2ObjectMap.class, "passive");
                @SuppressWarnings("unchecked") Int2ObjectMap<Entity> oldActive = (Int2ObjectMap<Entity>) active.get(tickList);
                @SuppressWarnings("unchecked") Int2ObjectMap<Entity> scratch = (Int2ObjectMap<Entity>) passive.get(tickList);
                tickList.forEach(entity -> {
                    if (entity != maid) return;
                    scratch.clear();
                    active.set(tickList, scratch);
                    passive.set(tickList, oldActive);
                });
                ctx.check(tickList.contains(maid)).as("发布的新 tick 表保留女仆").isTrue();
                ctx.check(tickList.contains(other)).as("其他条目被回滚进新 tick 表").isFalse();
                ctx.check(active.get(tickList) == scratch && passive.get(tickList) == oldActive)
                        .as("轮换同时提交 active 与 passive 两个引用").isTrue();
                ctx.check(world.getEntity(maid.getUUID())).as("世界按 UUID 查到的女仆").isSameAs(maid);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法取得实体索引字段", e);
            } finally {
                MaidHardRemovalProtection.forgetRegistration(maid);
                maid.discard();
            }
        });
    }

    @DisplayName("戴锚定核心的女仆：移除标记、世界回调与成批清空 AI 目标都改不动，授权移除照常摘下回调")
    @Test
    void protectsMaidLifecycleState(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            var world = ctx.level();
            var maid = InitEntities.MAID.get().create(world);
            var ordinary = InitEntities.MAID.get().create(world);
            if (maid == null || ordinary == null) throw ctx.fail("无法创建女仆");
            BlockPos pos = ctx.rel(0, 0, 0);
            maid.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            maid.getMaidBauble().setStackInSlot(0, new ItemStack(MaidSpellItems.ANCHOR_CORE.get()));
            ctx.check(world.addFreshEntity(maid)).as("戴锚定核心的女仆进入世界").isTrue();
            var accessor = (AnchorEntityCallbackAccessor) maid;
            try {
                var attached = accessor.maidspell$levelCallback();
                ctx.check(attached != null && attached != EntityInLevelCallback.NULL).as("原版挂上真实的世界回调").isTrue();
                var flag = handle(Entity.class, Entity.RemovalReason.class, "removalReason");
                flag.set(maid, Entity.RemovalReason.KILLED);
                ctx.check(maid.isRemoved()).as("直接写移除标记后女仆被标为移除").isFalse();

                maid.setLevelCallback(EntityInLevelCallback.NULL);
                maid.setLevelCallback(null);
                ctx.check(accessor.maidspell$levelCallback()).as("公开 setter 置空后的世界回调").isSameAs(attached);
                var callback = handle(Entity.class, EntityInLevelCallback.class, "levelCallback");
                callback.set(maid, EntityInLevelCallback.NULL);
                boolean replaced = callback.compareAndSet(maid, attached, null);
                ctx.check(!replaced && accessor.maidspell$levelCallback() == attached).as("经字段写世界回调后仍是原回调").isTrue();

                var testGoal = new Goal() {
                    @Override
                    public boolean canUse() {
                        return false;
                    }
                };
                maid.goalSelector.addGoal(0, testGoal);
                maid.targetSelector.addGoal(0, testGoal);
                int goals = maid.goalSelector.getAvailableGoals().size();
                int targets = maid.targetSelector.getAvailableGoals().size();
                maid.goalSelector.removeAllGoals(goal -> true);
                maid.targetSelector.removeAllGoals(goal -> true);
                ctx.check(maid.goalSelector.getAvailableGoals().size() == goals
                        && maid.targetSelector.getAvailableGoals().size() == targets).as("成批清空后两个 AI 选择器不变").isTrue();
                maid.goalSelector.removeGoal(testGoal);
                ctx.check(maid.goalSelector.getAvailableGoals().size()).as("单独移除一个目标后的目标数").isEqualTo(goals - 1);

                ordinary.goalSelector.addGoal(0, testGoal);
                ordinary.goalSelector.removeAllGoals(goal -> true);
                ctx.check(ordinary.goalSelector.getAvailableGoals().isEmpty()).as("没戴锚定核心的女仆成批清空后为空").isTrue();
                ctx.check(world.getEntity(maid.getUUID())).as("世界按 UUID 查到的女仆").isSameAs(maid);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法取得实体字段", e);
            } finally {
                MaidHardRemovalProtection.runAuthorizedRemoval(maid, maid::discard);
                MaidHardRemovalProtection.forgetRegistration(maid);
            }
            ctx.check(maid.isRemoved() && accessor.maidspell$levelCallback() == EntityInLevelCallback.NULL)
                    .as("授权移除后女仆被移除且回调摘下").isTrue();
        });
    }

    static VarHandle handle(Class<?> owner, Class<?> fieldType, String name) throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findVarHandle(owner, name, fieldType);
    }
}
