package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity.FragrantIngenuityBauble;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 精灵守卫索敌与同盟、铁魔法雷暴按施法者归属保护友方女仆、随机增益效果黑名单默认含 goety:explosive 且旧默认值会迁移补上。
 */
@DisplayName("精灵守卫、雷暴归属保护与随机增益黑名单")
@Scene("tlms")
class GuardTest {
    private static final String TEMPLAR = NS + "elf_templar";
    private static final String LIGHTNING = "irons_spellbooks:lightning_strike";
    /** 精灵守卫找目标的目标选择器每 10 tick 左右试一次，给足余量。 */
    private static final int ACQUIRE = 100;

    @DisplayName("精灵守卫主动攻击僵尸")
    @Test
    void targetsZombie(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        targets(Stage.flat(game, plot, reporter), "minecraft:zombie", true);
    }

    @DisplayName("精灵守卫主动攻击骷髅")
    @Test
    void targetsSkeleton(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        targets(Stage.flat(game, plot, reporter), "minecraft:skeleton", true);
    }

    @DisplayName("精灵守卫主动攻击掠夺者")
    @Test
    void targetsPillager(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        targets(Stage.flat(game, plot, reporter), "minecraft:pillager", true);
    }

    @DisplayName("精灵守卫主动攻击劫掠兽")
    @Test
    void targetsRavager(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        targets(Stage.flat(game, plot, reporter), "minecraft:ravager", true);
    }

    @DisplayName("精灵守卫不把苦力怕当目标")
    @Test
    void ignoresCreeper(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        targets(Stage.flat(game, plot, reporter), "minecraft:creeper", false);
    }

    @DisplayName("精灵守卫不把蜘蛛当目标")
    @Test
    void ignoresSpider(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        targets(Stage.flat(game, plot, reporter), "minecraft:spider", false);
    }

    @DisplayName("精灵守卫不以没惹她的玩家和女仆为目标")
    @Test
    void ignoresPeacefulPlayerAndMaid(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        record Cast(ServerPlayer player, EntityMaid maid, Mob templar) {
        }
        Cast cast = ctx.get(() -> {
            ServerPlayer player = Owners.visitor(ctx, "TlmsPasserby", 4, 0, 0);
            EntityMaid maid = Owners.maid(ctx, player, -4, 0, 0);
            return new Cast(player, maid, templar(ctx, 0, 0));
        });
        List<LivingEntity> seen = new ArrayList<>();
        ctx.watch(ACQUIRE, () -> {
            if (cast.templar().getTarget() != null && !seen.contains(cast.templar().getTarget())) {
                seen.add(cast.templar().getTarget());
            }
        });
        ctx.check(seen.contains(cast.player())).as("精灵守卫以没惹她的玩家为目标").isFalse();
        ctx.check(seen.contains(cast.maid())).as("精灵守卫以女仆为目标").isFalse();
    }

    /** 玩家打其中一个，周围的同伴都把这名玩家当目标。 */
    @DisplayName("玩家打其中一个精灵守卫，周围同伴都把这名玩家当目标")
    @Test
    void companionsRetaliate(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer attacker = ctx.get(() -> Owners.visitor(ctx, "TlmsAttacker", 6, 0, 0));
        Mob hit = ctx.get(() -> templar(ctx, 0, 0));
        List<Mob> companions = ctx.get(() -> List.of(templar(ctx, 0, 3), templar(ctx, 0, -3)));
        ctx.run(() -> {
            attacker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            attacker.setHealth(1000);
        });
        ctx.await(20, "三个精灵守卫都已进入世界", () -> hit.tickCount >= 2 && companions.stream().allMatch(c -> c.tickCount >= 2));
        ctx.run(() -> {
            ctx.record("hitTick", hit.tickCount);
            ctx.check(hit.hurt(hit.damageSources().playerAttack(attacker), 1)).as("攻击命中精灵守卫").isTrue();
        });
        AtomicReference<Map<String, Object>> last = new AtomicReference<>(Map.of());
        try {
            ctx.await(ACQUIRE, "被打的精灵守卫和两名同伴都追击攻击者", () -> {
                last.set(Map.of("hitTarget", String.valueOf(hit.getTarget()),
                        "companionTargets", companions.stream().map(c -> String.valueOf(c.getTarget())).toList(),
                        "attackerAlive", attacker.isAlive(), "hurtTimestamp", hit.getLastHurtByMobTimestamp()));
                return hit.getTarget() == attacker && companions.stream().allMatch(c -> c.getTarget() == attacker);
            });
        } finally {
            ctx.record("retaliation", last.get());
        }
    }

