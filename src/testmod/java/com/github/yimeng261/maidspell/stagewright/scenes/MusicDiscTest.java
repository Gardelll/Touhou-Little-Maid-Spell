package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

/**
 * 星海与 Vivaldi 两张音乐唱片：物品属性、jukebox_song 数据、唱片标签、创造栏、三种语言文本、
 * 曲目时长与音频实际时长一致、放进唱片机能播放且比较器读数为 15。
 */
@Scene("tlms")
class MusicDiscTest {
    @Test
    void itemsAndSongs(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MusicDiscFixtures.itemsAndSongs(ctx));
    }

    @Test
    void songLengthMatchesAudio(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> MusicDiscFixtures.audioLength(ctx));
    }

    @Test
    void jukeboxPlaysWithFullComparator(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> MusicDiscFixtures.jukebox(ctx));
    }
}
