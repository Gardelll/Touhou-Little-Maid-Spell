package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.api.AnchorTickIndex;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityTickList.class)
public abstract class AnchorEntityTickListMixin implements AnchorTickIndex {
    @Shadow private Int2ObjectMap<Entity> active;
    @Shadow private Int2ObjectMap<Entity> passive;
    @Shadow private Int2ObjectMap<Entity> iterated;

    @Override public Int2ObjectMap<Entity> maidspell$activeIndex() { return active; }
    @Override public Int2ObjectMap<Entity> maidspell$passiveIndex() { return passive; }
    @Override public Int2ObjectMap<Entity> maidspell$iteratedIndex() { return iterated; }

    @Override
    public void maidspell$publishTickIndexes(Int2ObjectMap<Entity> active, Int2ObjectMap<Entity> passive) {
        this.active = active;
        this.passive = passive;
    }
}
