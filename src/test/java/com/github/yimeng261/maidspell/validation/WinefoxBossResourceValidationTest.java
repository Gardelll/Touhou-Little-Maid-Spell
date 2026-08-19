package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.JsonUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinefoxBossResourceValidationTest {
    private static final Path PROJECT_ROOT = Path.of(System.getProperty(
            "maidspell.projectDir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
    private static final Path ASSETS = PROJECT_ROOT.resolve(
            "src/main/resources/assets/touhou_little_maid_spell");
    private static final Set<String> REQUIRED_CAST_ANIMATIONS = Set.of(
            "iss:instant_projectile",
            "iss:instant_self",
            "iss:instant_slash",
            "iss:katana_upslash",
            "iss:continuous_thrust",
            "iss:continuous_overhead",
            "iss:long_cast",
            "iss:long_cast_finish",
            "iss:charged_throw",
            "iss:charge_wavy",
            "iss:charge_raised_hand",
            "iss:touch_ground",
            "iss:charge_black_hole",
            "iss:charge_arrow",
            "iss:charge_spit",
            "iss:charge_spit_finish",
            "iss:cross_arms",
            "iss:cast_t_pose",
            "iss:stomp",
            "iss:horizontal_slash_one_handed",
            "iss:overhead_two_handed_swing");
    private static final Set<String> ISS_BASE_POSE_BONES = Set.of(
            "MRoot", "Root", "MAllbody", "AllBody", "UpBody", "MTail");

    @Test
    void bossContainsAllRuntimeCastAnimations() throws IOException {
        JsonObject root = parseObject(ASSETS.resolve(
                "animations/magical_winefox_boss.animation.json"));
        Set<String> animations = root.getAsJsonObject("animations").keySet();
        assertTrue(animations.containsAll(REQUIRED_CAST_ANIMATIONS),
                () -> "Missing Winefox cast animations: " + REQUIRED_CAST_ANIMATIONS.stream()
                        .filter(animation -> !animations.contains(animation))
                        .sorted()
                        .toList());
    }

    @Test
    void castAnimationsDoNotOverrideLiveBodyPose() throws IOException {
        JsonObject root = parseObject(ASSETS.resolve(
                "animations/magical_winefox_boss.animation.json"));
        JsonObject animations = root.getAsJsonObject("animations");
        for (String animationName : animations.keySet()) {
            if (!animationName.startsWith("iss:")) {
                continue;
            }
            JsonObject animation = animations.getAsJsonObject(animationName);
            if (animation == null || !animation.has("bones")) {
                continue;
            }
            Set<String> overriddenBones = animation.getAsJsonObject("bones").keySet();
            Set<String> forbidden = ISS_BASE_POSE_BONES.stream()
                    .filter(overriddenBones::contains)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(forbidden.isEmpty(), () -> animationName
                    + " must leave the live body/tail pose to the base controllers: " + forbidden);
        }
    }

    @Test
    void bossAnimationsAreAcceptedByGeckoLib() throws IOException {
        JsonObject root = parseObject(ASSETS.resolve(
                "animations/magical_winefox_boss.animation.json"));
        JsonObject animations = root.getAsJsonObject("animations");
        BakedAnimations bakedAnimations = JsonUtil.GEO_GSON.fromJson(animations, BakedAnimations.class);

        assertEquals(animations.size(), bakedAnimations.animations().size(),
                "GeckoLib rejected one or more Winefox animation tracks");
        assertTrue(bakedAnimations.animations().keySet().containsAll(REQUIRED_CAST_ANIMATIONS),
                "GeckoLib rejected one or more required Winefox cast animations");
    }

    @Test
    void spearProjectileKeepsCompleteModelAndTexture() throws IOException {
        JsonObject root = parseObject(ASSETS.resolve("geo/winefox_spear_projectile.geo.json"));
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        assertEquals(1, geometries.size());
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        assertEquals("geometry.winefox_spear_projectile",
                geometry.getAsJsonObject("description").get("identifier").getAsString());

        JsonArray bones = geometry.getAsJsonArray("bones");
        int cubeCount = 0;
        for (int index = 0; index < bones.size(); index++) {
            JsonArray cubes = bones.get(index).getAsJsonObject().getAsJsonArray("cubes");
            cubeCount += cubes == null ? 0 : cubes.size();
        }
        assertEquals(9, bones.size());
        assertEquals(103, cubeCount);

        BufferedImage texture = ImageIO.read(ASSETS.resolve(
                "textures/entity/winefox_spear_projectile.png").toFile());
        assertNotNull(texture, "Spear texture must be a readable PNG");
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());
    }

    private static JsonObject parseObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
