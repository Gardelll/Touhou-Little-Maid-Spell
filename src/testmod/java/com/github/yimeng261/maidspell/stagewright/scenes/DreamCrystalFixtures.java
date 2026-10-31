package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.PortableTimerMath;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 梦云水晶测试共用的内容。缺 Curios 的拓扑也会用到这个类，所以只引用原版、车万女仆与本模组的通用类。
 */
final class DreamCrystalFixtures {
    static final String CRYSTAL = NS + "dream_cat_crystal";
    static final int REVIVE_INVULNERABLE = 300;

    private DreamCrystalFixtures() {
    }

    /** 主世界游戏时间（服务端线程）。 */
    static long now(Stage ctx) {
        return PortableTimerMath.overworldGameTime(ctx.server());
    }

    /** 第一次致死：复活、回满、获得 15 秒无敌（期间不受伤），记下一条复活时间（测试线程）。 */
    static void reviveFirstDeath(Stage ctx) {
        EntityMaid maid = ctx.get(() -> Actors.sittingMaid(ctx, 0, 0, 0));
        ItemStack crystal = ctx.get(() -> Actors.equipBauble(maid, CRYSTAL));
        ctx.ticks(2);
        ctx.run(() -> {
            long at = now(ctx);
            Maids.kill(maid);
            ctx.check(maid.isAlive()).as("第一次致死后女仆存活").isTrue();
            ctx.check(maid.getHealth()).as("复活后生命").isCloseTo(maid.getMaxHealth(), 1e-3);
            ctx.check(crystal.get(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL.get()))
                    .as("无敌截止时间").isEqualTo(at + REVIVE_INVULNERABLE);
            ctx.check(crystal.getOrDefault(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), List.of()))
                    .as("复活记录").isEqualTo(List.of(at));
            maid.invulnerableTime = 0;
            float before = maid.getHealth();
            maid.hurt(maid.damageSources().generic(), 4);
            ctx.check(maid.getHealth()).as("无敌期间受 4 点伤害后的生命").isCloseTo(before, 1e-3);
        });
    }
}
