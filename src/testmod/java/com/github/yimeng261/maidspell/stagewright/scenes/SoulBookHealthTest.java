package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.mixin.accessor.SynchedEntityDataAccessor;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 魂之书女仆的生命：掉血只认 hurt → actuallyHurt 里结算过的那一笔，死亡只认结算把生命打到 0 后的那一次；
 * 直接改生命、改同步数据、改数据项或直接调 die 都被拒绝。读档恢复的残血照常保留。
 */
@Scene("tlms")
@DisplayName("魂之书女仆的生命保护")
class SoulBookHealthTest {
    private static final String SOUL_BOOK = NS + "soul_book";

    @SuppressWarnings("unchecked")
    private static SynchedEntityData.DataItem<Float> healthEntry(EntityMaid maid) {
        int id = LivingEntityHealthAccessor.maidspell$getHealthAccessor().id();
        return (SynchedEntityData.DataItem<Float>) ((SynchedEntityDataAccessor) maid.getEntityData())
                .maidspell$getItemsById()[id];
    }

    /** 不进世界的魂之书女仆：读档恢复生命只在进世界之前生效，结算与死亡也不受 AI 干扰。 */
    private static EntityMaid detachedMaid(Stage ctx) {
        return prepareDetached(ctx, new EntityMaid(ctx.level()));
    }

    private static <T extends EntityMaid> T prepareDetached(Stage ctx, T maid) {
        BlockPos pos = ctx.rel(0, 0, 0);
        maid.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        maid.getMaidBauble().setStackInSlot(0, new ItemStack(MaidSpellItems.SOUL_BOOK.get()));
        ctx.cleanup(() -> SoulBookBauble.cleanupMaid(maid.getUUID()));
        return maid;
    }

    /** 跳过魂之书的受伤间隔与原版无敌帧，下一击照常结算。 */
    private static void advancePastSoulBookCooldown(EntityMaid maid) {
        maid.tickCount += Config.soulBookDamageIntervalThreshold + 1;
        maid.invulnerableTime = 0;
    }

