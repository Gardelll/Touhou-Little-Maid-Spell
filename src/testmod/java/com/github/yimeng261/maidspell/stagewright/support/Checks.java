package com.github.yimeng261.maidspell.stagewright.support;

import com.github.yimeng261.maidspell.MaidSpellMod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** 常用软断言。 */
public final class Checks {
    /** 本模组的注册名前缀。 */
    public static final String NS = MaidSpellMod.MOD_ID + ":";
    private Checks() {
    }

    /** 集合完全相等：分别报告缺少和多出的元素，便于直接看出差异。 */
    public static void sameSet(Stage ctx, String label, Collection<String> expected, Collection<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(expected);
        ctx.check(missing).as(label + " 缺少").isEmpty();
        ctx.check(extra).as(label + " 多出").isEmpty();
    }

    /** 多重集合相等：按元素计数比较，报告缺少和多出的元素及次数差。 */
    public static void sameMultiset(Stage ctx, String label, Collection<String> expected, Collection<String> actual) {
        Map<String, Integer> diff = new TreeMap<>();
        expected.forEach(e -> diff.merge(e, 1, Integer::sum));
        actual.forEach(a -> diff.merge(a, -1, Integer::sum));
        List<String> missing = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        diff.forEach((key, count) -> {
            if (count > 0) {
                missing.add(key + " ×" + count);
            } else if (count < 0) {
                extra.add(key + " ×" + -count);
            }
        });
        ctx.check(missing).as(label + " 缺少").isEmpty();
        ctx.check(extra).as(label + " 多出").isEmpty();
    }
}
