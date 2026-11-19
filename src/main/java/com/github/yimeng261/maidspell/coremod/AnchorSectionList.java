package com.github.yimeng261.maidspell.coremod;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Predicate;

/** 持久化区段的权威条目列表；所有可写视图都经过同一道闸。 */
public final class AnchorSectionList<T> extends AbstractList<T> implements RandomAccess {
    private final List<T> entries;

    public static <T> AnchorSectionList<T> copyOf(Collection<T> existing) {
        return new AnchorSectionList<>(new ArrayList<>(existing));
    }

    private AnchorSectionList(List<T> entries) {
        this.entries = entries;
    }

    @Override
    public T get(int index) {
        return entries.get(index);
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public void add(int index, T entry) {
        entries.add(index, entry);
    }

    @Override
    public T set(int index, T entry) {
        T current = entries.get(index);
        if (current == entry) return current;
        requireReplaceable(current, entry);
        return entries.set(index, entry);
    }

    @Override
    public T remove(int index) {
        requireRemovable(entries.get(index));
        return entries.remove(index);
    }

    @Override
    public boolean remove(Object value) {
        int index = entries.indexOf(value);
        if (index < 0 || AnchorWriteHooks.blockSectionEntryRemoval(entries.get(index))) return false;
        entries.remove(index);
        return true;
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        Objects.requireNonNull(filter);
        return entries.removeIf(entry -> !AnchorWriteHooks.blockSectionEntryRemoval(entry)
                && filter.test(entry) && !AnchorWriteHooks.blockSectionEntryRemoval(entry));
    }

    @Override
    public void clear() {
        removeIf(entry -> true);
    }

    @Override
    public boolean removeAll(Collection<?> values) {
        Objects.requireNonNull(values);
        return removeIf(values::contains);
    }

    @Override
    public boolean retainAll(Collection<?> values) {
        Objects.requireNonNull(values);
        return removeIf(entry -> !values.contains(entry));
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return new AnchorSectionList<>(entries.subList(fromIndex, toIndex));
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        ListIterator<T> iterator = entries.listIterator(index);
        return new ListIterator<>() {
            private T current;
            private boolean hasCurrent;

            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public boolean hasPrevious() { return iterator.hasPrevious(); }
            @Override public int nextIndex() { return iterator.nextIndex(); }
            @Override public int previousIndex() { return iterator.previousIndex(); }

            @Override
            public T next() {
                current = iterator.next();
                hasCurrent = true;
                return current;
            }

            @Override
            public T previous() {
                current = iterator.previous();
                hasCurrent = true;
                return current;
            }

            @Override
            public void remove() {
                if (hasCurrent) requireRemovable(current);
                iterator.remove();
                hasCurrent = false;
            }

            @Override
            public void set(T entry) {
                if (hasCurrent && current != entry) {
                    requireReplaceable(current, entry);
                }
                iterator.set(entry);
                current = entry;
            }

            @Override
            public void add(T entry) {
                iterator.add(entry);
                hasCurrent = false;
            }
        };
    }

    @Override
    public Iterator<T> iterator() {
        return listIterator(0);
    }

    private void requireReplaceable(T current, T replacement) {
        requireRemovable(current);
        if (AnchorWriteHooks.blockSectionEntryRemoval(replacement) && entries.contains(replacement)) {
            throw new UnsupportedOperationException("不能用已在索引中的锚定女仆替换条目");
        }
    }

    private static void requireRemovable(Object entry) {
        if (AnchorWriteHooks.blockSectionEntryRemoval(entry)) {
            throw new UnsupportedOperationException("不能把受保护的女仆移出她的存盘区段");
        }
    }
}
