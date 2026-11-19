package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.AnchorGoalSelectorOwner;
import com.github.yimeng261.maidspell.coremod.AnchorWriteHooks;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(GoalSelector.class)
public abstract class AnchorGoalSelectorMixin implements AnchorGoalSelectorOwner {
    @Unique private EntityMaid maidspell$owner;

    @Override
    public void maidspell$bindMaid(EntityMaid maid) {
        maidspell$owner = maid;
    }

    @Inject(method = "removeAllGoals", at = @At("HEAD"), cancellable = true)
    private void maidspell$guardBulkGoalRemoval(Predicate<Goal> filter, CallbackInfo ci) {
        if (maidspell$owner != null && AnchorWriteHooks.blocksExternalMaidCleanup(maidspell$owner)) ci.cancel();
    }
}
