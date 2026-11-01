package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 本模组真伤（专用服）：排队到下一 tick 按实际扣血结算，致死时由攻击者击杀；
 * 吸收量与当前血量相同不会被当成血量改写；EntityData 写不进时回退到 NBT 改写根级 Health。
 */
@Scene("tlms")
@DisplayName("真伤：结算、击杀归属与 NBT 回退")
class TrueDamageTest {
    @DisplayName("女仆对僵尸的真伤下一 tick 按量扣血，致死时僵尸死亡、击杀者是这只女仆")
    @Test
    void killCreditGoesToAttacker(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        AtomicReference<Entity> killer = new AtomicReference<>();
        Zombie zombie = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 2, 0, 0, true));
        EntityMaid maid = ctx.get(() -> Actors.spawn(ctx, Actors.MAID, -2, 0, 0, true));
        Consumer<LivingDeathEvent> listener = event -> {
            if (event.getEntity() == zombie) {
                killer.set(event.getSource().getEntity());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDeathEvent.class, listener);
        ctx.cleanup(() -> NeoForge.EVENT_BUS.unregister(listener));
        float full = ctx.get(zombie::getHealth);
        ctx.run(() -> {
            ctx.check(TrueDamageUtil.dealTrueDamage(zombie, 5.0F, maid)).as("真伤入队").isTrue();
            ctx.check(zombie.getHealth()).as("入队当 tick 的血量").isCloseTo(full, 1e-3);
        });
        ctx.ticks(2);
        ctx.run(() -> {
            ctx.check(zombie.getHealth()).as("结算后的血量").isCloseTo(full - 5.0F, 1e-3);
            TrueDamageUtil.dealTrueDamage(zombie, 100.0F, maid);
        });
        ctx.await(5, "僵尸死亡", () -> zombie.isDeadOrDying() || zombie.isRemoved());
        ctx.run(() -> ctx.check(killer.get()).as("击杀者").isSameAs(maid));
    }

    @DisplayName("吸收量恰好等于当前血量时，真伤只改血量、不改吸收量")
    @Test
    void absorptionEqualToHealthUntouched(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Zombie zombie = ctx.get(() -> {
            Zombie z = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 0, true);
            // 吸收量受 max_absorption 截断，僵尸默认为 0
            z.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20.0D);
            z.setHealth(10.0F);
            z.setAbsorptionAmount(10.0F);
            return z;
        });
        ctx.ticks(1);
        ctx.run(() -> ctx.check(zombie.getAbsorptionAmount()).as("真伤前的吸收量").isCloseTo(10.0F, 1e-3));
        ctx.run(() -> TrueDamageUtil.dealTrueDamage(zombie, 4.0F, null));
        ctx.ticks(2);
        ctx.run(() -> {
            ctx.check(zombie.getHealth()).as("血量").isCloseTo(6.0F, 1e-3);
            ctx.check(zombie.getAbsorptionAmount()).as("吸收量").isCloseTo(10.0F, 1e-3);
        });
    }

    @DisplayName("NBT 回退路径按根级 Health 改写血量")
    @Test
    void nbtFallbackRewritesRootHealth(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            Zombie zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 0, true);
            zombie.setHealth(12.0F);
            float gap = Reflect.call(null, TrueDamageUtil.class, "tryNBTDamage",
                    new Class<?>[]{LivingEntity.class, float.class}, zombie, 7.0F);
            ctx.check(gap).as("回退后与目标血量的差").isCloseTo(0.0F, 0.01);
            ctx.check(zombie.getHealth()).as("回退后的血量").isCloseTo(7.0F, 1e-3);
        });
    }
}
