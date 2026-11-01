package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 坐在秋千上的星之魔女（专用服）：手持旅行日记右键，凑够四本换一颗星陨石；
 * 手持星芒短剑右键开战，生存模式血量不高于存活线时不接受，创造模式直接开始。
 */
@Scene("tlms")
@DisplayName("星之魔女：坐姿交互")
class WinefoxSeatedTest {
    private static final String WITCH = NS + "stellar_witch";
    private static final String METEORITE = NS + "star_meteorite";

    @DisplayName("手持旅行日记右键：不足四本不兑换，凑够四本收走四本、换一颗星陨石")
    @Test
    void diariesExchangeForMeteorite(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            MagicalWinefoxBossEntity witch = witch(ctx);
            ServerPlayer player = Owners.visitor(ctx, "TlmsDiaryReader", 2, 0, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, diaries(3));
            player.interactOn(witch, InteractionHand.MAIN_HAND);
            ctx.check(player.getMainHandItem().getCount()).as("三本时手上的日记").isEqualTo(3);
            ctx.check(meteorites(player)).as("三本时换到的星陨石").isEqualTo(0);
            player.getInventory().add(diaries(2));
            player.interactOn(witch, InteractionHand.MAIN_HAND);
            ctx.check(diaryCount(player)).as("五本兑换后剩下的日记").isEqualTo(1);
            ctx.check(meteorites(player)).as("五本时换到的星陨石").isEqualTo(1);
        });
    }

    @DisplayName("手持星芒短剑右键：生存模式血量在存活线以下不开战，创造模式直接开战")
    @Test
    void daggerNeedsHealthUnlessCreative(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            MagicalWinefoxBossEntity witch = witch(ctx);
            ServerPlayer player = Owners.visitor(ctx, "TlmsWeakChallenger", 2, 0, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, Actors.stack(NS + "starglint_dagger"));
            float floor = (float) Config.winefoxDuelSurvivalFloor;
            player.setHealth(Math.max(1.0F, floor));
            player.interactOn(witch, InteractionHand.MAIN_HAND);
            ctx.check(witch.isChallengeOngoingFor(player)).as("生存模式血量 " + player.getHealth() + " 时开战").isFalse();
            player.setGameMode(GameType.CREATIVE);
            player.interactOn(witch, InteractionHand.MAIN_HAND);
            ctx.check(witch.isChallengeOngoingFor(player)).as("创造模式同样血量时开战").isTrue();
        });
    }

    private static MagicalWinefoxBossEntity witch(Stage ctx) {
        MagicalWinefoxBossEntity witch = Actors.spawn(ctx, WITCH, 0, 0, 0, true);
        witch.setPersistenceRequired();
        if (!witch.isSeated()) {
            throw ctx.fail("新生成的星之魔女没有坐着");
        }
        return witch;
    }

    /** 带旅行日记标记的成书。 */
    private static ItemStack diaries(int count) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK, count);
        CompoundTag data = new CompoundTag();
        data.putBoolean("touhou_little_maid_spell:travel_diary", true);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return book;
    }

    private static int diaryCount(ServerPlayer player) {
        return player.getInventory().countItem(Items.WRITTEN_BOOK);
    }

    private static int meteorites(ServerPlayer player) {
        return player.getInventory().countItem(Actors.stack(METEORITE).getItem());
    }
}
