package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 魂符放出女仆：女仆没能进入世界时不清空魂符，放出成功时为新实例登记锚定保护。
 * 放出逻辑在魂符与照片共用的父类里，这里只处理魂符。
 */
@Mixin(value = AbstractStoreMaidItem.class, remap = false)
public abstract class ItemSmartSlabMixin {
    @WrapOperation(method = "spawnFromStore", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean maidspell$rememberSlabRelease(Level level, Entity entity, Operation<Boolean> original,
                                                 @Share("maidspell$slabReleased") LocalBooleanRef released) {
        boolean added = original.call(level, entity);
        released.set(added);
        if (added && (Object) this instanceof ItemSmartSlab && entity instanceof EntityMaid maid) {
            MaidHardRemovalProtection.rememberIfProtected(maid);
        }
        return added;
    }

    @Inject(method = "spawnFromStore", remap = false, cancellable = true, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;spawnExplosionParticle()V"))
    private void maidspell$keepSlabWhenJoinFails(UseOnContext context, Player player, Level level, EntityMaid maid,
                                                Runnable consumer, CallbackInfoReturnable<InteractionResult> cir,
                                                @Share("maidspell$slabReleased") LocalBooleanRef released) {
        if ((Object) this instanceof ItemSmartSlab && level instanceof ServerLevel && !released.get()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
