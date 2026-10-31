package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.dimension.StructureSearchWorker;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.entity.WindSeekingBellEntity;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.stagewright.support.ChatTap;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 寻风之铃与归隐之地测试共用的常量与阻塞式流程（测试线程调用；碰玩家、关卡的部分内部切到服务端线程）。
 * 私人维度模式的 {@link RetreatTest}、{@link RetreatCacheTest} 与共享模式的 {@link SharedRetreatTest} 都用它。
 */
final class RetreatFixtures {
    /** 真实客户端玩家整个运行共享：用到它的测试类持有这把锁，避免并发时互相传送、关界面。 */
    static final String HOST_PLAYER = "tlms.hostPlayer";
    static final String BELL_KEY = "item.touhou_little_maid_spell.wind_seeking_bell.";
    static final String ENTERED = BELL_KEY + "entered_retreat";
    static final String RETURNED = BELL_KEY + "return_to_overworld";
    static final String WAITING = BELL_KEY + "waiting_for_structure";
    static final String FOUND = BELL_KEY + "found_structure";
    static final String SEARCH_TIME = BELL_KEY + "search_time";
    static final String HIDDEN_RETREAT = NS + "hidden_retreat";
    static final String HIDDEN_RETREAT_SET = NS + "hidden_retreat_set";
    /** 首次进入要创建维度并生成落点附近的区块。 */
    static final int ENTER_TICKS = 600;
    /** 分帧搜索的等待上限。 */
    static final int SEARCH_TICKS = 2400;
    /** 进入后等提示送达客户端的上限。 */
    static final int NOTICE_TICKS = 100;
    static final int BELLS = 3;
    /** 铃每次使用后有 20 tick 冷却（见 WindSeekingBell），等它结束的上限留一倍余量。 */
    private static final int BELL_COOLDOWN_WAIT = 40;
    /** 宿主玩家用铃前站的主世界坐标（x = z）。 */
    private static final int NEAR_ORIGIN = 8;

    private RetreatFixtures() {
    }

    /** 一次搜索：右键前取的消息标记、等待期间飞出的铃。 */
    record Search(long mark, Set<Integer> flyingBells) {
    }

    /** 坐标消息的参数：结构 x、地表高度、结构 z、距离。 */
    record Found(int x, int height, int z, int distance, ChatTap.Received message) {
    }

    /** 服务端线程上调用。 */
    static ItemStack bell(int count) {
        return new ItemStack(MaidSpellItems.WIND_SEEKING_BELL.get(), count);
    }

    /** 把 count 个铃放到主手（测试线程）。 */
    static void holdBells(Stage ctx, ServerPlayer player, int count) {
        ctx.run(() -> Players.hold(player, bell(count)));
    }

    /** 主手物品数量（测试线程）。 */
    static int mainHandCount(Stage ctx, ServerPlayer player) {
        return ctx.get(() -> player.getMainHandItem().getCount());
    }

    /** 不在归隐之地时用手上的铃进入（不消耗）（测试线程）。 */
    static void enterRetreat(Stage ctx, ServerPlayer player) {
        if (ctx.get(() -> TheRetreatDimension.isInRetreat(player))) {
            return;
        }
        enterFromOverworld(ctx, player);
    }

    /**
     * 移到主世界原点附近，用手上的铃进入归隐之地（不消耗），等传送落定后返回右键前取的消息标记（测试线程）。
     * 归隐之地的落点与主世界坐标相同，隐世之境会生成在落点附近。
     */
    static long enterFromOverworld(Stage ctx, ServerPlayer player) {
        long mark = useBellInOverworld(ctx, player);
        ctx.await(ENTER_TICKS, "进入归隐之地", () -> TheRetreatDimension.isInRetreat(player));
        Players.settled(ctx, player);
        return mark;
    }

    /** 移到主世界原点附近并右键一次铃，不等传送完成，返回右键前取的消息标记（测试线程）。 */
    static long useBellInOverworld(Stage ctx, ServerPlayer player) {
        ctx.run(() -> player.setShiftKeyDown(false));
        toOverworldNearOrigin(ctx, player);
        whenReady(ctx, player);
        long mark = ChatTap.mark();
        ctx.run(() -> Players.use(player));
        return mark;
    }

