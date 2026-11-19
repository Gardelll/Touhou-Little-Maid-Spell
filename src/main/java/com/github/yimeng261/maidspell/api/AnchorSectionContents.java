package com.github.yimeng261.maidspell.api;

/** 标记实体区段持久化索引所用的集合。 */
public interface AnchorSectionContents<T> {
    void maidspell$markSectionContents();

    boolean maidspell$isSectionContents();

    boolean maidspell$ensureSectionEntry(T entry);
}
