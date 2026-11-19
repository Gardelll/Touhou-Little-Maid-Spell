package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentEntitySectionManager.class)
public abstract class AnchorPersistentSectionSaveMixin {
    @Inject(method = {"autoSave", "saveAll"}, at = @At("HEAD"))
    private void maidspell$checkAnchoredSectionsBeforeSave(CallbackInfo ci) {
        MaidHardRemovalProtection.restoreSectionsBeforeSave((PersistentEntitySectionManager<?>) (Object) this);
    }
}
