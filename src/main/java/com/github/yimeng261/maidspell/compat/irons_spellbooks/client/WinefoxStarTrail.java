package com.github.yimeng261.maidspell.compat.irons_spellbooks.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 战斗飞行与瞬移时在世界空间留下的星座印记。 */
public final class WinefoxStarTrail {
    private static final double RANGE = 48.0;
    private static final double SPACING = 1.50;
    private static final double MIN_HORIZONTAL_STEP = 0.05;
    private static final double MAX_STEP = 1.15;
    private static final double DIRECT_JUMP_DISTANCE = 1.50;
    private static final double RADIUS = 0.51;
    private static final int LIFE = 40;
    private static final int MAX_STAMPS = 128;
    private static final int MAX_JUMP_STAMPS = 10;
    private static final int MAX_JUMP_LINES = 32;
    private static final double TAU = 2 * Math.PI;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // 取自 Celestial Atlas 的 catalog.js 投影结果；生成世界坐标顶点时画布 Y 轴取反。
    private static final Constellation[] CONSTELLATIONS = {
        // 仙后座
        new Constellation(new Star[]{
            new Star(-0.95211, -0.30575, -0.01876, 0.44053, 2.27F),
            new Star(-0.59930, 0.30575, 0.00689, 1.94426, 2.23F),
            new Star(-0.08810, -0.12741, 0.03241, 2.72181, 2.47F),
            new Star(0.37811, 0.09675, 0.01933, 4.11891, 2.68F),
            new Star(0.95211, -0.28777, -0.03241, 5.49056, 3.38F)
        }, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}}),
        // 天琴座
        new Constellation(new Star[]{
            new Star(-0.66584, -0.74609, -0.01409, 53.60919, 0.03F),
            new Star(-0.23474, -0.46623, 0.01040, 53.98524, 4.36F),
            new Star(0.29373, -0.33202, 0.01409, 54.45224, 4.30F),
            new Star(0.66584, 0.74609, -0.01374, 54.66535, 3.24F),
            new Star(0.15660, 0.62395, 0.00434, 54.23995, 3.45F)
        }, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 1}}),
        // 北冕座
        new Constellation(new Star[]{
            new Star(-0.55969, -0.77488, -0.01354, 44.77743, 4.14F),
            new Star(-0.96752, -0.25278, -0.00669, 44.53257, 3.68F),
            new Star(-0.70465, 0.44667, 0.01051, 44.86182, 2.23F),
            new Star(-0.25887, 0.64502, 0.01691, 45.24843, 3.84F),
            new Star(0.12979, 0.77488, 0.01136, 45.57728, 4.63F),
            new Star(0.63001, 0.63970, -0.00023, 45.96094, 4.15F),
            new Star(0.96752, -0.10391, -0.01691, 46.14596, 4.99F)
        }, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}}),
        // 南十字座
        new Constellation(new Star[]{
            new Star(-0.22526, 0.97430, -0.01023, 35.83415, 1.33F),
            new Star(0.67276, -0.08255, 0.00932, 36.84791, 1.25F),
            new Star(0.02920, -0.97430, 0.00425, 36.05332, 1.63F),
            new Star(-0.67276, -0.44962, 0.01023, 35.28444, 2.80F)
        }, new int[][]{{0, 2}, {1, 3}}),
        // 三角座
        new Constellation(new Star[]{
            new Star(-0.82664, 0.56274, -0.01600, 5.42753, 3.41F),
            new Star(0.48976, -0.56274, 0.01600, 6.21763, 3.00F),
            new Star(0.82664, -0.15421, 0.01333, 6.59066, 4.01F)
        }, new int[][]{{0, 1}, {1, 2}, {2, 0}}),
        // 天箭座
        new Constellation(new Star[]{
            new Star(-0.84807, 0.32467, 0.00699, 56.64058, 4.37F),
            new Star(-0.72775, 0.52988, 0.00688, 56.68625, 4.37F),
            new Star(-0.17781, 0.01905, 0.01632, 56.99055, 3.82F),
            new Star(0.84807, -0.52988, -0.01632, 57.53620, 3.47F)
        }, new int[][]{{0, 2}, {2, 3}, {1, 2}}),
        // 海豚座
        new Constellation(new Star[]{
            new Star(0.24514, -0.61368, 0.01528, 59.49837, 3.77F),
            new Star(-0.06714, -0.26962, 0.01915, 59.39814, 3.63F),
            new Star(0.78956, -0.47983, 0.00634, 59.83531, 4.27F),
            new Star(0.42872, -0.24690, 0.01767, 59.68172, 4.43F),
            new Star(-0.78956, 0.61368, -0.01915, 59.18998, 4.03F)
        }, new int[][]{{4, 1}, {1, 0}, {0, 2}, {2, 3}, {3, 1}}),
        // 天鹅座
        new Constellation(new Star[]{
            new Star(0.65235, -0.40912, 0.00947, 59.58444, 1.25F),
            new Star(0.30323, -0.18761, 0.05185, 58.66275, 2.20F),
            new Star(0.04418, -0.69118, -0.00338, 56.87471, 2.87F),
            new Star(0.72268, 0.69118, -0.04173, 61.09658, 3.20F),
            new Star(0.43864, 0.32229, 0.03441, 59.81386, 2.46F),
            new Star(-0.15611, -0.02883, 0.04247, 57.41860, 3.89F),
            new Star(-0.72268, 0.20838, -0.05185, 56.19060, 3.08F)
        }, new int[][]{{0, 1}, {1, 5}, {5, 6}, {2, 1}, {1, 4}, {4, 3}})
    };

    private static final Map<MagicalWinefoxBossEntity, Flight> FLIGHTS = new IdentityHashMap<>();
    private static final ArrayDeque<Stamp> STAMPS = new ArrayDeque<>();
    private static final ArrayDeque<JumpLine> JUMP_LINES = new ArrayDeque<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(8192));
    private static ClientLevel level;

    private WinefoxStarTrail() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!trackLevel(mc.level) || mc.isPaused()) return;

        long now = level.getGameTime();
        while (!STAMPS.isEmpty() && now - STAMPS.getFirst().born() >= LIFE) {
            STAMPS.removeFirst();
        }
        while (!JUMP_LINES.isEmpty() && now - JUMP_LINES.getFirst().born() >= LIFE) {
            JUMP_LINES.removeFirst();
        }

        Entity viewer = mc.getCameraEntity();
        if (viewer == null) {
            FLIGHTS.clear();
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().isInitialized()
                ? mc.gameRenderer.getMainCamera().getPosition() : viewer.getEyePosition();
        for (MagicalWinefoxBossEntity boss : level.getEntitiesOfClass(MagicalWinefoxBossEntity.class,
                viewer.getBoundingBox().inflate(RANGE), WinefoxStarTrail::canEmit)) {
            Flight flight = FLIGHTS.computeIfAbsent(boss, ignored -> new Flight());
            flight.seen = now;
            MagicalWinefoxBossEntity.StarTrailJump jump = boss.pollStarTrailJump();
            if (jump != null) {
                flight.leaveJumpTrail(jump.from(), jump.to(), boss.getBbHeight(), eye, now);
            }
            if (boss.isStarTrailInterpolatingJump()) {
                // 显示位置跨过跳变的插值结束后再恢复采样。
                flight.resetPath();
            } else {
                flight.advance(boss.position(), boss.getBbHeight(), eye, now);
            }
        }
        FLIGHTS.values().removeIf(flight -> flight.seen != now);
    }

    private static boolean trackLevel(ClientLevel current) {
        if (current != level) {
            FLIGHTS.clear();
            STAMPS.clear();
            JUMP_LINES.clear();
            level = current;
        }
        return level != null;
    }

    private static boolean canEmit(MagicalWinefoxBossEntity boss) {
        return boss.isAlive() && boss.isBattleActive() && !boss.isTransitioning() && !boss.isInvisible();
    }

    private static void stamp(Constellation constellation, Vec3 center, Vec3 eye, double time, long born) {
        Vec3 normal = eye.subtract(center).normalize();
        Vec3 right = UP.cross(normal).normalize();
        if (right.lengthSqr() < 0.5) right = new Vec3(1, 0, 0);
        if (normal.lengthSqr() < 0.5) normal = new Vec3(0, 0, 1);
        Vec3 up = normal.cross(right);

        double angle = Math.sin(time * TAU / 22.0) * 0.094;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double local = RADIUS * 0.032;
        Star[] stars = constellation.stars();
        Vec3[] points = new Vec3[stars.length];
        for (int i = 0; i < stars.length; i++) {
            Star star = stars[i];
            double period = 3.55 + (i % 3) * 0.67;
            double x = star.x() * RADIUS + local * wobble(time, period, star.phase()) * 0.72;
            double y = star.y() * RADIUS + local * wobble(time, period * 1.23, star.phase() + 2.1) * 0.82;
            double z = star.z() * RADIUS + local * wobble(time, period * 1.43, star.phase() + 4.3) * 1.65;
            points[i] = center.add(right.scale(x * cos - y * sin))
                    .subtract(up.scale(x * sin + y * cos)).add(normal.scale(z));
        }

        if (STAMPS.size() >= MAX_STAMPS) STAMPS.removeFirst();
        STAMPS.addLast(new Stamp(center, new AABB(center, center).inflate(0.75), constellation, points, born));
    }

    private static double wobble(double time, double period, double phase) {
        double wave = time * TAU / period;
        return 0.68 * Math.sin(wave) + 0.32 * (Math.sin(wave * 0.62 + phase) - Math.sin(phase));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !trackLevel(Minecraft.getInstance().level) || (STAMPS.isEmpty() && JUMP_LINES.isEmpty())) return;

        Matrix4f pose = event.getPoseStack().last().pose();
        Vec3 camera = event.getCamera().getPosition();
        Vec3 right = new Vec3(event.getCamera().getLeftVector()).scale(-1);
        Vec3 up = new Vec3(event.getCamera().getUpVector());
        VertexConsumer vertices = BUFFERS.getBuffer(TrailRenderType.STAR_TRAIL);
        double now = level.getGameTime() + (double) event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (JumpLine line : JUMP_LINES) {
            if (line.bounds().distanceToSqr(camera) > RANGE * RANGE
                    || !event.getFrustum().isVisible(line.bounds())) continue;
            float remaining = (float) Mth.clamp(1 - (now - line.born()) / LIFE, 0, 1);
            float alpha = 0.44F * remaining * remaining;
            if (alpha > 0.01F) {
                drawLine(vertices, pose, line.from().subtract(camera), line.to().subtract(camera),
                        0.023, 0.55F, 0.83F, 1.00F, alpha);
            }
        }
        for (Stamp stamp : STAMPS) {
            if (stamp.center().distanceToSqr(camera) > RANGE * RANGE
                    || !event.getFrustum().isVisible(stamp.bounds())) continue;
            float age = (float) Mth.clamp((now - stamp.born()) / LIFE, 0, 1);
            drawStamp(vertices, pose, stamp, camera, right, up, age);
        }
        BUFFERS.endBatch(TrailRenderType.STAR_TRAIL);
    }

    private static void drawStamp(VertexConsumer vertices, Matrix4f pose, Stamp stamp, Vec3 camera,
                                  Vec3 right, Vec3 up, float age) {
        Vec3[] points = stamp.points();
        Constellation constellation = stamp.constellation();
        float warmth = Mth.clamp((age - 0.40F) / 0.45F, 0, 1);
        float red = Mth.lerp(warmth, 0.55F, 1.00F);
        float green = Mth.lerp(warmth, 0.83F, 0.74F);
        float blue = Mth.lerp(warmth, 1.00F, 0.40F);
        float lineLife = Math.max(0, 1 - age / 0.70F);
        float lineAlpha = 0.62F * lineLife * lineLife;
        if (lineAlpha > 0.01F) {
            for (int[] edge : constellation.edges()) {
                Vec3 from = points[edge[0]].subtract(camera);
                Vec3 to = points[edge[1]].subtract(camera);
                drawLine(vertices, pose, from, to, 0.0075, red, green, blue, lineAlpha);
            }
        }

        Star[] stars = constellation.stars();
        for (int i = 0; i < points.length; i++) {
            float brightness = Mth.clamp((5.25F - stars[i].magnitude()) / 3.25F, 0.28F, 1);
            float starLife = Math.max(0, 1 - age / (0.76F + 0.24F * brightness));
            float alpha = (0.58F + 0.42F * brightness) * starLife * starLife;
            if (alpha < 0.01F) continue;
            Vec3 center = points[i].subtract(camera);
            double size = 0.065 + 0.065 * brightness;
            Vec3 side = right.scale(size);
            Vec3 vertical = up.scale(size);
            vertex(vertices, pose, center.subtract(side).add(vertical), 0.125F, 0.125F, red, green, blue, alpha);
            vertex(vertices, pose, center.subtract(side).subtract(vertical), 0.125F, 0.875F, red, green, blue, alpha);
            vertex(vertices, pose, center.add(side).subtract(vertical), 0.875F, 0.875F, red, green, blue, alpha);
            vertex(vertices, pose, center.add(side).add(vertical), 0.875F, 0.125F, red, green, blue, alpha);
        }
    }

    private static void drawLine(VertexConsumer vertices, Matrix4f pose, Vec3 from, Vec3 to, double halfWidth,
                                 float red, float green, float blue, float alpha) {
        Vec3 sideways = to.subtract(from).cross(from.add(to)).normalize().scale(halfWidth);
        if (sideways.lengthSqr() < 0.000001) return;
        // 采样星点贴图里的不透明色块，使连线与星点合批绘制。
        float uv = 2.5F / 64;
        vertex(vertices, pose, from.subtract(sideways), uv, uv, red, green, blue, alpha);
        vertex(vertices, pose, from.add(sideways), uv, uv, red, green, blue, alpha);
        vertex(vertices, pose, to.add(sideways), uv, uv, red, green, blue, alpha);
        vertex(vertices, pose, to.subtract(sideways), uv, uv, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, Vec3 point, float u, float v,
                               float red, float green, float blue, float alpha) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setUv(u, v).setColor(red, green, blue, alpha);
    }

    private record Star(double x, double y, double z, double phase, float magnitude) {
    }

    private record Constellation(Star[] stars, int[][] edges) {
    }

    private record Stamp(Vec3 center, AABB bounds, Constellation constellation, Vec3[] points, long born) {
    }

    private record JumpLine(Vec3 from, Vec3 to, AABB bounds, long born) {
    }

    private static final class Flight {
        private Vec3 previous;
        private long seen;
        private long lastMovingTick = Long.MIN_VALUE;
        private double timeOffset;
        private double remaining;
        private int lastConstellation = -1;

        private void resetPath() {
            previous = null;
            lastMovingTick = Long.MIN_VALUE;
        }

        private void leaveJumpTrail(Vec3 start, Vec3 end, float height, Vec3 eye, long tick) {
            Vec3 from = start.add(0, height * 0.53, 0);
            Vec3 to = end.add(0, height * 0.53, 0);
            double length = from.distanceTo(to);
            if (length < MIN_HORIZONTAL_STEP) return;

            if (JUMP_LINES.size() >= MAX_JUMP_LINES) JUMP_LINES.removeFirst();
            JUMP_LINES.addLast(new JumpLine(from, to, new AABB(from, to).inflate(0.03), tick));

            int intervals = Math.min(MAX_JUMP_STAMPS - 1, Mth.ceil(length / SPACING));
            double time = tick / 20.0 + ThreadLocalRandom.current().nextDouble(90);
            for (int i = 0; i <= intervals; i++) {
                double fraction = (double) i / intervals;
                stamp(nextConstellation(), from.lerp(to, fraction), eye, time + fraction, tick);
            }
        }

        private void advance(Vec3 position, float height, Vec3 eye, long tick) {
            Vec3 from = previous;
            previous = position;
            if (from == null) return;

            Vec3 movement = position.subtract(from);
            double horizontal = movement.horizontalDistance();
            double distance = movement.length();
            if (distance > MAX_STEP) {
                if (distance > DIRECT_JUMP_DISTANCE) {
                    leaveJumpTrail(from, position, height, eye, tick);
                }
                lastMovingTick = Long.MIN_VALUE;
                return;
            }
            if (horizontal < MIN_HORIZONTAL_STEP) return;

            if (lastMovingTick == Long.MIN_VALUE || tick - lastMovingTick > 3) {
                remaining = 0;
                timeOffset = ThreadLocalRandom.current().nextDouble(90);
            }
            lastMovingTick = tick;

            Vec3 offset = new Vec3(-movement.x / horizontal * 0.30, height * 0.53, -movement.z / horizontal * 0.30);
            while (remaining <= distance) {
                double fraction = remaining / distance;
                Vec3 center = from.lerp(position, fraction).add(offset);
                stamp(nextConstellation(), center, eye, (tick - 1 + fraction) / 20.0 + timeOffset, tick);
                remaining += SPACING;
            }
            remaining -= distance;
        }

        private Constellation nextConstellation() {
            // 在其余星座中均匀抽取，保证相邻两个印记形状不同。
            int count = CONSTELLATIONS.length;
            int index = ThreadLocalRandom.current().nextInt(lastConstellation < 0 ? count : count - 1);
            if (lastConstellation >= 0 && index >= lastConstellation) index++;
            lastConstellation = index;
            return CONSTELLATIONS[index];
        }
    }

    private static final class TrailRenderType extends RenderType {
        private static final RenderType STAR_TRAIL = create("maidspell_winefox_star_trail",
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID,
                                "textures/particle/winefox_trail_star.png"), true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private TrailRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
