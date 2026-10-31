package com.github.yimeng261.maidspell.stagewright.support;

import net.magicterra.stagewright.test.Game;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 软断言：每个判定交给 {@link Game#check(boolean, String)}，失败记下并继续，测试结束时一并报告。
 * 写法 {@code ctx.check(actual).as("说明").isEqualTo(expected)}。
 */
public final class Soft<T> {
    private final Game game;
    private final T actual;
    private String label = "值";

    Soft(Game game, T actual) {
        this.game = game;
        this.actual = actual;
    }

    public Soft<T> as(String label) {
        this.label = label;
        return this;
    }

    private void judge(boolean holds, String expectation) {
        game.check(holds, label + "：期望" + expectation + "，实际为 " + describe(actual));
    }

    private static String describe(Object value) {
        if (value instanceof String s) {
            return "\"" + s + "\"";
        }
        return String.valueOf(value);
    }

    public void isEqualTo(Object expected) {
        judge(equal(actual, expected), "等于 " + describe(expected));
    }

    public void isNotEqualTo(Object unexpected) {
        judge(!equal(actual, unexpected), "不等于 " + describe(unexpected));
    }

    /** 数字按数值比较（int 与 long、float 与 double 混用时也算相等），其余按 equals。 */
    private static boolean equal(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y && !(a instanceof java.math.BigDecimal)) {
            if (isIntegral(x) && isIntegral(y)) {
                return x.longValue() == y.longValue();
            }
            return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
        }
        return Objects.equals(a, b);
    }

    private static boolean isIntegral(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }

    public void isSameAs(Object expected) {
        judge(actual == expected, "是同一个对象 " + describe(expected));
    }

    public void isTrue() {
        judge(Boolean.TRUE.equals(actual), "为 true");
    }

    public void isFalse() {
        judge(Boolean.FALSE.equals(actual), "为 false");
    }

    public void isNull() {
        judge(actual == null, "为 null");
    }

    public void isNotNull() {
        judge(actual != null, "不为 null");
    }

    public void isEmpty() {
        judge(size() == 0, "为空");
    }

    public void isNotEmpty() {
        judge(size() > 0, "不为空");
    }

    public void hasSize(int expected) {
        judge(size() == expected, "有 " + expected + " 个元素");
    }

    public void isPresent() {
        judge(actual instanceof Optional<?> o && o.isPresent(), "存在");
    }

    /** 字符串含子串，集合含元素，映射含键。 */
    public void contains(Object... expected) {
        for (Object e : expected) {
            judge(containsOne(e), "包含 " + describe(e));
        }
    }

    public void doesNotContain(Object... unexpected) {
        for (Object e : unexpected) {
            judge(actual != null && !containsOne(e), "不包含 " + describe(e));
        }
    }

    public void containsKey(Object key) {
        judge(actual instanceof Map<?, ?> m && m.containsKey(key), "含键 " + describe(key));
    }

    private boolean containsOne(Object e) {
        if (actual instanceof String s) {
            return e != null && s.contains(e.toString());
        }
        if (actual instanceof Collection<?> c) {
            return c.contains(e);
        }
        if (actual instanceof Map<?, ?> m) {
            return m.containsKey(e);
        }
        if (actual instanceof Object[] array) {
            return java.util.Arrays.asList(array).contains(e);
        }
        return false;
    }

    public void startsWith(String prefix) {
        judge(actual instanceof String s && s.startsWith(prefix), "以 " + describe(prefix) + " 开头");
    }

    public void endsWith(String suffix) {
        judge(actual instanceof String s && s.endsWith(suffix), "以 " + describe(suffix) + " 结尾");
    }

    public void isGreaterThan(Number bound) {
        judge(isNumber() && compare(bound) > 0, "大于 " + bound);
    }

    public void isLessThan(Number bound) {
        judge(isNumber() && compare(bound) < 0, "小于 " + bound);
    }

    public void isAtLeast(Number bound) {
        judge(isNumber() && compare(bound) >= 0, "至少 " + bound);
    }

    public void isAtMost(Number bound) {
        judge(isNumber() && compare(bound) <= 0, "至多 " + bound);
    }

    public void isBetween(Number low, Number high) {
        judge(isNumber() && compare(low) >= 0 && compare(high) <= 0, "在 [" + low + ", " + high + "] 之间");
    }

    public void isCloseTo(Number expected, Number tolerance) {
        judge(actual instanceof Number n && Math.abs(n.doubleValue() - expected.doubleValue()) <= tolerance.doubleValue(),
                "接近 " + expected + "（误差 " + tolerance + " 内）");
    }

    private boolean isNumber() {
        return actual instanceof Number;
    }

    private int compare(Number bound) {
        return Double.compare(((Number) actual).doubleValue(), bound.doubleValue());
    }

    private int size() {
        if (actual instanceof String s) {
            return s.length();
        }
        if (actual instanceof Collection<?> c) {
            return c.size();
        }
        if (actual instanceof Map<?, ?> m) {
            return m.size();
        }
        if (actual instanceof Optional<?> o) {
            return o.isPresent() ? 1 : 0;
        }
        if (actual instanceof Object[] array) {
            return array.length;
        }
        return -1;
    }
}
