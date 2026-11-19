package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.api.AnchorSectionContents;
import com.github.yimeng261.maidspell.coremod.AnchorSectionList;
import com.github.yimeng261.maidspell.coremod.AnchorWriteHooks;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(ClassInstanceMultiMap.class)
public abstract class AnchorClassInstanceMultiMapMixin<T> implements AnchorSectionContents<T> {
    @Shadow @Final @Mutable private List<T> allInstances;
    @Shadow @Final private Class<T> baseClass;
    @Shadow @Final private Map<Class<?>, List<T>> byClass;
    @Unique private boolean maidspell$sectionContents;

    @Override
    public void maidspell$markSectionContents() {
        if (maidspell$sectionContents) return;
        // 复制一次，持有旧底层列表引用的代码就改不到已发布的区段。
        List<T> guarded = AnchorSectionList.copyOf(allInstances);
        byClass.put(baseClass, guarded);
        allInstances = guarded;
        maidspell$sectionContents = true;
    }

    @Override
    public boolean maidspell$isSectionContents() {
        return maidspell$sectionContents;
    }

    @Override
    public boolean maidspell$ensureSectionEntry(T entry) {
        maidspell$markSectionContents();
        boolean changed = false;
        if (allInstances.stream().noneMatch(existing -> existing == entry)) {
            allInstances.add(entry);
            changed = true;
        }
        for (var index : byClass.entrySet()) {
            if (index.getKey().isInstance(entry)
                    && index.getValue().stream().noneMatch(existing -> existing == entry)) {
                index.getValue().add(entry);
                changed = true;
            }
        }
        return changed;
    }

    @Inject(method = "add", at = @At("RETURN"), cancellable = true, remap = false)
    private void maidspell$keepAuthoritativeListInSyncOnAdd(T entry, CallbackInfoReturnable<Boolean> cir) {
        // 按类索引的缓存可能暴露转发列表或独立变化，这里以权威列表为准补齐。
        if (maidspell$sectionContents && !allInstances.contains(entry)) {
            cir.setReturnValue(allInstances.add(entry) || cir.getReturnValueZ());
        }
    }

    @Inject(method = "remove", at = @At("RETURN"), cancellable = true, remap = false)
    private void maidspell$keepAuthoritativeListInSyncOnRemove(Object entry, CallbackInfoReturnable<Boolean> cir) {
        if (maidspell$sectionContents && allInstances.remove(entry)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true, remap = false)
    private void maidspell$guardSectionEntry(Object entity, CallbackInfoReturnable<Boolean> cir) {
        if (maidspell$sectionContents && AnchorWriteHooks.blockSectionEntryRemoval(entity)) {
            cir.setReturnValue(false);
        }
    }
}
