package com.github.yimeng261.maidspell.stagewright.scenes;

import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.github.yimeng261.maidspell.stagewright.scenes.SpellLifecycleTest.RETENTION;

/** UsefulMagic 的女仆法术数据生命周期（集成拓扑，专用服不装 UsefulMagic），场景与 {@link SpellLifecycleTest} 的各模组一致。 */
@DisplayName("女仆法术数据的生命周期：UsefulMagic（集成拓扑）")
@Scene("tlms.integrated")
@ResourceLock("stagewright.player:TlmsSpellOwner")
class SpellLifecycleIntegratedTest {
    private static final String MOD = "usefulmagic";

    @DisplayName("UsefulMagic：收进魂符再放出，法术书与冷却保留且能再次施法")
    @Test
    void usefulmagicSlabRoundTrip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.slabRoundTrip(ctx, MOD);
    }

    @DisplayName("UsefulMagic：区块卸载 5 秒内回来，法术数据仍在")
    @Test
    void usefulmagicUnloadBriefly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.unload(ctx, MOD, RETENTION / 2);
    }

    @DisplayName("UsefulMagic：区块卸载超过 5 秒才回来，旧冷却被删除并按背包重新登记法术书")
    @Test
    void usefulmagicUnloadLong(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.unload(ctx, MOD, RETENTION + 20);
    }

    @DisplayName("UsefulMagic：跟随主人换维度后，法术书与冷却保留且能施法")
    @Test
    void usefulmagicDimensionChange(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.dimensionChange(ctx, MOD);
    }

    @DisplayName("UsefulMagic：女仆真正死亡后法术数据立即清掉")
    @Test
    void usefulmagicDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.death(ctx, MOD, false);
    }

    @DisplayName("UsefulMagic：被梦云水晶救下后法术书保留、冷却清零且能施法")
    @Test
    void usefulmagicRevived(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.death(ctx, MOD, true);
    }
}
