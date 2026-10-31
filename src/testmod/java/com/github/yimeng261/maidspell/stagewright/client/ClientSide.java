package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.stagewright.support.Mismatches;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 集成服测试读写同一 JVM 里的客户端：把操作交给客户端线程执行，测试线程等结果。
 * <p>只在集成拓扑的测试里使用；专用服上没有客户端。
 */
public final class ClientSide {
    /** 等客户端线程执行完的上限：客户端刚进世界时还在加载资源、文档和进度，线程可能连续几秒不响应。 */
    private static final int WAIT_TICKS = 600;

    private ClientSide() {
    }

    /** 等客户端线程执行完排队的任务（测试线程）。 */
    public static void awaitIdle(Stage ctx) {
        CompletableFuture<Boolean> idle = Minecraft.getInstance().submit(() -> Boolean.TRUE);
        ctx.await(WAIT_TICKS, "客户端线程空闲", idle::isDone);
    }

    /** 在客户端线程上运行 action 并返回结果（测试线程）。action 抛出的异常让测试失败。 */
    public static <T> T call(Stage ctx, Supplier<T> action) {
        CompletableFuture<T> future = Minecraft.getInstance().submit(action);
        ctx.await(WAIT_TICKS, "客户端线程执行完", future::isDone);
        try {
            return future.join();
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException r) throw r;
            if (cause instanceof Error err) throw err;
            throw e;
        }
    }

    public static void run(Stage ctx, Runnable action) {
        call(ctx, () -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    /** 在客户端线程上逐项比对，再断言没有不一致（测试线程）。body 抛出的异常让测试失败。 */
    public static void verify(Stage ctx, String label, Consumer<Mismatches> body) {
        Mismatches mismatches = call(ctx, () -> {
            Mismatches m = new Mismatches();
            body.accept(m);
            return m;
        });
        mismatches.verify(ctx, label);
    }

    /** 当前界面里消息为该翻译键的第一个控件（没有时为 null）。只能在客户端线程上调用。 */
    public static AbstractWidget widget(Screen screen, String translationKey) {
        return widget(screen, w -> translationKey.equals(key(w.getMessage())));
    }

    public static AbstractWidget widget(Screen screen, Predicate<AbstractWidget> filter) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w && filter.test(w)) {
                return w;
            }
        }
        return null;
    }

    public static List<AbstractWidget> widgets(Screen screen) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
    }

    /** 文本组件的翻译键；不是翻译组件时为 null。 */
    public static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }

    /** 按指定语言加载本客户端的全部语言文件（与切换语言时一样，含所有模组）。只能在客户端线程上调用。 */
    public static ClientLanguage language(String code) {
        return ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(code), false);
    }
}
