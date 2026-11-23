package com.github.yimeng261.maidspell.mixin.accessor;

import com.github.yimeng261.maidspell.coremod.AnchorTrackedIndexEntry;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface AnchorTrackedIndexEntryMixin extends AnchorTrackedIndexEntry {
    @Override
    @Accessor("entity")
    Entity maidspell$trackedEntity();
}
