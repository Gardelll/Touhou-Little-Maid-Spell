package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.coremod.AnchorGoalSelectorOwner;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class AnchorMobGoalSelectorMixin {
    @Shadow @Final public GoalSelector goalSelector;
    @Shadow @Final public GoalSelector targetSelector;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void maidspell$bindGoalSelectors(CallbackInfo ci) {
        if ((Object) this instanceof EntityMaid maid && !maid.level().isClientSide()) {
            ((AnchorGoalSelectorOwner) goalSelector).maidspell$bindMaid(maid);
            ((AnchorGoalSelectorOwner) targetSelector).maidspell$bindMaid(maid);
        }
    }
}
