package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.AnchorCoreWhitelist;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 锚定核心调用者白名单（专用服）：内置前缀、按类名（连同嵌套类）、按前缀、按谓词四种匹配，
 * 以及注册前后对锚定女仆 discard 的拦截。
 *
 * <p>注册是进程级且不可撤销的，这里注册的类名都在虚构的 {@code org.example.anchorprobe} 下，
 * 不会放行真实代码；放行测试引擎的谓词只在 {@link #TRUST_TEST_ENGINE} 打开期间生效。
 * 类名避开内置白名单按子串放行的 maid、backup、c2me。</p>
 */
@Scene("tlms")
@DisplayName("锚定核心：调用者白名单（专用服）")
class AnchorWhitelistTest {
    private static final String PROBE = "org.example.anchorprobe.";

    /** 打开时放行测试引擎的帧；ctx.run 的任务经测试引擎的 GameImpl 派发到服务端线程，它默认不可信。 */
    private static final AtomicBoolean TRUST_TEST_ENGINE = new AtomicBoolean();

    static {
        AnchorCoreWhitelist.addAllowedCaller(name -> TRUST_TEST_ENGINE.get() && name.startsWith("net.magicterra.stagewright."));
    }

    @DisplayName("内置白名单放行原版与本模组，空类名和陌生类不可信")
    @Test
    void builtInRules(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(null)).as("null").isFalse();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed("")).as("空串").isFalse();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed("net.minecraft.server.level.ServerLevel")).as("原版").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed("net.neoforged.neoforge.common.NeoForge")).as("NeoForge").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed("com.github.yimeng261.maidspell.MaidSpellMod")).as("本模组").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(PROBE + "Stranger")).as("未注册的陌生类").isFalse();
    }

    @DisplayName("按类注册放行该类及其内部类、匿名类，不放行同前缀的别的类")
    @Test
    void classCoversNested(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        String outer = PROBE + "Outer";
        AnchorCoreWhitelist.addAllowedClass(outer);
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(outer)).as("注册的类本身").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(outer + "$Inner")).as("内部类").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(outer + "$1")).as("匿名类").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(outer + "$Inner$2")).as("内部类里的匿名类").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(outer + "Twin")).as("类名以它开头的另一个类").isFalse();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(outer + ".Sub")).as("同名包下的类").isFalse();
    }

    @DisplayName("注册内部类不连带放行外层类和兄弟内部类")
    @Test
    void innerDoesNotCoverOuter(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        String host = PROBE + "Host";
        AnchorCoreWhitelist.addAllowedClass(host + "$Inner");
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(host + "$Inner")).as("注册的内部类").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(host + "$Inner$1")).as("内部类里的匿名类").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(host)).as("外层类").isFalse();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(host + "$Other")).as("兄弟内部类").isFalse();
    }

    @DisplayName("按前缀注册原样 startsWith 匹配，注册时去掉首尾空白")
    @Test
    void packagePrefix(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        AnchorCoreWhitelist.addAllowedPackage("  " + PROBE + "pkg. ");
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(PROBE + "pkg.Handler")).as("包里的类").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(PROBE + "pkg.deep.Handler")).as("子包里的类").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(PROBE + "pkgx.Handler")).as("名字相近的另一个包").isFalse();
    }

    @DisplayName("谓词抛异常按不匹配处理；空白或 null 名称注册时报错")
    @Test
    void predicateAndArguments(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        String thrower = PROBE + "throwing.Handler";
        AnchorCoreWhitelist.addAllowedCaller(name -> {
            if (name.startsWith(PROBE + "throwing.")) {
                throw new IllegalStateException("谓词故意抛出");
            }
            return name.equals(PROBE + "predicate.Handler");
        });
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(PROBE + "predicate.Handler")).as("谓词匹配的类").isTrue();
        ctx.check(AnchorCoreWhitelist.isCallerAllowed(thrower)).as("谓词抛异常的类").isFalse();
        ctx.check(thrown(() -> AnchorCoreWhitelist.addAllowedPackage("   "))).as("空白前缀").isEqualTo(IllegalArgumentException.class);
        ctx.check(thrown(() -> AnchorCoreWhitelist.addAllowedClass("   "))).as("空白类名").isEqualTo(IllegalArgumentException.class);
        ctx.check(thrown(() -> AnchorCoreWhitelist.addAllowedClass((String) null))).as("null 类名").isEqualTo(NullPointerException.class);
        ctx.check(thrown(() -> AnchorCoreWhitelist.addAllowedCaller(null))).as("null 谓词").isEqualTo(NullPointerException.class);
    }

    @DisplayName("不可信调用者 discard 锚定女仆被拦下；注册放行后同样的调用能删掉她")
    @Test
    void registrationLiftsBlock(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid maid = ctx.get(() -> {
            EntityMaid m = Actors.spawn(ctx, Actors.MAID, 0, 0, 0, true, (EntityMaid e) -> {
                e.setTame(true, false);
                e.setOwnerUUID(UUID.randomUUID());
            });
            Actors.equipBauble(m, AnchorTest.ANCHOR);
            return m;
        });
        ctx.await(10, "锚定核心生效", () -> AnchorCoreProtection.hasAnchorCore(maid));
        ctx.run(maid::discard);
        ctx.ticks(2);
        ctx.run(() -> {
            ctx.check(maid.isRemoved()).as("未注册时 discard 后女仆被移除").isFalse();
            ctx.check(ctx.level().getEntity(maid.getUUID())).as("未注册时世界里的女仆").isSameAs(maid);
            ctx.check(AnchorCoreProtection.hasAnchorCore(maid)).as("未注册时女仆仍戴锚定核心").isTrue();
        });
        ctx.run(() -> {
            TRUST_TEST_ENGINE.set(true);
            try {
                maid.discard();
            } finally {
                TRUST_TEST_ENGINE.set(false);
            }
        });
        ctx.ticks(2);
        ctx.run(() -> {
            ctx.check(maid.isRemoved()).as("注册放行后 discard 后女仆被移除").isTrue();
            ctx.check(ctx.level().getEntity(maid.getUUID())).as("注册放行后世界里的女仆").isNull();
        });
    }

    /** 执行并返回抛出的异常类型，没抛出时返回 null。 */
    private static Class<?> thrown(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e.getClass();
        }
    }
}
