package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

/**
 * 星海与 Vivaldi 两张音乐唱片：物品属性、jukebox_song 数据、唱片标签、创造栏、三种语言文本、
 * 曲目时长与音频实际时长一致、放进唱片机能播放且比较器读数为 15。
 * <p>只装车万女仆的拓扑里不能引用缺席模组的类。
 */
@Scene("tlms.minimal")
@DisplayName("音乐唱片：星海与 Vivaldi（仅装车万女仆）")
class MusicDiscMinimalTest {
    @DisplayName("唱片物品属性、曲目数据、唱片标签、创造栏与三种语言文本都正确")
    @Test
    void itemsAndSongs(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MusicDiscFixtures.itemsAndSongs(ctx));
    }

    @DisplayName("曲目时长与音频文件实际时长相差不到 0.1 秒")
    @Test
    void songLengthMatchesAudio(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MusicDiscFixtures.audioLength(ctx));
    }

    @DisplayName("唱片放进唱片机能播放，比较器读数为 15")
    @Test
    void jukeboxPlaysWithFullComparator(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> MusicDiscFixtures.jukebox(ctx));
    }
}
