package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.FormattedText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 书的每页在成书界面里放得下：按客户端字体折行后不超过一页的行数，中英日文都一样。
 */
@DisplayName("书页排版：成书界面分页")
@Scene("tlms.integrated")
class BookClientTest {
    /** 成书界面的文字区宽 114 像素、高 128 像素，一行 9 像素。 */
    private static final int PAGE_WIDTH = 114;
    private static final int PAGE_LINES = 128 / 9;

    @DisplayName("书的每页按客户端字体折行后不超过一页行数，中英日文都放得下")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void everyPageFits(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, player);
        Map<String, Integer> books = Lang.books(Lang.read("en_us"));
        List<String> overflow = ClientSide.call(ctx, () -> {
            Font font = Minecraft.getInstance().font;
            List<String> result = new ArrayList<>();
            // 日文缺的键与游戏里一样回落英文
            for (List<String> codes : List.of(List.of("en_us"), List.of("zh_cn"), List.of("en_us", "ja_jp"))) {
                String code = codes.get(codes.size() - 1);
                ClientLanguage language = ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), codes, false);
                books.forEach((book, pages) -> {
                    for (int page = 1; page <= pages; page++) {
                        String key = book + ".page_" + page;
                        int lines = font.split(FormattedText.of(language.getOrDefault(key)), PAGE_WIDTH).size();
                        if (lines > PAGE_LINES) {
                            result.add(code + " " + key + "：" + lines + " 行");
                        }
                    }
                });
            }
            return result;
        });
        ctx.record("books", books);
        ctx.check(overflow).as("折行后超过 " + PAGE_LINES + " 行的页").isEmpty();
    }
}
