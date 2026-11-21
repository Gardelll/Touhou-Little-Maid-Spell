package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * 万法酒狐直接复用车万女仆的渲染器：模型、贴图、动画控制器、持物层全部走 TLM 那一套，
 * 具体模型由 {@code MagicalWinefoxBossEntity.getModelId()} 指到内置模型包里那份。
 */
public class MagicalWinefoxBossRenderer extends EntityMaidRenderer {
    public MagicalWinefoxBossRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(Mob entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Player target = entity instanceof MagicalWinefoxBossEntity witch ? witch.getVisualFacingTarget() : null;
        if (target == null) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        // 与 LevelRenderer 本帧使用的位置一致，包括客户端此刻看到的玩家位置。
        double dx = Mth.lerp((double) partialTick, target.xOld, target.getX())
                - Mth.lerp((double) partialTick, entity.xOld, entity.getX());
        double dz = Mth.lerp((double) partialTick, target.zOld, target.getZ())
                - Mth.lerp((double) partialTick, entity.zOld, entity.getZ());
        if (dx * dx + dz * dz < 1.0E-4D) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float correction = Mth.wrapDegrees(targetYaw - headYaw);
        float bodyRot = entity.yBodyRot;
        float oldBodyRot = entity.yBodyRotO;
        float headRot = entity.yHeadRot;
        float oldHeadRot = entity.yHeadRotO;
        // TLM 直接读取并插值这几个角度。身体和头部平移同一个量以保持头相对身体的角度，渲染完再还原网络同步的状态。
        entity.yBodyRot = bodyRot + correction;
        entity.yBodyRotO = oldBodyRot + correction;
        entity.yHeadRot = headRot + correction;
        entity.yHeadRotO = oldHeadRot + correction;
        try {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            entity.yBodyRot = bodyRot;
            entity.yBodyRotO = oldBodyRot;
            entity.yHeadRot = headRot;
            entity.yHeadRotO = oldHeadRot;
        }
    }
}
