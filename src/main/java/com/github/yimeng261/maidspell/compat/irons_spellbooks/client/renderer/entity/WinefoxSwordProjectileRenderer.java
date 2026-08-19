package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.model.WinefoxSwordProjectileModel;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.render.RenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WinefoxSwordProjectileRenderer extends GeoEntityRenderer<WinefoxSwordProjectileEntity> {
    public WinefoxSwordProjectileRenderer(EntityRendererProvider.Context context) {
        super(context, new WinefoxSwordProjectileModel());
        shadowRadius = 0.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, WinefoxSwordProjectileEntity entity,
                          BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    @Override
    public @Nullable RenderType getRenderType(WinefoxSwordProjectileEntity entity,
                                               ResourceLocation texture,
                                               @Nullable MultiBufferSource bufferSource,
                                               float partialTick) {
        return RenderHelper.CustomerRenderType.magic(texture);
    }

    @Override
    public Color getRenderColor(WinefoxSwordProjectileEntity entity, float partialTick, int packedLight) {
        return Color.LIGHT_GRAY;
    }

    @Override
    protected void applyRotations(WinefoxSwordProjectileEntity entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        Vec3 direction = entity.isPlanted()
                ? entity.getPlantedDirection()
                : entity.getDeltaMovement();
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = new Vec3(0.0D, -1.0D, 0.0D);
        }

        Vector3f targetDirection = new Vector3f(
                (float) direction.x, (float) direction.y, (float) direction.z).normalize();
        // The source's handle extends toward +Z and its blade toward -Z. After
        // the converted root rotation, the blade-first direction is local -X.
        poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(-1.0F, 0.0F, 0.0F), targetDirection));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRoll()));
    }
}