    @DisplayName("直接改生命、写同步数据、写数据项与直接调用 die 都被拒绝，卸下魂之书后数据项写入放行")
    @Test
    void rejectsDirectWritesAndDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter)
            throws Throwable {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid m = Actors.stillMaid(ctx, 0, 0, 0);
            Actors.equipBauble(m, SOUL_BOOK);
            return m;
        });
        // 女仆的 die 覆写了父类；父类 die 上的守卫要绕开虚分派单独打一次
        var baseDie = MethodHandles.privateLookupIn(LivingEntity.class, MethodHandles.lookup())
                .findSpecial(LivingEntity.class, "die", MethodType.methodType(void.class, DamageSource.class),
                        LivingEntity.class);
        float before = ctx.get(maid::getHealth);
        int beforeTick = ctx.get(() -> maid.tickCount);
        ctx.run(() -> {
            EntityDataAccessor<Float> accessor = LivingEntityHealthAccessor.maidspell$getHealthAccessor();
            var health = healthEntry(maid);
            DamageSource source = maid.damageSources().magic();
            maid.getCombatTracker().recordDamage(source, before);
            maid.setHealth(0.0F);
            maid.getEntityData().set(accessor, 0.0F, true);
            health.setValue(0.0F);
            health.setValue(Float.NaN);
            maid.setHealth(Float.POSITIVE_INFINITY);
            maid.die(source);
            try {
                baseDie.invoke((LivingEntity) maid, source);
            } catch (Throwable e) {
                throw new IllegalStateException("调用父类 die 失败", e);
            }
            ctx.check(maid.getHealth()).as("各条直接掉血与死亡路径之后的生命").isEqualTo(before);
            ctx.check(maid.getPose()).as("被拒绝的死亡之后的姿态").isNotEqualTo(Pose.DYING);
            ctx.check(maid.isAlive()).as("被拒绝的死亡之后女仆存活").isTrue();
        });
        ctx.ticks(3);
        ctx.run(() -> {
            ctx.check(maid.isAlive()).as("3 tick 后女仆存活").isTrue();
            ctx.check(maid.tickCount).as("被拒绝的死亡之后女仆继续 tick").isGreaterThan(beforeTick);
            Maids.takeOff(maid, 0);
            healthEntry(maid).setValue(before - 1.0F);
            ctx.check(maid.getHealth()).as("卸下魂之书后直接写数据项的生命").isEqualTo(before - 1.0F);
        });
    }

    @DisplayName("伤害按比例封顶、受伤间隔内的下一击被挡，不死图腾照常救命，结算致死后进入正常死亡流程")
    @Test
    void allowsLimitedDamageTotemAndDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = detachedMaid(ctx);
            float initial = maid.getHealth();
            DamageSource source = maid.damageSources().magic();
            // 魂之书对第一次受伤本身就有间隔，先挨一下把间隔起点立起来
            maid.hurt(source, initial * 2.0F);
            advancePastSoulBookCooldown(maid);
            maid.hurt(source, initial * 2.0F);
            float cap = maid.getMaxHealth() * (float) Config.soulBookDamageThresholdPercent;
            float reduced = Math.max(0.0F, initial - cap);
            ctx.check(maid.getHealth()).as("超量伤害封顶后的生命").isEqualTo(reduced);
            maid.invulnerableTime = 0;
            maid.hurt(source, initial * 2.0F);
            ctx.check(maid.getHealth()).as("受伤间隔内第二击之后的生命").isEqualTo(reduced);

            // 进世界之前经原版读档入口恢复残血
            CompoundTag tag = maid.saveWithoutId(new CompoundTag());
            tag.putFloat("Health", cap / 2.0F);
            maid.load(tag);
            ctx.check(maid.getHealth()).as("读档恢复的残血").isEqualTo(cap / 2.0F);

            maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            advancePastSoulBookCooldown(maid);
            maid.hurt(source, initial * 2.0F);
            ctx.check(maid.getHealth()).as("不死图腾救下后的生命").isEqualTo(1.0F);
            ctx.check(maid.getMainHandItem().isEmpty()).as("不死图腾被消耗").isTrue();
            ctx.check(maid.isAlive()).as("不死图腾救下后女仆存活").isTrue();

            maid.removeAllEffects();
            maid.setAbsorptionAmount(0.0F);
            advancePastSoulBookCooldown(maid);
            maid.hurt(source, initial * 2.0F);
            ctx.check(maid.getHealth()).as("结算致死后的生命").isEqualTo(0.0F);
            ctx.check(maid.getPose()).as("结算致死后的姿态").isEqualTo(Pose.DYING);
        });
    }

    @DisplayName("读档恢复残血先于读取饰品，读档后魂之书保护照常生效")
    @Test
    void preservesHealthAcrossSaveAndLoad(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            EntityMaid maid = detachedMaid(ctx);
            CompoundTag tag = maid.saveWithoutId(new CompoundTag());
            tag.putFloat("Health", 7.0F);
            EntityMaid loaded = new EntityMaid(ctx.level());
            loaded.load(tag);
            ctx.check(loaded.getHealth()).as("读档后的生命").isEqualTo(7.0F);
            ctx.check(loaded.getMaidBauble().getStackInSlot(0).is(MaidSpellItems.SOUL_BOOK.get()))
                    .as("读档后第一个饰品槽是魂之书").isTrue();
            loaded.setHealth(0.0F);
            ctx.check(loaded.getHealth()).as("读档后直接改生命为 0 之后的生命").isEqualTo(7.0F);
        });
    }

    @DisplayName("同步数据回调里的写入许可不会落到另一位女仆，中断的结算也不会放行之后的直接写入")
    @Test
    void scopesWritesAcrossSyncCallbacks(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            CallbackMaid maid = prepareDetached(ctx, new CallbackMaid(ctx.level()));
            EntityMaid other = detachedMaid(ctx);
            DamageSource source = maid.damageSources().magic();
            float before = maid.getHealth();
            float otherBefore = other.getHealth();
            maid.hurt(source, 1.0F);
            advancePastSoulBookCooldown(maid);
            IllegalStateException interrupted = new IllegalStateException("同步回调中断");
            maid.afterHealthWrite = () -> {
                other.setHealth(0.0F);
                healthEntry(other).setValue(0.0F);
                throw interrupted;
            };
            boolean callbackRan = false;
            try {
                maid.hurt(source, 1.0F);
            } catch (IllegalStateException e) {
                if (e != interrupted) throw e;
                callbackRan = true;
            }
            ctx.check(callbackRan).as("许可写入途中触发了同步回调").isTrue();
            ctx.check(other.getHealth()).as("回调里改写的另一位女仆的生命").isEqualTo(otherBefore);
            float lastCommittedHealth = maid.getHealth();
            ctx.check(lastCommittedHealth).as("通知回调之前已写入的生命").isLessThan(before);
            maid.setHealth(before);
            healthEntry(maid).setValue(lastCommittedHealth);
            ctx.check(maid.getHealth()).as("中断之后直接写数据项的生命").isEqualTo(before);
        });
    }

    /** 生命同步数据写入后跑一次回调，模拟在 onSyncedDataUpdated 里改写别的女仆的模组。 */
    private static final class CallbackMaid extends EntityMaid {
        Runnable afterHealthWrite;

        CallbackMaid(Level level) {
            super(level);
        }

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
            super.onSyncedDataUpdated(accessor);
            if (afterHealthWrite != null && accessor == LivingEntityHealthAccessor.maidspell$getHealthAccessor()) {
                Runnable callback = afterHealthWrite;
                afterHealthWrite = null;
                callback.run();
            }
        }
    }
}
