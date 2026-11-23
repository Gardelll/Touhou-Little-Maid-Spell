package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.coremod.AnchorSectionContents;
import com.github.yimeng261.maidspell.coremod.AnchorSectionMembership;
import com.github.yimeng261.maidspell.coremod.AnchorSectionStorageIndex;
import com.github.yimeng261.maidspell.coremod.AnchorWriteHooks;
import com.github.yimeng261.maidspell.mixin.accessor.AnchorEntitySectionAccessor;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public abstract class AnchorPersistentSectionCallbackMixin<T extends EntityAccess> implements AnchorSectionMembership {
    @Shadow @Final private T entity;
    @Shadow private long currentSectionKey;
    @Shadow private EntitySection<T> currentSection;

    @Override
    @SuppressWarnings("unchecked")
    public boolean maidspell$restoreSectionMembership(EntitySectionStorage<?> storage) {
        long expected = SectionPos.asLong(entity.blockPosition());
        if (currentSectionKey != expected) return false;
        var typedStorage = (EntitySectionStorage<T>) storage;
        var section = typedStorage.getOrCreateSection(expected);
        boolean repaired = ((AnchorSectionStorageIndex<?>) storage).maidspell$ensureSectionId(expected);
        if (section != currentSection) {
            AnchorWriteHooks.removeSectionEntryForLifecycle(currentSection, entity);
            currentSection = section;
            repaired = true;
        }
        var contents = (AnchorSectionContents<T>) ((AnchorEntitySectionAccessor<T>) section).maidspell$contents();
        return contents.maidspell$ensureSectionEntry(entity) || repaired;
    }

    @Inject(method = "onRemove", at = @At("HEAD"), cancellable = true)
    private void maidspell$requireRemovalState(Entity.RemovalReason reason, CallbackInfo ci) {
        if (entity instanceof Entity live && !live.isRemoved()
                && AnchorWriteHooks.blockSectionEntryRemoval(live)) {
            ci.cancel();
        }
    }

    @Redirect(method = {"onMove", "onRemove"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/EntitySection;remove(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private boolean maidspell$removeFromCurrentSection(EntitySection<T> section, T entry) {
        return AnchorWriteHooks.removeSectionEntryForLifecycle(section, entry);
    }
}
