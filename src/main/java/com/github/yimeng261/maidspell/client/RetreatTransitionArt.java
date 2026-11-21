package com.github.yimeng261.maidspell.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleSupplier;

/** 在 384×216 的逻辑画布上合成可替换的转场图层。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RetreatTransitionArt {
    static final int WIDTH = 384;
    static final int HEIGHT = 216;
    private static final double TAU = 2 * Math.PI;
    private static final ResourceLocation FOG = texture("fog");
    private static final ResourceLocation LIGHT_RAYS = texture("light_rays");
    private static final ResourceLocation MIST_SPECKS = texture("mist_specks");
    private static final ResourceLocation REVEAL_MASK = texture("reveal_mask");
    private static final ResourceLocation BRANCH = texture("branch");
    private static final ResourceLocation EMBLEM = texture("blossom_emblem");
    private static final ResourceLocation[][] SPRITES = {
            {texture("petal_far"), texture("blossom_far")},
            {texture("petal_middle"), texture("blossom_middle")},
            {texture("petal_near"), texture("blossom_near")}
    };
    private static final List<Particle> PARTICLES = makeParticles();
    private static ShaderInstance fogShader;

    private RetreatTransitionArt() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "retreat_fog"), DefaultVertexFormat.POSITION_TEX),
                shader -> fogShader = shader);
    }

    static void draw(GuiGraphics graphics, double time) {
        drawFog(graphics, time);
        float branchOpacity = (float) (.23 * smooth(.94, 1.86, time)
                * (1 - .6 * smooth(1.92, 2.27, time)) * (1 - smooth(2.62, 3.04, time)));
        if (branchOpacity > 0) {
            var pose = graphics.pose();
            pose.pushPose();
            // 枝影图按 4:1 保持比例，在两个角上各裁掉一点
            drawSprite(graphics, BRANCH, -10, -3, 131, 33, branchOpacity);
            pose.translate(WIDTH, HEIGHT, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            drawSprite(graphics, BRANCH, -27, -7, 144, 36, branchOpacity);
            pose.popPose();
        }
        float emblemOpacity = (float) (.21 * smooth(2.22, 2.42, time) * (1 - smooth(2.43, 2.62, time)));
        if (emblemOpacity > 0) {
            int size = 127;
            drawSprite(graphics, EMBLEM, (WIDTH - size) / 2, (HEIGHT - size) / 2, size, size, emblemOpacity);
        }
        drawParticles(graphics, time);
    }

    private static void drawFog(GuiGraphics graphics, double time) {
        double thinMist = .072 * smooth(0, .8, time);
        float fog = (float) (mix(thinMist, 1, smooth(1.12, 2.2, time)) * (1 - smooth(2.91, 3.5, time)));
        if (fogShader == null) {
            graphics.fill(0, 0, WIDTH, HEIGHT, (Math.round(fog * 255) << 24) | 0xecc7db);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            return;
        }
        float light = (float) (.73 * smooth(.69, 1.96, time) * (1 - smooth(2.58, 3.36, time)));
        float specks = (float) (.54 * smooth(.93, 2.12, time) * (1 - smooth(2.66, 3.36, time)));
        // 前段按原定速度揭幕，收尾时把揭幕图里偏亮的像素也一并清掉
        double progress = smooth(2.7, 3.48, time);
        float reveal = (float) (progress * (.82 - .018) / (1.105 - .018)
                + smooth(3.34, 3.5, time) * (1.105 - .82) / (1.105 - .018));
        RenderSystem.setShader(() -> fogShader);
        RenderSystem.setShaderTexture(0, FOG);
        RenderSystem.setShaderTexture(1, LIGHT_RAYS);
        RenderSystem.setShaderTexture(2, MIST_SPECKS);
        RenderSystem.setShaderTexture(3, REVEAL_MASK);
        fogShader.safeGetUniform("LayerOpacity").set(fog, light, specks, 0);
        fogShader.safeGetUniform("Reveal").set(reveal);

        var matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, 0, 0, 0).setUv(0, 0);
        buffer.addVertex(matrix, 0, HEIGHT, 0).setUv(0, 1);
        buffer.addVertex(matrix, WIDTH, HEIGHT, 0).setUv(1, 1);
        buffer.addVertex(matrix, WIDTH, 0, 0).setUv(1, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void drawSprite(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, int width, int height, float opacity) {
        RenderSystem.setShaderColor(1, 1, 1, opacity);
        // 按整张纹理采样，资源包换成别的分辨率也能完整显示
        graphics.blit(texture, x, y, width, height, 0, 0, 1, 1, 1, 1);
    }

    private static void drawParticles(GuiGraphics graphics, double time) {
        double suspended = mix(time, 2.4 + (time - 2.4) * .22,
                smooth(1.94, 2.25, time) * (1 - smooth(2.7, 2.96, time)));
        double rotationStep = TAU / 32;
        var pose = graphics.pose();
        for (Particle p : PARTICLES) {
            if (time < p.start || time >= p.end) {
                continue;
            }
            double age = time - p.start;
            double arrival = clamp(age / (p.gather - p.start));
            double approach = arrival * (1.32 - .32 * arrival);
            double exit = smooth(p.departure, p.end + .035, time);
            double weight = smooth(0, .29, age) * (1 - smooth(p.end - (p.linger ? .12 : .35), p.end, time));
            double sway = Math.sin(suspended * p.flutter + p.phase);
            double roll = Math.cos(suspended * p.flutter * 1.33 + p.phase);
            double lift = p.lift * Math.sin(Math.PI * smooth(.28, .78, arrival));
            double flight = p.reach * (p.linger ? .77 : 1);
            double x = mix(p.x, p.targetX, approach) + p.drift * Math.sin(Math.PI * arrival)
                    + p.sway * sway * smooth(0, .23, age) + Math.cos(p.exitAngle) * exit * flight;
            double y = mix(p.y, p.targetY, approach) - lift + roll * .015 * approach
                    + Math.sin(p.exitAngle) * exit * flight;
            int size = Math.max(2, (int) Math.round(WIDTH * p.size * mix(.85, 1.13, exit) * (p.bloom ? .90 : .69)));
            double squash = p.bloom ? .82 + .18 * roll : .53 + .46 * Math.abs(roll);
            double rimLight = 1 + .10 * smooth(.48, .92, x) * smooth(.6, .08, y);
            float opacity = (float) clamp(p.opacity * weight * rimLight * (p.depth == 2 ? 1.16 : 1));

            pose.pushPose();
            pose.translate(Math.round(x * WIDTH), Math.round(y * HEIGHT), 0);
            double angle = p.angle + suspended * p.spin + sway * .21;
            pose.mulPose(Axis.ZP.rotation((float) (Math.round(angle / rotationStep) * rotationStep)));
            pose.scale((float) squash, 1, 1);
            drawSprite(graphics, SPRITES[p.depth][p.bloom ? 1 : 0], -size / 2, -size / 2, size, size, opacity);
            pose.popPose();
        }
    }

    private record Particle(double x, double y, double targetX, double targetY, double size,
                            int depth, double exitAngle, boolean bloom, double start, double gather,
                            double drift, double sway, double flutter, double phase, double angle,
                            double spin, double lift, double opacity, double departure, double end,
                            double reach, boolean linger) {
    }

    private record Lead(double x, double y, double targetX, double targetY, double size,
                        double start, double exitAngle, double reach) {
    }

    private static List<Particle> makeParticles() {
        DoubleSupplier random = seededRandom();
        Lead[] leads = {
                new Lead(.2, -.026, .35, .39, .052, .03, -2.55, .44),
                new Lead(.68, -.037, .58, .49, .049, .16, .18, .45),
                new Lead(.87, -.052, .70, .51, .044, .36, 1.1, .60)
        };
        List<Particle> particles = new ArrayList<>(159);
        for (int i = 0; i < 159; i++) {
            Lead lead = i < leads.length ? leads[i] : null;
            int depth = lead != null ? 1 : (int) (random.getAsDouble() * 3);
            double edge = random.getAsDouble();
            double size = lead != null ? lead.size : (.018 + Math.pow(random.getAsDouble(), 2) * .076) * (.72 + depth * .24);
            double x = lead != null ? lead.x : edge < .59 ? random.getAsDouble() * 1.05 - .025 : edge < .8 ? -.06 : 1.06;
            double y = lead != null ? lead.y : edge < .59 ? -.055 : random.getAsDouble() * .75 + .015;
            boolean wide = random.getAsDouble() < .40;
            double targetX = lead != null ? lead.targetX : wide ? random.getAsDouble() : .14 + (random.getAsDouble() + random.getAsDouble()) * .36;
            double targetY = lead != null ? lead.targetY : wide ? random.getAsDouble() : .08 + (random.getAsDouble() + random.getAsDouble()) * .4;
            double naturalExit = Math.atan2((targetY - .49) * .85, targetX - .5) + (random.getAsDouble() - .5) * .8;
            double exitAngle = lead != null ? lead.exitAngle : naturalExit;
            boolean bloom = lead != null || random.getAsDouble() < (size > .058 ? .38 : .22);
            double start = lead != null ? lead.start : .79 + Math.pow(random.getAsDouble(), 1.4) * 1.03;
            double gather = lead != null ? 2.33 : 2.05 + random.getAsDouble() * .38;
            double drift = (random.getAsDouble() - .5) * (lead != null ? .07 : .17);
            double sway = random.getAsDouble() * .039 + .012;
            double flutter = random.getAsDouble() * 5 + 3;
            double phase = random.getAsDouble() * TAU;
            double angle = random.getAsDouble() * TAU;
            double spin = (random.getAsDouble() - .5) * 4.2;
            double lift = random.getAsDouble() < .19 ? random.getAsDouble() * .065 + .017 : 0;
            double opacity = lead != null ? .49 : (.51 + random.getAsDouble() * .49) * (.5 + depth * .24);
            double departure = 2.64 + random.getAsDouble() * .19;
            double end = lead != null ? 3.5 - i * .007 : 3.11 + random.getAsDouble() * .37;
            double reach = lead != null ? lead.reach : .88 + random.getAsDouble() * .43;
            boolean linger = lead != null || random.getAsDouble() < .04;
            particles.add(new Particle(x, y, targetX, targetY, size, depth, exitAngle, bloom, start, gather,
                    drift, sway, flutter, phase, angle, spin, lift, opacity, departure, end, reach, linger));
        }
        particles.sort(Comparator.comparingInt(Particle::depth));
        return List.copyOf(particles);
    }

    private static DoubleSupplier seededRandom() {
        // 无符号 32 位线性同余生成器，固定种子让每次转场的花瓣轨迹都一样
        return new DoubleSupplier() {
            private int seed = 5193;

            @Override
            public double getAsDouble() {
                seed = seed * 1664525 + 1013904223;
                return Integer.toUnsignedLong(seed) / 4294967296.0;
            }
        };
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/gui/retreat_transition/" + name + ".png");
    }

    private static double smooth(double start, double end, double value) {
        double t = clamp((value - start) / (end - start));
        return t * t * (3 - 2 * t);
    }

    private static double mix(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
