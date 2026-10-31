package com.github.yimeng261.maidspell.stagewright.scenes;

import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.github.yimeng261.maidspell.stagewright.scenes.SpellLifecycleTest.RETENTION;

/** UsefulMagic 的女仆法术数据生命周期（集成拓扑，专用服不装 UsefulMagic），场景与 {@link SpellLifecycleTest} 的各模组一致。 */
@Scene("tlms.integrated")
@ResourceLock("stagewright.player:TlmsSpellOwner")
class SpellLifecycleIntegratedTest {
    private static final String MOD = "usefulmagic";

    @Test
    void usefulmagicSlabRoundTrip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.slabRoundTrip(ctx, MOD);
    }

    @Test
    void usefulmagicUnloadBriefly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.unload(ctx, MOD, RETENTION / 2);
    }

    @Test
    void usefulmagicUnloadLong(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.unload(ctx, MOD, RETENTION + 20);
    }

    @Test
    void usefulmagicDimensionChange(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.dimensionChange(ctx, MOD);
    }

    @Test
    void usefulmagicDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.death(ctx, MOD, false);
    }

    @Test
    void usefulmagicRevived(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        SpellLifecycleTest.death(ctx, MOD, true);
    }
}
