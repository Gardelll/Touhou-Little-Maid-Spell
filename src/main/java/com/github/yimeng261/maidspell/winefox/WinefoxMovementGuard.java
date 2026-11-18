package com.github.yimeng261.maidspell.winefox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 星之魔女外部移动判定的纯状态机（不引用任何 Minecraft 类型，便于单元测试）。
 *
 * <p>判定模型：
 * <ul>
 *   <li>实体加入世界前（{@code armed == false}）一律放行 —— 刷怪蛋、结构生成、读档
 *       都要能写初始位置；</li>
 *   <li>加入世界后，只有 {@code trustedDepth > 0}（她自己 {@code aiStep} 的调用栈）或
 *       {@code bypassDepth > 0}（内部生命周期、管理员命令）里的写入才被接受；</li>
 *   <li>客户端永远放行，服务端才是权威。</li>
 * </ul>
 *
 * <p>三种"允许"是三条独立理由，不能合并成一个布尔：{@code enabled == false} 是调试开关，
 * {@code !armed} 是加载窗口，{@code trusted/bypass} 是调用栈归属。合并之后就没法区分
 * "还没加入世界"和"外部模组在写"。
 */
public final class WinefoxMovementGuard {
    private boolean armed;
    private int trustedDepth;
    private int bypassDepth;
    private final Map<String, Integer> rejections = new LinkedHashMap<>();
    private String lastRejected = "";

    public boolean canChange(boolean clientSide, boolean enabled) {
        if (clientSide || !this.armed || !enabled) {
            return true;
        }
        return this.trustedDepth > 0 || this.bypassDepth > 0;
    }

    /** 外部写现在是否会被拒绝；命令侧用它决定要不要静默失败。 */
    public boolean blocksExternalWrites(boolean clientSide, boolean enabled) {
        return !this.canChange(clientSide, enabled);
    }

    public void arm() {
        this.armed = true;
    }

    public void disarm() {
        this.armed = false;
    }

    public boolean isArmed() {
        return this.armed;
    }

    public void enterTrusted() {
        this.trustedDepth++;
    }

    public void exitTrusted() {
        if (this.trustedDepth > 0) {
            this.trustedDepth--;
        }
    }

    public void enterBypass() {
        this.bypassDepth++;
    }

    public void exitBypass() {
        if (this.bypassDepth > 0) {
            this.bypassDepth--;
        }
    }

    public boolean isTrusted() {
        return this.trustedDepth > 0;
    }

    public boolean isBypassing() {
        return this.bypassDepth > 0;
    }

    public void recordRejection(String method) {
        this.rejections.merge(method, 1, Integer::sum);
        this.lastRejected = method;
    }

    public int rejectedCount(String method) {
        return this.rejections.getOrDefault(method, 0);
    }

    public String lastRejected() {
        return this.lastRejected;
    }

    /** 诊断用的一行摘要：最近一次被拒绝的入口 + 各入口计数。 */
    public String describe() {
        return "last=" + (this.lastRejected.isEmpty() ? "none" : this.lastRejected)
            + ", counts=" + this.rejections;
    }

    public void resetRejections() {
        this.rejections.clear();
        this.lastRejected = "";
    }
}
