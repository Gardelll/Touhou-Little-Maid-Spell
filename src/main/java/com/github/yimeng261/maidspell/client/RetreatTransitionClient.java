package com.github.yimeng261.maidspell.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.message.RetreatTransitionAckMessage;
import com.github.yimeng261.maidspell.network.message.RetreatTransitionMessage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.UUID;

/** 客户端播放进入归隐之地的转场：先遮满屏幕再让服务端传送，目标地形就绪后揭幕。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT)
public final class RetreatTransitionClient {
    private static final int COVER_AFTER_MS = 2200;
    private static final int HOLD_MS = 500;
    private static final int REVEAL_MS = 800;
    private static final int MAX_COVER_MS = 35000;
    private static Playback active;

    private RetreatTransitionClient() {
    }

    private static final class Playback {
        private final UUID token;
        private final ResourceLocation target;
        private final long startedAt = Util.getMillis();
        private long coveredAt = -1;
        private long revealedAt = -1;
        private boolean reportedReady;
        private boolean mayReveal;

        private Playback(UUID token, ResourceLocation target) {
            this.token = token;
            this.target = target;
        }
    }

    public static void receive(RetreatTransitionMessage message) {
        if (message.stage() == RetreatTransitionMessage.Stage.BEGIN && message.target() != null) {
            active = new Playback(message.token(), message.target());
        } else if (active != null && active.token.equals(message.token())) {
            if (message.stage() == RetreatTransitionMessage.Stage.REVEAL) {
                active.mayReveal = true;
            } else if (message.stage() == RetreatTransitionMessage.Stage.CANCEL) {
                active = null;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = null;
    }

    // 画在 HUD 和当前界面之后，原版的加载地形界面也会被盖住
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        Playback playback = active;
        if (playback == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        if (!dimension.equals(Level.OVERWORLD.location()) && !dimension.equals(playback.target)) {
            active = null;
            return;
        }

        long now = Util.getMillis();
        if (playback.coveredAt < 0 && now - playback.startedAt < COVER_AFTER_MS && !dimension.equals(playback.target)) {
            draw(minecraft, (now - playback.startedAt) / 1000.0);
            return;
        }
        if (playback.coveredAt < 0) {
            draw(minecraft, COVER_AFTER_MS / 1000.0);
            playback.coveredAt = now;
            acknowledge(playback, RetreatTransitionAckMessage.Stage.COVERED);
            return;
        }

        long coveredFor = now - playback.coveredAt;
        if (playback.revealedAt < 0 && coveredFor >= HOLD_MS) {
            if (coveredFor >= MAX_COVER_MS) {
                active = null;
                return;
            }
            if (dimension.equals(playback.target) && terrainReady(minecraft)) {
                if (playback.mayReveal) {
                    playback.revealedAt = now;
                } else if (!playback.reportedReady) {
                    playback.reportedReady = true;
                    acknowledge(playback, RetreatTransitionAckMessage.Stage.TERRAIN_READY);
                }
            }
        }
        if (playback.revealedAt < 0) {
            // 地形还在加载时停在完全遮屏的那一帧
            draw(minecraft, (COVER_AFTER_MS + Math.min(coveredFor, HOLD_MS)) / 1000.0);
        } else {
            long revealedFor = now - playback.revealedAt;
            if (revealedFor >= REVEAL_MS) {
                active = null;
            } else {
                draw(minecraft, (COVER_AFTER_MS + HOLD_MS + revealedFor) / 1000.0);
            }
        }
    }

    private static void acknowledge(Playback playback, RetreatTransitionAckMessage.Stage stage) {
        PacketDistributor.sendToServer(new RetreatTransitionAckMessage(playback.token, stage));
    }

    private static boolean terrainReady(Minecraft minecraft) {
        if (minecraft.screen instanceof ReceivingLevelScreen) {
            return false;
        }
        BlockPos position = minecraft.player.blockPosition();
        return minecraft.level.isOutsideBuildHeight(position.getY()) || minecraft.levelRenderer.isSectionCompiled(position);
    }

    private static void draw(Minecraft minecraft, double time) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        // 任意宽高比都按比例放大后居中裁切，像素画不变形
        float scale = Math.max((float) width / RetreatTransitionArt.WIDTH, (float) height / RetreatTransitionArt.HEIGHT);
        GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());

        var shader = RenderSystem.getShader();
        int texture0 = RenderSystem.getShaderTexture(0);
        int texture1 = RenderSystem.getShaderTexture(1);
        int texture2 = RenderSystem.getShaderTexture(2);
        int texture3 = RenderSystem.getShaderTexture(3);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        boolean blending = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean depthTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.pose().translate((width - RetreatTransitionArt.WIDTH * scale) / 2,
                    (height - RetreatTransitionArt.HEIGHT * scale) / 2, 0);
            graphics.pose().scale(scale, scale, 1);
            RetreatTransitionArt.draw(graphics, time);
        } finally {
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.setShaderTexture(1, texture1);
            RenderSystem.setShaderTexture(2, texture2);
            RenderSystem.setShaderTexture(3, texture3);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            if (!blending) {
                RenderSystem.disableBlend();
            }
            if (depthTest) {
                RenderSystem.enableDepthTest();
            }
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