    /** 一个精灵守卫的法术误伤另一个：两边都不把对方当目标。 */
    @DisplayName("精灵守卫的法术误伤同伴时，双方都不把对方当目标")
    @Test
    void noInfighting(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Mob a = ctx.get(() -> templar(ctx, 0, 0));
        Mob b = ctx.get(() -> templar(ctx, 0, 3));
        ctx.run(() -> {
            ctx.check(a.isAlliedTo(b)).as("精灵守卫之间互为同盟").isTrue();
            a.hurt(a.damageSources().indirectMagic(b, b), 1);
            b.hurt(b.damageSources().mobAttack(a), 1);
        });
        ctx.ticks(40);
        ctx.run(() -> {
            ctx.check(a.getTarget() == b).as("被误伤的精灵守卫以同伴为目标").isFalse();
            ctx.check(b.getTarget() == a).as("被误伤的精灵守卫以同伴为目标").isFalse();
        });
    }

    @DisplayName("铁魔法雷暴：主人施放时不劈自己的女仆，敌对生物照常被劈")
    @Test
    void thunderstormOwnerSparesOwnMaid(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> thunderstorm(ctx, true));
    }

    @DisplayName("铁魔法雷暴：敌人施放时照常劈女仆和敌对生物")
    @Test
    void thunderstormEnemyHitsMaid(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> thunderstorm(ctx, false));
    }

    @DisplayName("铁魔法雷暴：敌人施放时劈其他玩家的宠物、敌对骑手和盯着玩家的北极熊，放过无主奶牛")
    @Test
    void thunderstormEnemyPetAndRiderHit(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> thunderstormEnemySide(ctx));
    }

    @DisplayName("铁魔法雷暴：敌人施放时放过村民、铁傀儡、奶牛和没盯着谁的北极熊")
    @Test
    void thunderstormNeutralsSparedByEnemyCaster(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> thunderstormNeutrals(ctx));
    }

    @DisplayName("铁魔法雷暴：主人施放时，主人宠物载着的敌对乘客整条链都不被劈")
    @Test
    void thunderstormOwnerSideVehicleCarriesPassenger(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> thunderstormOwnerSideVehicle(ctx));
    }

    @DisplayName("梦云水晶与馥郁巧思的默认随机增益黑名单含 goety:explosive，候选效果里没有它")
    @Test
    void explosiveInEffectBlacklists(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> explosiveBlacklisted(ctx));
    }

    @DisplayName("随机增益黑名单迁移：两份各自判断，恰为旧默认才补上 goety:explosive，版本已到 1 不动")
    @Test
    void effectBlacklistMigrationCoversBoth(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> blacklistMigration(ctx));
    }

    private static Mob templar(Stage ctx, int dx, int dz) {
        Mob templar = Actors.spawn(ctx, TEMPLAR, dx, 0, dz, false);
        templar.setPersistenceRequired();
        return templar;
    }

    /** 精灵守卫旁边放一只（不动的）生物，看她会不会主动把它当目标。 */
    private static void targets(Stage ctx, String type, boolean expected) {
        record Pair(Mob templar, Mob other) {
        }
        Pair pair = ctx.get(() -> new Pair(templar(ctx, 0, 0), Actors.spawn(ctx, type, 4, 0, 0, true)));
        if (expected) {
            ctx.await(ACQUIRE, "精灵守卫主动攻击 " + type, () -> pair.templar().getTarget() == pair.other());
            ctx.record("note", "精灵守卫主动攻击 " + type);
        } else {
            boolean[] targeted = {false};
            ctx.watch(ACQUIRE, () -> targeted[0] |= pair.templar().getTarget() == pair.other());
            ctx.check(targeted[0]).as("精灵守卫以 " + type + " 为目标").isFalse();
        }
    }

    /**
     * 让施法者身上的雷暴效果结算一次：范围内每个可命中的生物脚下落一道雷。
     * 施法者是女仆主人时不劈女仆，敌人施法时照常劈女仆；旁边的敌对生物作为落雷对照。
     */
    private static void thunderstorm(Stage ctx, boolean ownerCasts) {
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("irons_spellbooks:thunderstorm"));
        if (effect.isEmpty()) {
            throw ctx.fail("irons_spellbooks:thunderstorm 未注册");
        }
        ServerPlayer owner = Owners.visitor(ctx, "TlmsStormOwner", 0, 0, 0);
        LivingEntity caster = ownerCasts ? owner : Actors.spawn(ctx, "minecraft:zombie", 0, 0, -2, true);
        EntityMaid maid = Owners.maid(ctx, owner, 5, 0, 0);
        LivingEntity pig = Actors.spawn(ctx, "minecraft:husk", -5, 0, 0, true);
        effect.get().value().applyEffectTick(caster, 7);
        List<Entity> strikes = ctx.level().getEntities((Entity) null, new AABB(ctx.rel(0, 0, 0)).inflate(24),
                entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(LIGHTNING));
        strikes.forEach(Entity::discard);
        String who = ownerCasts ? "主人" : "敌人";
        ctx.check(strikes.stream().anyMatch(strike -> strike.distanceTo(maid) < 0.5)).as(who + "的雷暴在女仆脚下落雷").isEqualTo(!ownerCasts);
        ctx.check(strikes.stream().anyMatch(strike -> strike.distanceTo(pig) < 0.5)).as(who + "的雷暴在敌对生物脚下落雷").isTrue();
    }

    /**
     * 敌人施放雷暴时，其他玩家的宠物、敌对骑手（鸡骑士）和盯着玩家的北极熊照常落雷，无主的奶牛仍被放过。
     */
    private static void thunderstormEnemySide(Stage ctx) {
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("irons_spellbooks:thunderstorm"));
        if (effect.isEmpty()) {
            throw ctx.fail("irons_spellbooks:thunderstorm 未注册");
        }
        ServerPlayer owner = Owners.visitor(ctx, "TlmsStormOwner", 0, 0, 0);
        LivingEntity caster = Actors.spawn(ctx, "minecraft:zombie", 0, 0, -2, true);
        LivingEntity wolf = Owners.pet(ctx, "minecraft:wolf", owner, 5, 0, 0);
        LivingEntity chicken = Actors.spawn(ctx, "minecraft:chicken", -5, 0, 0, true);
        LivingEntity rider = Actors.spawn(ctx, "minecraft:husk", -5, 0, 0, true);
        rider.startRiding(chicken, true);
        LivingEntity cow = Actors.spawn(ctx, "minecraft:cow", 0, 0, 5, true);
        net.minecraft.world.entity.Mob bear = Actors.spawn(ctx, "minecraft:polar_bear", 0, 0, 8, true);
        bear.setTarget(owner);
        effect.get().value().applyEffectTick(caster, 7);
        List<Entity> strikes = ctx.level().getEntities((Entity) null, new AABB(ctx.rel(0, 0, 0)).inflate(24),
                entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(LIGHTNING));
        strikes.forEach(Entity::discard);
        ctx.check(strikes.stream().anyMatch(strike -> strike.distanceTo(wolf) < 0.5)).as("敌人的雷暴在其他玩家的宠物脚下落雷").isTrue();
        ctx.check(strikes.stream().anyMatch(strike -> strike.distanceTo(rider) < 0.5)).as("敌人的雷暴在鸡骑士的敌对骑手脚下落雷").isTrue();
        ctx.check(strikes.stream().anyMatch(strike -> strike.distanceTo(bear) < 0.5)).as("盯着玩家的北极熊照常落雷").isTrue();
        ctx.check(strikes.stream().anyMatch(strike -> strike.distanceTo(cow) < 0.5)).as("无主奶牛不被雷劈").isFalse();
    }

    /** 让 caster 身上的雷暴结算一次，返回范围内每道落雷的位置所对应的实体是否被劈。 */
    private static List<Entity> strikeOnce(Stage ctx, LivingEntity caster, MobEffect effect) {
        effect.applyEffectTick(caster, 7);
        List<Entity> strikes = ctx.level().getEntities((Entity) null, new AABB(ctx.rel(0, 0, 0)).inflate(24),
                entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(LIGHTNING));
        strikes.forEach(Entity::discard);
        return strikes;
    }

    private static boolean struck(List<Entity> strikes, Entity target) {
        return strikes.stream().anyMatch(strike -> strike.distanceTo(target) < 0.5);
    }

    /** 敌人施放的雷暴照样放过无主的中立生物：村民、铁傀儡、奶牛、没有盯着谁的北极熊；僵尸作为落雷对照。 */
    private static void thunderstormNeutrals(Stage ctx) {
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("irons_spellbooks:thunderstorm"));
        if (effect.isEmpty()) {
            throw ctx.fail("irons_spellbooks:thunderstorm 未注册");
        }
        LivingEntity caster = Actors.spawn(ctx, "minecraft:zombie", 0, 0, -2, true);
        LivingEntity villager = Actors.spawn(ctx, "minecraft:villager", 5, 0, 0, true);
        LivingEntity golem = Actors.spawn(ctx, "minecraft:iron_golem", -5, 0, 0, true);
        LivingEntity cow = Actors.spawn(ctx, "minecraft:cow", 0, 0, 5, true);
        LivingEntity calmBear = Actors.spawn(ctx, "minecraft:polar_bear", 0, 0, 8, true);
        LivingEntity skeleton = Actors.spawn(ctx, "minecraft:skeleton", 8, 0, 0, true);
        List<Entity> strikes = strikeOnce(ctx, caster, effect.get().value());
        ctx.check(struck(strikes, villager)).as("村民被劈").isFalse();
        ctx.check(struck(strikes, golem)).as("铁傀儡被劈").isFalse();
        ctx.check(struck(strikes, cow)).as("奶牛被劈").isFalse();
        ctx.check(struck(strikes, calmBear)).as("没有盯着谁的北极熊被劈").isFalse();
        ctx.check(struck(strikes, skeleton)).as("骷髅被劈").isTrue();
    }

    /** 主人施放时，主人驯服的宠物载着敌对乘客：整条乘客链不被劈；无关的敌对生物仍被劈。 */
    private static void thunderstormOwnerSideVehicle(Stage ctx) {
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("irons_spellbooks:thunderstorm"));
        if (effect.isEmpty()) {
            throw ctx.fail("irons_spellbooks:thunderstorm 未注册");
        }
        ServerPlayer owner = Owners.visitor(ctx, "TlmsStormOwner", 0, 0, 0);
        LivingEntity wolf = Owners.pet(ctx, "minecraft:wolf", owner, 5, 0, 0);
        LivingEntity rider = Actors.spawn(ctx, "minecraft:husk", 5, 0, 0, true);
        rider.startRiding(wolf, true);
        LivingEntity bystander = Actors.spawn(ctx, "minecraft:husk", -5, 0, 0, true);
        List<Entity> strikes = strikeOnce(ctx, owner, effect.get().value());
        ctx.check(struck(strikes, wolf)).as("主人的宠物被劈").isFalse();
        ctx.check(struck(strikes, rider)).as("骑在主人宠物上的敌对乘客被劈").isFalse();
        ctx.check(struck(strikes, bystander)).as("无关的敌对生物被劈").isTrue();
    }

    /** 新生成的配置里，梦云水晶与馥郁巧思的随机增益黑名单都含 goety:explosive，候选效果里也就没有它。 */
    @SuppressWarnings("unchecked")
    private static void explosiveBlacklisted(Stage ctx) {
        String explosive = "goety:explosive";
        net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> crystalSpec =
                Reflect.field(null, baublesConfig(), "DREAM_CRYSTAL_EFFECT_BLACKLIST");
        net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> fragrantSpec =
                Reflect.field(null, baublesConfig(), "FRAGRANT_INGENUITY_EFFECT_BLACKLIST");
        var crystalDefaults = (List<String>) crystalSpec.getDefault();
        var fragrantDefaults = (List<String>) fragrantSpec.getDefault();
        ctx.check(crystalDefaults.contains(explosive)).as("梦云水晶默认黑名单含 " + explosive).isTrue();
        ctx.check(fragrantDefaults.contains(explosive)).as("馥郁巧思默认黑名单含 " + explosive).isTrue();
        var oldCrystal = Config.dreamCrystalEffectBlacklist;
        var oldFragrant = Config.fragrantIngenuityEffectBlacklist;
        try {
            Config.dreamCrystalEffectBlacklist = new ArrayList<>(crystalDefaults);
            Config.fragrantIngenuityEffectBlacklist = new ArrayList<>(fragrantDefaults);
            FragrantIngenuityBauble.refreshEffectsList();
            DreamCatCrystalBauble.invalidateBeneficialEffectsCache();
            List<Holder.Reference<MobEffect>> crystal = Reflect.call(null, DreamCatCrystalBauble.class, "getBeneficialEffects", new Class<?>[0]);
            var fragrant = FragrantIngenuityBauble.POSITIVE_EFFECTS;
            ctx.record("candidates", Map.of("dreamCrystal", crystal.size(), "fragrantIngenuity", fragrant.size()));
            ctx.check(crystal.stream().anyMatch(h -> h.is(ResourceLocation.parse(explosive)))).as("梦云水晶候选效果含 " + explosive).isFalse();
            ctx.check(fragrant.stream().anyMatch(h -> h.is(ResourceLocation.parse(explosive)))).as("馥郁巧思候选效果含 " + explosive).isFalse();
        } finally {
            Config.dreamCrystalEffectBlacklist = oldCrystal;
            Config.fragrantIngenuityEffectBlacklist = oldFragrant;
            FragrantIngenuityBauble.refreshEffectsList();
            DreamCatCrystalBauble.invalidateBeneficialEffectsCache();
        }
    }

    /**
     * 迁移版本为 0 时，梦云水晶与馥郁巧思两份黑名单各自判断：恰为旧默认（默认值去掉 goety:explosive）才补上，
     * 改过的保持原样；版本已到 1 时什么都不动。结束后把三项写回原值并落盘。
     */
    @SuppressWarnings("unchecked")
    private static void blacklistMigration(Stage ctx) {
        String explosive = "goety:explosive";
        var crystal = (ModConfigSpec.ConfigValue<List<? extends String>>) Reflect.<Object>field(null, baublesConfig(), "DREAM_CRYSTAL_EFFECT_BLACKLIST");
        var fragrant = (ModConfigSpec.ConfigValue<List<? extends String>>) Reflect.<Object>field(null, baublesConfig(), "FRAGRANT_INGENUITY_EFFECT_BLACKLIST");
        ModConfigSpec.IntValue version = Reflect.field(null, baublesConfig(), "DREAM_CRYSTAL_EFFECT_BLACKLIST_MIGRATION_VERSION");
        List<String> defaults = (List<String>) crystal.getDefault();
        List<String> previousDefaults = defaults.stream().filter(effect -> !effect.equals(explosive)).toList();
        List<String> custom = List.of("minecraft:speed");
        List<? extends String> originalCrystal = crystal.get();
        List<? extends String> originalFragrant = fragrant.get();
        int originalVersion = version.get();
        try {
            migrate(crystal, previousDefaults, fragrant, previousDefaults, version, 0);
            ctx.check(crystal.get()).as("两份都是旧默认：梦云水晶黑名单").isEqualTo(defaults);
            ctx.check(fragrant.get()).as("两份都是旧默认：馥郁巧思黑名单").isEqualTo(defaults);
            ctx.check(version.get()).as("迁移后版本号").isEqualTo(1);

            migrate(crystal, custom, fragrant, previousDefaults, version, 0);
            ctx.check(crystal.get()).as("只有馥郁巧思是旧默认：梦云水晶黑名单").isEqualTo(custom);
            ctx.check(fragrant.get()).as("只有馥郁巧思是旧默认：馥郁巧思黑名单").isEqualTo(defaults);

            migrate(crystal, previousDefaults, fragrant, previousDefaults, version, 1);
            ctx.check(crystal.get()).as("版本已到 1：梦云水晶黑名单").isEqualTo(previousDefaults);
            ctx.check(fragrant.get()).as("版本已到 1：馥郁巧思黑名单").isEqualTo(previousDefaults);
        } finally {
            crystal.set(originalCrystal);
            fragrant.set(originalFragrant);
            version.set(originalVersion);
            Config.BAUBLES_SPEC.save();
        }
    }

    /** 饰品配置类是包私有的，按名字取。 */
    private static Class<?> baublesConfig() {
        try {
            return Class.forName(Config.class.getPackageName() + ".ConfigBaubles");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("找不到饰品配置类", e);
        }
    }

    private static void migrate(ModConfigSpec.ConfigValue<List<? extends String>> crystal, List<String> crystalValue,
                                ModConfigSpec.ConfigValue<List<? extends String>> fragrant, List<String> fragrantValue,
                                ModConfigSpec.IntValue version, int versionValue) {
        crystal.set(crystalValue);
        fragrant.set(fragrantValue);
        version.set(versionValue);
        Reflect.call(null, baublesConfig(), "migrateRandomEffectBlacklists", new Class<?>[0]);
    }
}
