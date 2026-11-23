package com.github.yimeng261.maidspell.coremod;

import net.minecraft.world.level.entity.EntityAccess;

public interface AnchorSectionStorageIndex<T extends EntityAccess> {
    boolean maidspell$ensureSectionId(long key);
}
