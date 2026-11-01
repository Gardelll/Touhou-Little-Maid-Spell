package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketTeleport;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.List;

import static com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketTest.ANCHOR;
import static com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketTest.listed;
import static com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketTest.netherSpot;
import static com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketTest.pocketMaid;

/** 末影腰包传送到锚定女仆身边（集成服，需要真实玩家）：授权、落点安全、跨维度、先下坐骑、冷却。 */
@DisplayName("末影腰包：传送到锚定女仆身边")
@Scene("tlms.integrated")
class EnderPocketIntegratedTest {
    /** 只有同时戴锚定核心的女仆才能传送过去。 */
    @DisplayName("只有同时戴锚定核心的女仆才能传送过去，被拒绝时玩家不位移")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void teleportNeedsAnchorCore(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host,
                                 TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ctx.run(() -> {
            EntityMaid plain = pocketMaid(ctx, player, 0, 0, 40);
            Vec3 start = player.position();
            ctx.check(EnderPocketService.teleportToMaid(player, plain.getUUID())).as("传送到没戴锚定核心的女仆").isFalse();
            ctx.check(player.position().distanceTo(start)).as("被拒绝后玩家位移").isLessThan(0.01);
        });
    }

    /** 传送到女仆旁边：不卡进方块，站在实心地面上，摔落距离清零。 */
    @DisplayName("传送到女仆旁边的安全落点：不卡进方块，站在实心地面上，摔落距离清零")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void teleportToSafeSpot(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host,
                            TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        BlockPos at = ctx.get(() -> {
            // 女仆放在地板范围内，周围一圈才有可站的地面
            EntityMaid maid = pocketMaid(ctx, player, 0, 0, 12, ANCHOR);
            BlockPos pos = maid.blockPosition();
            // 女仆东南西北都是墙，只有西北角空着
            for (BlockPos wall : List.of(pos.north(), pos.south(), pos.east(), pos.west(), pos.north().east(),
                    pos.south().east(), pos.south().west())) {
                ctx.level().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                ctx.level().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
                ctx.cleanup(() -> {
                    ctx.level().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
                    ctx.level().setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
                });
            }
            player.fallDistance = 10;
            ctx.check(EnderPocketService.teleportToMaid(player, maid.getUUID())).as("传送").isTrue();
            return pos;
        });
        ctx.ticks(5);
        ctx.run(() -> {
            ctx.check(player.blockPosition()).as("落点").isEqualTo(at.north().west());
            ctx.check(player.isInWall()).as("落点卡在方块里").isFalse();
            ctx.check(player.fallDistance).as("落地后的摔落距离").isEqualTo(0F);
        });
    }

    /** 跨维度传送到下界的锚定女仆身边。 */
    @DisplayName("可跨维度传送到下界的锚定女仆身边")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void teleportAcrossDimensions(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host,
                                  TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ServerLevel nether = ctx.get(() -> ctx.server().getLevel(Level.NETHER));
        EntityMaid maid = ctx.get(() -> {
            BlockPos pos = netherSpot(ctx);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    nether.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y < 3; y++) {
                        nether.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            EntityMaid anchored = pocketMaid(ctx, nether, pos, player, ANCHOR);
            ctx.cleanup(() -> player.teleportTo(ctx.level(), ctx.originX() + 0.5, ctx.originY(), ctx.originZ() + 0.5, 0, 0));
            return anchored;
        });
        ctx.await(200, "下界锚定女仆加载并出现在列表里", () -> nether.getEntity(maid.getUUID()) == maid
                && nether.getEntity(maid.getId()) == maid && listed(player).contains(maid.getUUID()));
        boolean teleported = ctx.get(() -> EnderPocketService.teleportToMaid(player, maid.getUUID()));
        ctx.check(teleported).as("跨维度传送").isTrue();
        ctx.await(60, "跨维度传送落定", () -> !player.isChangingDimension());
        ctx.run(() -> {
            ctx.check(player.level().dimension()).as("传送后所在维度").isEqualTo(Level.NETHER);
            ctx.check(player.distanceTo(maid)).as("传送后与女仆的距离").isLessThan(2F);
            ctx.check(player.isInWall()).as("落点卡在方块里").isFalse();
        });
    }

    /** 骑乘状态下传送：先下坐骑再传送。 */
    @DisplayName("骑乘状态下传送会先下坐骑再传送，坐骑留在原地")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void teleportDismountsFirst(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host,
                                TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        EntityMaid maid = ctx.get(() -> pocketMaid(ctx, player, 0, 0, 40, ANCHOR));
        Entity boat = ctx.get(() -> {
            Entity spawned = Actors.spawn(ctx, "minecraft:boat", 1, 0, 0, false);
            player.startRiding(spawned, true);
            ctx.check(player.isPassenger()).as("玩家在船上").isTrue();
            ctx.check(EnderPocketService.teleportToMaid(player, maid.getUUID())).as("传送").isTrue();
            return spawned;
        });
        ctx.ticks(5);
        ctx.run(() -> {
            ctx.check(player.isPassenger()).as("传送后仍在骑乘").isFalse();
            ctx.check(player.distanceTo(maid)).as("传送后与女仆的距离").isLessThan(2F);
            ctx.check(boat.distanceTo(maid)).as("船跟着传送过去").isGreaterThan(10F);
        });
    }

    /** 传送请求有 1 秒冷却：连点两次只传送一次。 */
    @DisplayName("传送请求有 1 秒冷却，连点两次只传送一次")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void teleportCooldown(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host,
                          TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        EntityMaid first = ctx.get(() -> pocketMaid(ctx, player, 0, 0, 40, ANCHOR));
        EntityMaid second = ctx.get(() -> pocketMaid(ctx, player, 40, 0, 0, ANCHOR));
        ctx.run(() -> {
            new C2SEnderPocketTeleport(first.getUUID()).handle(player);
            new C2SEnderPocketTeleport(second.getUUID()).handle(player);
        });
        ctx.ticks(5);
        ctx.run(() -> {
            ctx.check(player.distanceTo(first)).as("第一次请求后与第一只女仆的距离").isLessThan(2F);
            ctx.check(player.distanceTo(second)).as("冷却内的第二次请求后与第二只女仆的距离").isGreaterThan(10F);
        });
    }
}
