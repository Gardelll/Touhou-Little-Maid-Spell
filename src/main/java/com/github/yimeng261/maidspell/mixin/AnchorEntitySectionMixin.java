package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.coremod.AnchorSectionContents;
import com.github.yimeng261.maidspell.coremod.AnchorWriteHooks;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySection.class)
public abstract class AnchorEntitySectionMixin<T extends EntityAccess> {
    @Shadow @Final private ClassInstanceMultiMap<T> storage;

    @Inject(method = "add", at = @At("HEAD"))
    private void maidspell$markSectionContents(T entity, CallbackInfo ci) {
        if (entity instanceof EntityMaid maid && !maid.level().isClientSide()) {
            ((AnchorSectionContents<?>) storage).maidspell$markSectionContents();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void maidspell$guardSectionEntry(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (AnchorWriteHooks.blockSectionEntryRemoval(entity)) {
            cir.setReturnValue(false);
        }
    }
}
