package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星之魔女残留的吟唱标记（专用服）：读档丢弃存档里的施法状态；坐秋千等不跑 AI 的状态下残留的 isCasting
 * 下一 tick 清掉；管理员维修同样清掉；status 报出清理次数。
 * 残留标记用 ISS 的同步施法数据直接置上，模拟没有 castingSpell 的读档残影。
 */
@Scene("tlms")
@DisplayName("星之魔女：残留吟唱标记清理")
class WinefoxCastStateTest {
    private static final String WITCH = NS + "stellar_witch";
    private static final String SPELL = "irons_spellbooks:fireball";

    @DisplayName("存档里正在吟唱的星之魔女读档后不再吟唱，施法键清空，status 记一次清理")
    @Test
    void loadDiscardsInFlightCast(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            MagicalWinefoxBossEntity witch = witch(ctx);
            markCasting(witch);
            CompoundTag saved = witch.saveWithoutId(new CompoundTag());
            ctx.check(saved.getBoolean("isCasting")).as("存档里的 isCasting").isTrue();
            ctx.check(saved.getString("castingSpellId")).as("存档里的 castingSpellId").isEqualTo(SPELL);
            Entity created = witch.getType().create(ctx.level());
            if (!(created instanceof MagicalWinefoxBossEntity loaded)) {
                throw ctx.fail("读档用的星之魔女没造出来");
            }
            loaded.load(saved);
            ctx.check(loaded.isCasting()).as("读档后吟唱中").isFalse();
            ctx.check(loaded.getMagicData().getSyncedData().getCastingSpellId()).as("读档后的施法法术").isEqualTo("");
            ctx.check(saved.getBoolean("isCasting")).as("读档时改写的 isCasting").isFalse();
            ctx.check(loaded.maidspell$lifecycleStatus().getString()).as("读档后的 status").contains("staleClears=1");
        });
    }

    @DisplayName("坐在秋千上的星之魔女残留吟唱标记，下一 tick 被清掉，status 记一次清理")
    @Test
    void seatedClearsStaleCast(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        MagicalWinefoxBossEntity witch = ctx.get(() -> {
            MagicalWinefoxBossEntity w = witch(ctx);
            ctx.check(w.isSeated()).as("新生成的星之魔女坐着").isTrue();
            markCasting(w);
            ctx.check(w.isCasting()).as("置上残留标记后吟唱中").isTrue();
            return w;
        });
        ctx.ticks(2);
        ctx.run(() -> {
            ctx.check(witch.isCasting()).as("一 tick 后吟唱中").isFalse();
            String status = witch.maidspell$lifecycleStatus().getString();
            ctx.record("status", status);
            ctx.check(status).as("status").contains("staleClears=1", "castRemaining=", "spell=none");
        });
    }

    @DisplayName("管理员维修清掉残留吟唱标记")
    @Test
    void repairClearsStaleCast(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            MagicalWinefoxBossEntity witch = witch(ctx);
            markCasting(witch);
            witch.maidspell$repairAuthorized();
            ctx.check(witch.isCasting()).as("维修后吟唱中").isFalse();
            ctx.check(witch.maidspell$lifecycleStatus().getString()).as("维修后的 status").contains("staleClears=1");
        });
    }

    /** 在原点生成一只无 AI 的星之魔女；服务端线程。 */
    private static MagicalWinefoxBossEntity witch(Stage ctx) {
        MagicalWinefoxBossEntity witch = Actors.spawn(ctx, WITCH, 0, 0, 0, true);
        witch.setPersistenceRequired();
        return witch;
    }

    /** 置上没有 castingSpell 的吟唱标记，等同读档残影。 */
    private static void markCasting(MagicalWinefoxBossEntity witch) {
        SyncedSpellData synced = witch.getMagicData().getSyncedData();
        synced.setIsCasting(true, SPELL, 3, synced.getCastingEquipmentSlot());
    }
}
