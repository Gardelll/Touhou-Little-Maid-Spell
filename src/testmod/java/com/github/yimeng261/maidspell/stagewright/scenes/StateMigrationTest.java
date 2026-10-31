package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.spell.manager.AllianceManager;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.PortableTimerMath;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 旧状态迁移与配置：旧版结盟队伍的清理、配置项、春归计时改用主世界时间及旧物品计时迁移。
 */
@DisplayName("旧状态迁移与配置")
@Scene("tlms")
class StateMigrationTest {
    private static final String SPRING_BLOOM = NS + "spring_bloom_return";
    private static final int MIGRATION_GRACE = 20;
    private static final String FIREBOLT_BOOK = "irons_spellbooks:gold_spell_book[irons_spellbooks:spell_container="
            + "{maxSpells:1,mustEquip:false,spellWheel:true,data:[{id:\"irons_spellbooks:firebolt\",index:0,level:1,locked:false}]}]";

    private static long now(Stage ctx) {
        return PortableTimerMath.overworldGameTime(ctx.server());
    }

    // ---- 结盟与配置 ----

    /** 以 maidspell_alliance_ 开头的旧队伍都被删掉，玩家自己建的队伍不动。 */
    @DisplayName("清掉 maidspell_alliance_ 开头的旧结盟队伍，玩家自建的队伍不动")
    @Test
    void allianceTeamsRemoved(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            Scoreboard scoreboard = ctx.server().getScoreboard();
            List<String> legacy = List.of("maidspell_alliance_0badf00d", "maidspell_alliance_deadbeef");
            for (String name : legacy) {
                if (scoreboard.getPlayerTeam(name) == null) {
                    scoreboard.addPlayerTeam(name);
                }
            }
            PlayerTeam own = scoreboard.getPlayerTeam("tlms_player_team");
            PlayerTeam kept = own != null ? own : scoreboard.addPlayerTeam("tlms_player_team");
            ctx.cleanup(() -> scoreboard.removePlayerTeam(kept));
            AllianceManager.cleanupLegacyTeams(ctx.server());
            ctx.check(legacy.stream().filter(name -> scoreboard.getPlayerTeam(name) != null).toList()).as("清理后仍在的旧队伍").isEmpty();
            ctx.check(scoreboard.getPlayerTeam("tlms_player_team")).as("玩家自建的队伍").isNotNull();
        });
    }

    /** 新生成的归隐之地配置文件里没有 autoAllianceEnabled。 */
    @DisplayName("新生成的归隐之地配置文件里没有 autoAllianceEnabled 配置项")
    @Test
    void autoAllianceRemoved(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        Path file = ctx.server().getServerDirectory().resolve("config")
                .resolve(MaidSpellMod.MOD_ID).resolve("retreat.toml");
        try {
            String text = Files.readString(file);
            ctx.check(text.contains("autoAllianceEnabled")).as(file + " 里有 autoAllianceEnabled").isFalse();
            ctx.check(text.contains("retreatRecordRetentionDays")).as(file + " 里有 retreatRecordRetentionDays").isTrue();
        } catch (IOException e) {
            throw ctx.fail("读不到归隐之地配置文件 " + file + ": " + e.getMessage());
        }
    }

    /** retreatRecordRetentionDays 默认 0（不清理）。 */
    @DisplayName("归隐记录保留天数默认为 0，即不清理")
    @Test
    void retreatRecordRetentionDaysDefault(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.check(Config.retreatRecordRetentionDays).as("retreatRecordRetentionDays 当前值").isEqualTo(0);
    }

    // ---- 春归 ----

    /** 层数按主世界时间到期消失。 */
    @DisplayName("春花-返的层数按主世界时间到期消失")
    @Test
    void stacksExpire(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid spawned = Actors.sittingMaid(ctx, 0, 0, 0);
            ItemStack stack = Actors.equipBauble(spawned, SPRING_BLOOM);
            stack.set(MaidSpellDataComponents.SPRING_BLOOM_RETURN_CLOCK_VERSION.get(), 1);
            stack.set(MaidSpellDataComponents.SPRING_BLOOM_RETURN_EXPIRIES.get(), List.of(now(ctx) + 5));
            return spawned;
        });
        ctx.ticks(15);
        ctx.run(() -> {
            Actors.castSpringBloom(maid);
            List<Long> expiries = Actors.springBloomExpiries(maid);
            ctx.check(expiries.stream().filter(expiry -> expiry <= now(ctx)).toList()).as("到期后仍在的层").isEmpty();
        });
    }

    /** 下界里施法：层数的到期时间是主世界时间 + 持续时间，不会立即过期也不会永不过期。 */
    @DisplayName("下界施法时春花-返层数的到期时间按主世界时间计算")
    @Test
    void timersUseOverworldClockInNether(Game game, @Plot.In("minecraft:the_nether") Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid spawned = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(spawned, SPRING_BLOOM);
            return spawned;
        });
        ctx.ticks(2);
        ctx.run(() -> {
            long at = now(ctx);
            Actors.castSpringBloom(maid);
            ctx.check(Actors.springBloomExpiries(maid)).as("下界施法后的层数到期时间")
                    .isEqualTo(List.of(at + Config.springBloomReturnStackDurationTicks));
        });
    }

    /**
     * 旧物品（没有时钟版本号、按所在维度时间记录）：层数和冷却大致保留（剩余时间最多多出迁移余量），
     * 不会变成永久冷却，也不会一次性清空。
     */
    @DisplayName("旧春花-返物品的层数和冷却迁移后大致保留，不变成永久冷却也不被清空")
    @Test
    void legacyTimersMigrate(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        record Rig(EntityMaid maid, ItemStack worn) { }
        Rig rig = ctx.get(() -> {
            EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
            long local = maid.level().getGameTime();
            ItemStack stack = Actors.stack(SPRING_BLOOM);
            stack.set(MaidSpellDataComponents.SPRING_BLOOM_RETURN_EXPIRIES.get(), List.of(local + 200, local + 300));
            stack.set(MaidSpellDataComponents.SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_UNTIL.get(), local + 10_000_000L);
            maid.getMaidBauble().setStackInSlot(0, stack);
            return new Rig(maid, maid.getMaidBauble().getStackInSlot(0));
        });
        ctx.ticks(2);
        ctx.run(() -> {
            ItemStack worn = rig.worn();
            long now = now(ctx);
            Actors.castSpringBloom(rig.maid());
            List<Long> expiries = Actors.springBloomExpiries(rig.maid());
            ctx.record("expiries", expiries);
            ctx.check(worn.get(MaidSpellDataComponents.SPRING_BLOOM_RETURN_CLOCK_VERSION.get())).as("迁移后的时钟版本").isEqualTo(1);
            ctx.check(expiries.size()).as("迁移后保留的层数（施法冷却内不新增）").isBetween(2, 3);
            ctx.check(expiries.stream().allMatch(expiry -> expiry > now
                    && expiry <= now + Config.springBloomReturnStackDurationTicks + MIGRATION_GRACE)).as("迁移后各层剩余时间合理").isTrue();
            Long trigger = worn.get(MaidSpellDataComponents.SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_UNTIL.get());
            ctx.check(trigger == null || trigger - now <= Config.springBloomReturnTriggerCooldownTicks + MIGRATION_GRACE)
                    .as("迁移后的触发冷却不超过一次冷却时长（" + trigger + "）").isTrue();
        });
    }

    /** 春归交给另一只在下界的女仆后重新装备：层数与到期时间不变。 */
    @DisplayName("春花-返交给下界的另一只女仆重新装备后，层数与到期时间不变")
    @Test
    void timersFollowItemAcrossMaids(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid first = ctx.get(() -> {
            EntityMaid spawned = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(spawned, SPRING_BLOOM);
            return spawned;
        });
        ctx.ticks(2);
        record Moved(List<Long> before, EntityMaid inNether) { }
        Moved moved = ctx.get(() -> {
            Actors.castSpringBloom(first);
            List<Long> before = Actors.springBloomExpiries(first);
            ItemStack item = Maids.takeOff(first, 0);
            ServerLevel nether = ctx.server().getLevel(Level.NETHER);
            EntityMaid second = Actors.sittingMaid(ctx, 2, 0, 0);
            EntityMaid inNether = (EntityMaid) Maids.copyToDimension(ctx, second, nether, new Vec3(0.5, 64, 0.5));
            ctx.check(inNether).as("传送到下界的女仆").isNotNull();
            if (inNether != null) {
                Maids.putOn(inNether, 0, item);
            }
            return new Moved(before, inNether);
        });
        if (moved.inNether() == null) {
            return;
        }
        ctx.ticks(5);
        ctx.run(() -> ctx.check(Actors.springBloomExpiries(moved.inNether())).as("换女仆、换维度后的层").isEqualTo(moved.before()));
    }

    /** 好感度 2 级：触发时按比例退还法术冷却。 */
    @DisplayName("好感度 2 级的春花-返触发时按比例退还法术冷却")
    @Test
    void refundsCooldownAtFavorabilityLevel2(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        record Rig(EntityMaid maid, ISpellBookProvider<?, ?> iss, ItemStack book) { }
        Rig rig = ctx.get(() -> {
            EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
            Actors.equipBauble(maid, SPRING_BLOOM);
            maid.setFavorability(maid.getFavorabilityManager().getPointByLevel(2));
            ItemStack book = Actors.parse(ctx, FIREBOLT_BOOK);
            maid.setItemSlot(EquipmentSlot.OFFHAND, book);
            return new Rig(maid, SpellBookManager.getProvider("irons_spellbooks"), book);
        });
        EntityMaid maid = rig.maid();
        ctx.ticks(2);
        ctx.run(() -> {
            SpellBookManager.getOrCreateManager(maid).addSpellItem(maid, rig.book());
            IMaidSpellData data = SpellLifecycleTest.data(rig.iss(), maid.getUUID());
            ctx.check(data).as("铁魔法法术数据").isNotNull();
            if (data == null) {
                return;
            }
            data.setSpellCooldown("irons_spellbooks:firebolt", 1000, maid);
            int before = data.getSpellCooldown("irons_spellbooks:firebolt");
            Actors.castSpringBloom(maid);
            Actors.setMaidHealth(maid, maid.getMaxHealth() / 2);
            maid.invulnerableTime = 0;
            maid.hurt(maid.damageSources().generic(), 8);
            int after = data.getSpellCooldown("irons_spellbooks:firebolt");
            ctx.record("cooldown", before + " -> " + after);
            ctx.check(after).as("触发后火球术冷却（原 " + before + "）").isLessThan(before);
        });
    }
}
