package com.github.yimeng261.maidspell.mixin.accessor;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntitySection.class)
public interface AnchorEntitySectionAccessor<T extends EntityAccess> {
    @Accessor("storage")
    ClassInstanceMultiMap<T> maidspell$contents();
}
