package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.MaidOriginData;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.Holder;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 结构女仆的初始永久效果（专用服，活体女仆）：被清掉后约 1 秒补回，更强的有限同类效果到期后回落到初始等级，
 * 没有来源标记的女仆不会凭空多出效果；保留永久效果的女仆收进魂符再放出，效果仍是永久的。
 * 静态函数的逐项规则见 {@link HiddenRetreatMaidTest}。
 */
@Scene("tlms")
@DisplayName("结构女仆：初始永久效果的维持")
class PresetEffectLiveTest {
    private static final String SOURCE = NS + "relic_sanctum";
    private static final Holder<MobEffect> PRESET = MobEffects.REGENERATION;
    private static final int PRESET_AMPLIFIER = 1;
    /** 维持逻辑每 20 tick 跑一次，多留一点余量。 */
    private static final int MAINTAIN_WAIT = 30;

    @DisplayName("清掉全部效果后约 1 秒内补回初始的永久效果")
    @Test
    void clearedPresetRestored(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> withPreset(Actors.spawn(ctx, Actors.MAID, 0, 0, 0, true), false));
        ctx.run(maid::removeAllEffects);
        ctx.await(MAINTAIN_WAIT, "补回初始效果", () -> permanentPreset(maid));
    }

    @DisplayName("更强的有限同类效果到期后回落到初始等级的永久效果")
    @Test
    void strongerFiniteFallsBack(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> withPreset(Actors.spawn(ctx, Actors.MAID, 0, 0, 0, true), false));
        ctx.run(() -> maid.addEffect(new MobEffectInstance(PRESET, 30, 4)));
        ctx.ticks(5);
        ctx.run(() -> ctx.check(maid.getEffect(PRESET).getAmplifier()).as("套上更强效果后的等级").isEqualTo(4));
        ctx.await(30 + MAINTAIN_WAIT, "回落到初始等级", () -> permanentPreset(maid));
    }

    @DisplayName("没有来源标记的女仆清掉效果后不会多出效果")
    @Test
    void plainMaidGetsNothing(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> Actors.spawn(ctx, Actors.MAID, 0, 0, 0, true));
        ctx.run(maid::removeAllEffects);
        ctx.ticks(MAINTAIN_WAIT);
        ctx.run(() -> ctx.check(maid.getActiveEffects()).as("女仆身上的效果").isEmpty());
    }

    @DisplayName("保留永久效果的结构女仆收进魂符再放出，初始效果仍是永久的")
    @Test
    void slabRoundTripKeepsPermanent(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer owner = ctx.get(() -> Owners.visitor(ctx, "TlmsPresetOwner", 2, 0, 0));
        EntityMaid maid = ctx.get(() -> withPreset(Owners.maid(ctx, owner, 0, 0, 0), true));
        UUID id = maid.getUUID();
        ItemStack slab = ctx.get(() -> Players.storeInSlab(owner, maid));
        Players.releaseSlab(ctx, owner, slab, ctx.rel(0, -1, 2));
        ctx.ticks(2);
        Entity released = ctx.get(() -> Maids.find(ctx.level(), id));
        if (!(released instanceof EntityMaid back)) {
            throw ctx.fail("魂符没有放出同一只女仆");
        }
        ctx.cleanup(() -> Actors.cleanupEntity(back));
        ctx.run(() -> ctx.check(permanentPreset(back)).as("放出后仍有初始的永久效果").isTrue());
    }

    /** 写上来源标记和初始效果（再生 II，永久），并让女仆当前带着这个效果；服务端线程。 */
    private static EntityMaid withPreset(EntityMaid maid, boolean preservePermanent) {
        MobEffectInstance effect = new MobEffectInstance(PRESET, MobEffectInstance.INFINITE_DURATION, PRESET_AMPLIFIER);
        ListTag initial = new ListTag();
        initial.add(effect.save());
        maid.getPersistentData().put(MaidOriginData.ORIGIN, MaidOriginData.createOrigin(SOURCE, preservePermanent, initial));
        maid.addEffect(new MobEffectInstance(effect));
        return maid;
    }

    private static boolean permanentPreset(EntityMaid maid) {
        MobEffectInstance current = maid.getEffect(PRESET);
        return current != null && current.isInfiniteDuration() && current.getAmplifier() == PRESET_AMPLIFIER;
    }
}
