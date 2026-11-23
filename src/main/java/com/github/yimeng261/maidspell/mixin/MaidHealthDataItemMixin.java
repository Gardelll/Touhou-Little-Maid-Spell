package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.MaidHealthDataItem;
import com.github.yimeng261.maidspell.utils.MaidHealthWriteGuard;
import com.github.yimeng261.maidspell.utils.SoulBookHealthProtection;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 魂之书女仆的生命数据项：绕过 SynchedEntityData#set 直接改数据项的写入也要经过许可，
 * 只放行回血、生命上限下降后截到上限的写入和本次结算许可的那一笔掉血。
 */
@Mixin(SynchedEntityData.DataItem.class)
public abstract class MaidHealthDataItemMixin<T> implements MaidHealthDataItem {
    @Shadow public abstract T getValue();
    @Unique private EntityMaid maidspell$healthOwner;

    @Override
    public void maidspell$bindHealthOwner(EntityMaid maid) {
        if (maidspell$healthOwner == null) maidspell$healthOwner = maid;
    }

    @Inject(method = "setValue", at = @At("HEAD"), cancellable = true)
    private void maidspell$validateStoredHealth(T value, CallbackInfo ci) {
        EntityMaid maid = maidspell$healthOwner;
        if (maid == null || !SoulBookHealthProtection.isProtected(maid)) return;
        if (!(value instanceof Float health) || !Float.isFinite(health)) {
            ci.cancel();
            return;
        }
        if (!MaidHealthWriteGuard.consume(maid, (SynchedEntityData.DataItem<?>) (Object) this, health)
                && getValue() instanceof Float previousHealth && health < previousHealth
                && health < maid.getMaxHealth()) {
            ci.cancel();
        }
    }
}
