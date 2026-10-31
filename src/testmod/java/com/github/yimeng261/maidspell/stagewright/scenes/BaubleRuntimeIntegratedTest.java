package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.scenes.BaubleRuntimeTest.SOUL_BOOK;
import static com.github.yimeng261.maidspell.stagewright.scenes.BaubleRuntimeTest.hurt;
import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 魂之书主人保护（集成服，需要真实玩家）：装备、卸下、反复穿脱、女仆死亡、收进魂符、区块卸载时的开关；
 * 戴锚定核心的女仆区块不卸载，保护保留。
 */
@Scene("tlms.integrated")
class BaubleRuntimeIntegratedTest {
    private static final String ANCHOR = NS + "anchor_core";
    /** 远程女仆与玩家的距离：超出集成服视距，强加载解除后区块会卸载。 */
    private static final int REMOTE = 320;

    /** 直接 setHealth 往下调：受保护时被拦下（生命不变），否则照常生效。返回是否被拦下。 */
    private static boolean directWriteBlocked(ServerPlayer player) {
        player.setHealth(20);
        player.setHealth(10);
        boolean blocked = player.getHealth() == 20;
        player.setHealth(20);
        return blocked;
    }

    /** 开家模式并把家设在女仆当前位置：不跟随主人，也不被传回默认的原点。 */
    private static void stayHome(EntityMaid maid) {
        maid.setHomeModeEnable(true);
        maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
    }

    /** 装备魂之书时主人受保护、正常受伤照常扣血；卸下后立即失效，再装备后恢复。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerProtectionOnOff(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ctx.run(() -> {
            EntityMaid maid = Players.ownedMaid(ctx, player, 2, 0, 0, SOUL_BOOK);
            ctx.check(directWriteBlocked(player)).as("装备魂之书时直接改写主人生命被拦下").isTrue();
            player.setHealth(20);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 4);
            ctx.check(player.getHealth()).as("装备魂之书时主人正常受 4 点伤害后的生命").isCloseTo(16, 1e-4);
            player.setHealth(20);
            Maids.takeOffAll(maid);
            ctx.check(directWriteBlocked(player)).as("卸下魂之书后直接改写主人生命被拦下").isFalse();
            Actors.equipBauble(maid, SOUL_BOOK);
            ctx.check(directWriteBlocked(player)).as("重新装备后直接改写主人生命被拦下").isTrue();
        });
    }

    /** 反复穿脱多次后状态仍与当前是否装备一致：不会永久开启或永久失效。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerProtectionAfterCycles(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ctx.run(() -> {
            EntityMaid maid = Players.ownedMaid(ctx, player, 2, 0, 0, SOUL_BOOK);
            EntityMaid second = Players.ownedMaid(ctx, player, -2, 0, 0, SOUL_BOOK);
            for (int i = 0; i < 5; i++) {
                Maids.takeOffAll(maid);
                Actors.equipBauble(maid, SOUL_BOOK);
            }
            Maids.takeOffAll(second);
            ctx.check(directWriteBlocked(player)).as("穿脱 5 次后仍装备着时受保护").isTrue();
            Maids.takeOffAll(maid);
            ctx.check(directWriteBlocked(player)).as("两只女仆都卸下后受保护").isFalse();
            Actors.equipBauble(second, SOUL_BOOK);
            ctx.check(directWriteBlocked(player)).as("另一只女仆重新装备后受保护").isTrue();
        });
    }

    /** 装备魂之书的女仆死亡后保护失效。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerProtectionEndsOnDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid owned = Players.ownedMaid(ctx, player, 2, 0, 0, SOUL_BOOK);
            ctx.check(directWriteBlocked(player)).as("女仆存活时受保护").isTrue();
            hurt(owned, 2);
            return owned;
        });
        ctx.ticks(Config.soulBookDamageIntervalThreshold + 2);
        ctx.run(() -> {
            Maids.kill(maid);
            ctx.check(maid.isAlive()).as("女仆已死亡").isFalse();
            ctx.check(directWriteBlocked(player)).as("女仆死亡后受保护").isFalse();
        });
    }

    /** 女仆被收进魂符后保护失效；放出来后恢复。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerProtectionEndsInSlab(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        UUID[] id = new UUID[1];
        ItemStack slab = ctx.get(() -> {
            EntityMaid maid = Players.ownedMaid(ctx, player, 2, 0, 0, SOUL_BOOK);
            id[0] = maid.getUUID();
            ItemStack stored = Players.storeInSlab(player, maid);
            ctx.check(maid.isRemoved()).as("女仆已收进魂符").isTrue();
            ctx.check(directWriteBlocked(player)).as("女仆在魂符里时受保护").isFalse();
            return stored;
        });
        Players.releaseSlab(ctx, player, slab, ctx.rel(2, -1, 0));
        ctx.ticks(2);
        ctx.run(() -> {
            Entity released = Maids.find(ctx.level(), id[0]);
            ctx.check(released instanceof EntityMaid).as("魂符放出了同一只女仆").isTrue();
            if (released != null) {
                ctx.cleanup(() -> Actors.cleanupEntity(released));
            }
            ctx.check(directWriteBlocked(player)).as("放出后受保护").isTrue();
        });
    }

    /** 女仆所在区块卸载后保护失效。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerProtectionEndsOnUnload(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ServerLevel level = ctx.get(ctx::level);
        BlockPos far = ctx.rel(0, 0, REMOTE);
        EntityMaid maid = ctx.get(() -> {
            level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
            ctx.cleanup(() -> level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false));
            EntityMaid owned = Players.ownedMaid(ctx, player, 0, 0, REMOTE, SOUL_BOOK);
            stayHome(owned);
            ctx.check(directWriteBlocked(player)).as("远处女仆区块加载时受保护").isTrue();
            level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
            return owned;
        });
        ctx.await(360, "女仆随区块卸载", maid::isRemoved);
        ctx.run(() -> {
            ctx.record("removalReason", String.valueOf(maid.getRemovalReason()));
            ctx.check(maid.getRemovalReason()).as("女仆随区块卸载").isEqualTo(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            ctx.check(directWriteBlocked(player)).as("女仆随区块卸载后受保护").isFalse();
        });
    }

    /** 同时戴锚定核心的女仆，撤掉测试的强加载后区块仍由锚定核心保持加载，保护不失效。 */
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerProtectionKeptWhenAnchored(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ServerLevel level = ctx.get(ctx::level);
        BlockPos far = ctx.rel(0, 0, REMOTE);
        EntityMaid maid = ctx.get(() -> {
            level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
            ctx.cleanup(() -> level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false));
            EntityMaid owned = Players.ownedMaid(ctx, player, 0, 0, REMOTE, SOUL_BOOK);
            Maids.putOn(owned, 1, Actors.stack(ANCHOR));
            stayHome(owned);
            ctx.check(directWriteBlocked(player)).as("远处女仆区块加载时受保护").isTrue();
            return owned;
        });
        ctx.ticks(10);
        ctx.run(() -> level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false));
        ctx.ticks(360);
        ctx.run(() -> {
            ctx.check(maid.isRemoved()).as("戴锚定核心的女仆随区块卸载").isFalse();
            ctx.check(directWriteBlocked(player)).as("撤掉测试强加载后受保护").isTrue();
        });
    }
}
