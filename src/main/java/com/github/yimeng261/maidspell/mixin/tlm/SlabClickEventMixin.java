package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.event.maid.SlabClickEvent;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 魂符收女仆由服务端说了算：客户端不预测，魂符没写进女仆数据就不收，收走时的 discard 走授权移除。
 */
@Mixin(value = SlabClickEvent.class, remap = false)
public class SlabClickEventMixin {
    @Inject(method = "onInteract", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maidspell$checkSlabCapture(InteractMaidEvent event, CallbackInfo ci) {
        if (!event.getStack().is(InitItems.SMART_SLAB_EMPTY.get())) return;

        // 手上的物品和女仆的移除都归服务端处理。
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide() || maid.level() != event.getPlayer().level()
                || !maid.isAlive() || !maid.isOwnedBy(event.getPlayer())
                || event.getStack() != event.getPlayer().getMainHandItem()) {
            event.setCanceled(true);
            ci.cancel();
        }
    }

    @WrapOperation(method = "onInteract", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/github/tartaricacid/touhoulittlemaid/item/ItemSmartSlab;storeMaidData(Lnet/minecraft/world/item/ItemStack;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V"))
    private static void maidspell$serializeForSlab(ItemStack output, EntityMaid maid, Operation<Void> original,
                                                   @Share("maidspell$slabSerialized") LocalBooleanRef serialized) {
        AnchorCoreProtection.runWithInternalSerialization(maid, () -> original.call(output, maid));
        CustomData stored = output.get(InitDataComponent.MAID_INFO);
        var data = stored == null ? null : stored.copyTag();
        serialized.set(data != null && data.hasUUID("UUID") && maid.getUUID().equals(data.getUUID("UUID"))
                && data.hasUUID("Owner") && data.getUUID("Owner").equals(maid.getOwnerUUID()));
    }

    @Inject(method = "onInteract", remap = false, cancellable = true, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;spawnExplosionParticle()V"))
    private static void maidspell$requireSerializedSlab(InteractMaidEvent event, CallbackInfo ci,
                                                       @Share("maidspell$slabSerialized") LocalBooleanRef serialized) {
        if (!serialized.get()) {
            event.setCanceled(true);
            ci.cancel();
        }
    }

    @WrapOperation(method = "onInteract", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;discard()V"))
    private static void maidspell$authorizeSlabDiscard(EntityMaid maid, Operation<Void> original) {
        MaidHardRemovalProtection.runAuthorizedRemoval(maid, () -> original.call(maid));
    }

    @Inject(method = "onInteract", remap = false, cancellable = true, at = @At(value = "INVOKE", remap = true,
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;discard()V", shift = At.Shift.AFTER))
    private static void maidspell$requireSlabDiscard(InteractMaidEvent event, CallbackInfo ci) {
        if (!event.getMaid().isRemoved()) {
            event.setCanceled(true);
            ci.cancel();
        }
    }
}