    /** 传送到主世界原点附近的地面上并等传送落定（测试线程；测试场地在 StageWright 自己的 plot 里）。 */
    static void toOverworldNearOrigin(Stage ctx, ServerPlayer player) {
        ctx.run(() -> {
            ServerLevel overworld = ctx.server().overworld();
            overworld.getChunk(NEAR_ORIGIN >> 4, NEAR_ORIGIN >> 4);
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, NEAR_ORIGIN, NEAR_ORIGIN);
            player.teleportTo(overworld, NEAR_ORIGIN + 0.5, y, NEAR_ORIGIN + 0.5, Set.of(), 0, 0);
        });
        Players.settled(ctx, player);
    }

    /** 原版在物品冷却中直接忽略右键：等铃的冷却结束（测试线程）。 */
    static void whenReady(Stage ctx, ServerPlayer player) {
        ctx.await(BELL_COOLDOWN_WAIT, "寻风之铃冷却结束",
                () -> !player.getCooldowns().isOnCooldown(MaidSpellItems.WIND_SEEKING_BELL.get()));
    }

    static Search search(Stage ctx, ServerPlayer player) {
        return search(ctx, player, () -> {
        });
    }

    /**
     * 在归隐之地右键一次铃（先等过冷却），afterUse 在右键后同一 tick 于服务端线程运行；等到搜索结束（收到搜索耗时消息），
     * 期间记下玩家附近飞出的铃。测试结束时取消本维度仍在进行的搜索（测试线程）。
     */
    static Search search(Stage ctx, ServerPlayer player, Runnable afterUse) {
        ServerLevel level = ctx.get(player::serverLevel);
        ctx.cleanup(() -> cancelSearches(level));
        Set<Integer> flyingBells = ConcurrentHashMap.newKeySet();
        Set<Integer> leftover = new HashSet<>();
        whenReady(ctx, player);
        long mark = ChatTap.mark();
        ctx.run(() -> {
            // 同一维度里先前的搜索留下的铃不算本次飞出的
            level.getEntitiesOfClass(WindSeekingBellEntity.class, player.getBoundingBox().inflate(32))
                    .forEach(bell -> leftover.add(bell.getId()));
            Players.use(player);
            afterUse.run();
        });
        ctx.await(SEARCH_TICKS, "搜索结束", () -> {
            level.getEntitiesOfClass(WindSeekingBellEntity.class, player.getBoundingBox().inflate(32))
                    .stream().filter(bell -> !leftover.contains(bell.getId())).forEach(bell -> flyingBells.add(bell.getId()));
            return ChatTap.find(mark, SEARCH_TIME, false).isPresent();
        });
        return new Search(mark, flyingBells);
    }

    /** 右键一次铃，等到坐标消息后返回坐标（找不到时记失败并返回 null）（测试线程）。 */
    static Found locate(Stage ctx, ServerPlayer player) {
        return found(ctx, search(ctx, player).mark());
    }

    /** mark 之后收到的坐标消息；没有时记一条失败并返回 null。 */
    static Found found(Stage ctx, long mark) {
        ChatTap.Received received = ChatTap.find(mark, FOUND, false).orElse(null);
        ctx.check(received != null).as("收到隐世之境坐标消息（实际：" + ChatTap.since(mark) + "）").isTrue();
        if (received == null) {
            return null;
        }
        return new Found(received.intArg(0), received.intArg(1), received.intArg(2), received.intArg(3), received);
    }

    /** 按名字固定 UUID 的玩家档案。 */
    static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    /** 放在 level 的 pos 处、手上拿着铃的 NeoForge 模拟玩家（服务端线程）。 */
    static FakePlayer walker(ServerLevel level, GameProfile profile, BlockPos pos) {
        FakePlayer walker = FakePlayerFactory.get(level, profile);
        walker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        Players.hold(walker, bell(BELLS));
        return walker;
    }

    /** 取消本维度仍在进行的分帧搜索，免得超时的测试拖慢后面的测试（服务端线程）。 */
    static void cancelSearches(ServerLevel level) {
        Map<String, StructureSearchWorker> searches = Reflect.field(null, RetreatManager.class, "ongoingSearches");
        String dimension = level.dimension().location().toString();
        searches.forEach((key, search) -> {
            if (key.startsWith(dimension)) {
                search.cancel();
            }
        });
    }
}
