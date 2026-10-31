package com.github.yimeng261.maidspell.stagewright.support;

import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.TestReporter;
import org.opentest4j.TestAbortedException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 一个测试的场地与工具：租到的 {@link Plot}、服务端执行、等待、清理、软断言和记录值。
 *
 * <p>测试方法跑在测试线程上；碰 Minecraft 对象的代码放进 {@link #run}/{@link #get}，在服务端线程执行。
 * {@link #level()}、{@link #server()} 只是取引用，返回的对象也只能在服务端线程里用。
 * {@link #rel} 只做坐标换算，哪个线程都能调。
 */
public final class Stage {
    /** {@link #flat} 场地的半边长：地板覆盖相对坐标 x、z 的 [-16, 15]。 */
    public static final int FLAT_HALF = 16;
    /** {@link #fastForward} 的目标 tick 速率：远高于服务端跟得上的速度，实际就是不停顿地连续 tick。 */
    private static final float FAST_TICKS_PER_SECOND = 1000F;

    private final Game game;
    private final Plot plot;
    private final TestReporter reporter;
    private final MinecraftServer server;
    private final BlockPos origin;
    private final Deque<Runnable> cleanups = new ArrayDeque<>();

    private Stage(Game game, Plot plot, TestReporter reporter, BlockPos origin) {
        this.game = game;
        this.plot = plot;
        this.reporter = reporter;
        this.server = game.onServer(s -> s);
        this.origin = origin;
        game.afterTest(s -> runCleanups());
    }

    /** 悬空的空场地：原点就是 plot 的 (0, 0, 0)，周围全是空气。 */
    public static Stage sky(Game game, Plot plot, TestReporter reporter) {
        return new Stage(game, plot, reporter, plot.at(0, 0, 0));
    }

    /**
     * 草方块地板的平地：plot 须是 {@code @Plot.Size(32) @Plot.Centered}，地板在 y=0，
     * 原点 {@code rel(0, 0, 0)} 是地板上方一格（站立位置）。
     */
    public static Stage flat(Game game, Plot plot, TestReporter reporter) {
        plot.fill(-FLAT_HALF, 0, -FLAT_HALF, FLAT_HALF - 1, 0, FLAT_HALF - 1, Blocks.GRASS_BLOCK);
        return new Stage(game, plot, reporter, plot.at(0, 1, 0));
    }

    /**
     * 与 {@link #flat} 相同的站立原点，地板铺满 plot 的整个水平范围：放置远大于 32 格的结构用。
     * plot 须是 {@code @Plot.Centered} 且各边不小于 32。
     */
    public static Stage wideFlat(Game game, Plot plot, TestReporter reporter) {
        Stage stage = flat(game, plot, reporter);
        net.minecraft.core.Vec3i min = plot.min();
        net.minecraft.core.Vec3i max = plot.max();
        plot.fill(min.getX(), 0, min.getZ(), max.getX(), 0, max.getZ(), Blocks.GRASS_BLOCK);
        return stage;
    }

    public Game game() {
        return game;
    }

    public Plot plot() {
        return plot;
    }

    public MinecraftServer server() {
        return server;
    }

    /** plot 所在维度。 */
    public ServerLevel level() {
        ServerLevel level = server.getLevel(plot.dimension());
        if (level == null) {
            throw new IllegalStateException("没有维度 " + plot.dimension().location());
        }
        return level;
    }

    public BlockPos origin() {
        return origin;
    }

    public int originX() {
        return origin.getX();
    }

    public int originY() {
        return origin.getY();
    }

    public int originZ() {
        return origin.getZ();
    }

    /** 相对原点的世界坐标。 */
    public BlockPos rel(int dx, int dy, int dz) {
        return origin.offset(dx, dy, dz);
    }

    /** 服务端线程上执行；已在服务端线程（等待条件里）时直接执行。 */
    public void run(Runnable work) {
        game.runOnServer(s -> work.run());
    }

    /** 服务端线程上计算并返回结果。 */
    public <T> T get(Supplier<T> work) {
        return game.onServer(s -> work.get());
    }

    /**
     * 本测试余下部分让服务端尽快连续 tick（结束时恢复）：只等游戏逻辑的测试用，等区块生成、存盘等后台工作的测试别用，
     * 按 tick 计的等待上限在墙钟上会变短。测试类须持有 {@code @ResourceLock(Locks.TICK_RATE)}（测试线程）。
     */
    public Stage fastForward() {
        game.overrideTickRate(FAST_TICKS_PER_SECOND);
        return this;
    }

    /** 让 n 个服务端 tick 过去（测试线程）。 */
    public void ticks(int n) {
        game.ticks(n);
    }

    /** 每 tick 后在服务端线程检查一次 condition，直到成立；within 个 tick 内不成立则测试失败（测试线程）。 */
    public void await(int within, String what, BooleanSupplier condition) {
        game.await().within(within).as(what).until(condition);
    }

    /** 接下来 ticks 个 tick 里每 tick 在服务端线程运行一次 sample（测试线程）。 */
    public void watch(int ticks, Runnable sample) {
        int end = get(server::getTickCount) + ticks;
        await(ticks + 10, "采样 " + ticks + " tick", () -> {
            sample.run();
            return server.getTickCount() >= end;
        });
    }

    /** 相对原点 (dx, dz) 这一列的地表高度：最高非空气方块上方第一格（服务端线程）。 */
    public int surfaceY(int dx, int dz) {
        return level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                origin.offset(dx, 0, dz)).getY();
    }

    /** 当前服务端 tick 计数。 */
    public int tick() {
        return server.getTickCount();
    }

    /** 测试结束时（含失败）在服务端线程运行，后登记的先运行；哪个线程都能调。 */
    public void cleanup(Runnable undo) {
        synchronized (cleanups) {
            cleanups.addFirst(undo);
        }
    }

    private void runCleanups() {
        RuntimeException first = null;
        while (true) {
            Runnable next;
            synchronized (cleanups) {
                next = cleanups.pollFirst();
            }
            if (next == null) {
                break;
            }
            try {
                next.run();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /** 记一条值进测试报告；JUnit 不收空白值，空白记作 {@code (空)}。 */
    public void record(String key, Object value) {
        String text = String.valueOf(value);
        reporter.publishEntry(key, text.isBlank() ? "(空)" : text);
    }

    /** 立即判测试失败。 */
    public AssertionError fail(String reason) {
        throw new AssertionError(reason);
    }

    /** 前提不满足：测试记为中止（报告里显示为跳过）。 */
    public TestAbortedException skip(String why) {
        throw new TestAbortedException(why);
    }

    /** 软断言：失败记下并继续，测试结束时一并报告。哪个线程都能调。 */
    public <T> Soft<T> check(T actual) {
        return new Soft<>(game, actual);
    }

    /** 在相对原点处放方块（服务端线程）；plot 归还时恢复原样。 */
    public void setBlock(int dx, int dy, int dz, Block block) {
        level().setBlockAndUpdate(rel(dx, dy, dz), block.defaultBlockState());
    }

    /** 某个玩家的服务端实体（测试线程）。 */
    public static ServerPlayer serverPlayer(net.magicterra.stagewright.test.Body body) {
        return body.onServer(p -> p);
    }
}
