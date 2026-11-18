package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.api.IMovementGuardedEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * 让原版 {@code /tp} 对受移动判定的实体静默失败。
 *
 * <p>只靠实体侧拒绝还不够：{@code performTeleport} 是 {@code void}，调用方在循环结束后
 * <b>无条件</b> {@code sendSuccess}，于是命令会报告"已传送"而实体其实没动。这里在入口处
 * 判断整批目标是否全部受保护：是则直接返回 0 —— 不传送、不打印回执。</p>
 *
 * <p>混合目标时仍然放行（受保护的那些由实体侧的守卫静默拒绝；回执里的数量会包含它们，
 * 这是可接受的边界，不值得为此再拆一层参数过滤）。</p>
 *
 * <p>注意：{@code /teleport}、{@code /execute ... run tp}、{@code /tp <targets> <entity>}
 * 都走这两个方法，所以不需要逐个命令处理。</p>
 */
@Mixin(TeleportCommand.class)
public abstract class TeleportCommandMixin {
    @Inject(method = "teleportToPos", at = @At("HEAD"), cancellable = true, remap = true)
    private static void maidspell$silenceGuardedTeleportToPos(
            CallbackInfoReturnable<Integer> cir,
            @Local(argsOnly = true) Collection<? extends Entity> entities) {
        if (maidspell$allGuarded(entities)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "teleportToEntity", at = @At("HEAD"), cancellable = true, remap = true)
    private static void maidspell$silenceGuardedTeleportToEntity(
            CallbackInfoReturnable<Integer> cir,
            @Local(argsOnly = true) Collection<? extends Entity> entities) {
        if (maidspell$allGuarded(entities)) {
            cir.setReturnValue(0);
        }
    }

    private static boolean maidspell$allGuarded(Collection<? extends Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        for (Entity entity : entities) {
            if (!(entity instanceof IMovementGuardedEntity guard)
                || !guard.maidspell$blocksExternalMovement()) {
                return false;
            }
        }
        return true;
    }
}
