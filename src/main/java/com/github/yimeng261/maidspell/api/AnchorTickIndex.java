package com.github.yimeng261.maidspell.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;

public interface AnchorTickIndex {
    Int2ObjectMap<Entity> maidspell$activeIndex();
    Int2ObjectMap<Entity> maidspell$passiveIndex();
    Int2ObjectMap<Entity> maidspell$iteratedIndex();
    void maidspell$publishTickIndexes(Int2ObjectMap<Entity> active, Int2ObjectMap<Entity> passive);
}
