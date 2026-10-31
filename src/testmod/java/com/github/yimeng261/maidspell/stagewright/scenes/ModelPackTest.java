package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.TouhouLittleMaidLegacyModelPackCleaner;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.TouhouLittleMaidModelPackInstaller;
import com.github.yimeng261.maidspell.stagewright.support.Logs;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.neoforged.fml.loading.FMLPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 车万女仆模型包：不再内置旧的 geckolib 兼容包，启动时删掉旧版装到游戏目录的残留，
 * 星之魔女酒狐模型包按配置解压并被车万女仆读到。
 */
@DisplayName("车万女仆模型包：旧包清理与星之魔女酒狐包安装")
@Scene("tlms")
class ModelPackTest {
    private static final Path PACK_DIR = FMLPaths.GAMEDIR.get().resolve("tlm_custom_pack");
    private static final Path LEGACY = PACK_DIR.resolve("maidspell_geckolib_models-1.0.0");
    private static final Path STAR_WITCH = PACK_DIR.resolve("star_witch_winefox-1.0.0");
    private static final String REMOVED_LOG = "Removed obsolete Touhou Little Maid compatibility model pack";

    @DisplayName("不再内置旧的 geckolib 兼容模型包，服务端模型表里也没有它的模型")
    @Test
    void noBundledGeckolibPack(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            ctx.check(Files.exists(LEGACY)).as(LEGACY + " 存在").isFalse();
            ctx.check(ServerCustomPackLoader.SERVER_MAID_MODELS.containsInfo("geckolib:winefox_saint_black"))
                    .as("服务端模型表含已移除的 geckolib:winefox_saint_black").isFalse();
        });
    }

    /** 放一个带子目录的旧包和一个同级的其他包，清理后只删旧包，日志记一行。 */
    @DisplayName("清理时只删旧版残留包，同级的其他包保留，日志记一行")
    @Test
    void legacyPackRemovedOthersKept(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            Path sibling = PACK_DIR.resolve("tlms_test_sibling_pack");
            ctx.cleanup(() -> delete(sibling));
            write(LEGACY.resolve("assets/geckolib/models/entity/old.json"), "{}");
            write(LEGACY.resolve("pack.mcmeta"), "{}");
            write(sibling.resolve("pack.mcmeta"), "{}");
            long logged = removedLogCount();
            TouhouLittleMaidLegacyModelPackCleaner.cleanGameDirectory();
            ctx.check(Files.exists(LEGACY)).as("清理后旧包仍在").isFalse();
            ctx.check(Files.exists(sibling.resolve("pack.mcmeta"))).as("同级的其他包仍在").isTrue();
            ctx.check(Files.exists(STAR_WITCH)).as("星之魔女酒狐模型包仍在").isTrue();
            ctx.check(removedLogCount()).as("日志「" + REMOVED_LOG + "」条数").isEqualTo(logged + 1);
        });
    }

    @DisplayName("旧版残留目录不存在时，清理器报告未删除")
    @Test
    void cleanerWithoutLegacyDir(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            delete(LEGACY);
            boolean removed = Reflect.call(null, TouhouLittleMaidLegacyModelPackCleaner.class, "deleteLegacyPack",
                    new Class<?>[]{Path.class}, FMLPaths.GAMEDIR.get());
            ctx.check(removed).as("目录不存在时报告已删除").isFalse();
        });
    }

    /** 首次启动后解压出的星之魔女酒狐包文件齐全，车万女仆服务端读到了这个模型和 1.5.3 自带的两个酒狐模型。 */
    @DisplayName("首次启动后星之魔女酒狐包文件齐全，车万女仆服务端读到其模型和两个自带酒狐模型")
    @Test
    void starWitchPackInstalled(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            List<String> files = Reflect.field(null, TouhouLittleMaidModelPackInstaller.class, "PACK_FILES");
            List<String> missing = files.stream().filter(file -> !Files.isRegularFile(STAR_WITCH.resolve(file))).toList();
            ctx.check(missing).as(STAR_WITCH + " 缺少的文件").isEmpty();
            for (String model : List.of("touhou_little_maid_spell:stellar_witch", "geckolib:winefox_elf", "geckolib:winefox_saint")) {
                ctx.check(ServerCustomPackLoader.SERVER_MAID_MODELS.containsInfo(model)).as("服务端模型表含 " + model).isTrue();
            }
        });
    }

    /** 关掉 autoInstallTlmModelPack 时不解压（先把已解压的包挪开，测试结束挪回）。 */
    @DisplayName("关闭自动安装模型包配置时不解压星之魔女酒狐包")
    @Test
    void autoInstallOffSkipsUnpack(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            Path aside = PACK_DIR.resolve("tlms_test_star_witch_aside");
            boolean previous = Config.autoInstallTlmModelPack;
            move(STAR_WITCH, aside);
            ctx.cleanup(() -> {
                Config.autoInstallTlmModelPack = previous;
                delete(STAR_WITCH);
                move(aside, STAR_WITCH);
            });
            Config.autoInstallTlmModelPack = false;
            ctx.check(TouhouLittleMaidModelPackInstaller.installIfNeeded()).as("配置关闭时报告已解压").isFalse();
            ctx.check(Files.exists(STAR_WITCH)).as("配置关闭时解压出了 " + STAR_WITCH.getFileName()).isFalse();
        });
    }

    private static long removedLogCount() {
        List<String> lines = Logs.lines();
        return lines == null ? -1 : lines.stream().filter(line -> line.contains(REMOVED_LOG)).count();
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void move(Path from, Path to) {
        if (!Files.exists(from)) {
            return;
        }
        try {
            Files.move(from, to);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
