package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.client.event.WinefoxProgressTooltipEvent;
import com.github.yimeng261.maidspell.client.winefox.WinefoxProgressClientCache;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;

/**
 * 归星的工具提示按客户端缓存的誓约进度显示：进度未同步时不加行；已同步时第一行是灰色进度，
 * 两场常规胜利后追加紫色的下一步提示；其他物品不受影响。
 */
@Scene("tlms.integrated")
class WinefoxProgressClientTest {
    private static final String PREFIX = "item.touhou_little_maid_spell.returning_star.";

    @Test
    @Requires(Requires.Capability.CLIENT)
    void tooltipFollowsCache(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, player);
        List<String> result = ClientSide.call(ctx, () -> {
            boolean known = WinefoxProgressClientCache.isKnown();
            int wins = WinefoxProgressClientCache.getWins();
            boolean solo = WinefoxProgressClientCache.hasSoloWin();
            ItemStack star = new ItemStack(MaidSpellItems.RETURNING_STAR.get());
            List<String> lines = new ArrayList<>();
            try {
                WinefoxProgressClientCache.clear();
                lines.add("未同步 " + tooltipKeys(star));
                WinefoxProgressClientCache.update(1, false);
                lines.add("1 场 " + tooltipKeys(star));
                WinefoxProgressClientCache.update(2, false);
                lines.add("2 场 " + tooltipKeys(star));
                WinefoxProgressClientCache.update(2, true);
                lines.add("2 场加专属胜利 " + tooltipKeys(star));
                lines.add("其他物品 " + tooltipKeys(new ItemStack(net.minecraft.world.item.Items.STICK)));
            } finally {
                if (known) {
                    WinefoxProgressClientCache.update(wins, solo);
                } else {
                    WinefoxProgressClientCache.clear();
                }
            }
            return lines;
        });
        ctx.check(result).as("归星工具提示的行").isEqualTo(List.of(
                "未同步 []",
                "1 场 [" + PREFIX + "progress]",
                "2 场 [" + PREFIX + "progress, " + PREFIX + "awaiting_dance]",
                "2 场加专属胜利 [" + PREFIX + "progress, " + PREFIX + "ready]",
                "其他物品 []"));
    }

    private static List<String> tooltipKeys(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        ItemTooltipEvent event = new ItemTooltipEvent(stack, Minecraft.getInstance().player, lines,
                TooltipFlag.NORMAL, Item.TooltipContext.EMPTY);
        WinefoxProgressTooltipEvent.onItemTooltip(event);
        List<String> keys = new ArrayList<>();
        for (Component line : lines) {
            keys.add(line.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "<非翻译文本>");
        }
        return keys;
    }
}
