package com.github.yimeng261.maidspell.api;

import net.minecraft.world.level.entity.EntitySectionStorage;

/** 让仍在世界里的实体与她自己的持久化区段重新对齐。 */
public interface AnchorSectionMembership {
    boolean maidspell$restoreSectionMembership(EntitySectionStorage<?> storage);
}
