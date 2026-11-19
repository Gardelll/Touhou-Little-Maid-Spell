package com.github.yimeng261.maidspell.mixin.accessor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface AnchorEntityCallbackAccessor {
    @Accessor("levelCallback")
    EntityInLevelCallback maidspell$levelCallback();
}
