package com.github.yimeng261.maidspell.mixin.travelerstitles;

import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 让按玩家 UUID 命名的独立归隐之地与共享归隐之地使用同一个维度标题。 */
@Pseudo
@Mixin(targets = "com.yungnickyoung.minecraft.travelerstitles.render.TitleRenderManager", remap = false)
public abstract class TitleRenderManagerMixin {
    // 方法里第一个 String 局部变量是 dimensionNameKey，标题文字和颜色都按它查
    @ModifyVariable(method = "updateDimensionTitle", at = @At(value = "STORE", ordinal = 0),
            ordinal = 0, remap = false, require = 0)
    private String maidspell$useSharedRetreatTitle(String dimensionNameKey, Level world) {
        if (TheRetreatDimension.isPrivateRetreatDimension(world.dimension().location())) {
            return "travelerstitles.touhou_little_maid_spell.the_retreat";
        }
        return dimensionNameKey;
    }
}
