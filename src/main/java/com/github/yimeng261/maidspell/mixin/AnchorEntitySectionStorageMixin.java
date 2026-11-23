package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.coremod.AnchorSectionStorageIndex;
import com.github.yimeng261.maidspell.coremod.AnchorWriteHooks;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySectionStorage.class)
public abstract class AnchorEntitySectionStorageMixin<T extends EntityAccess> implements AnchorSectionStorageIndex<T> {
    @Shadow @Final private LongSortedSet sectionIds;

    @Override
    public boolean maidspell$ensureSectionId(long key) {
        return sectionIds.add(key);
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void maidspell$guardWholeSection(long key, CallbackInfo ci) {
        if (AnchorWriteHooks.containsProtectedSectionEntries(((EntitySectionStorage<?>) (Object) this).getSection(key))) {
            ci.cancel();
        }
    }
}
