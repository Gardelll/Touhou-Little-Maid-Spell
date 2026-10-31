package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Hits;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 盟友判定与友伤（集成服，宿主玩家为主人 P，模拟玩家 Q 为另一名玩家）：
 * 女仆、宠物、各模组召唤物和法术实体按主人链判定互不伤害、互不索敌；别人的一方照常能打；
 * 记分板队伍与友伤开关；主人的间接伤害不打自己一方，近战交给车万女仆原有规则。
 */
@DisplayName("盟友判定与友伤")
@Scene("tlms.integrated")
@ResourceLock(RetreatFixtures.HOST_PLAYER)
class AllyTest {
    private static final String ISS_SUMMON = "irons_spellbooks:summoned_zombie";
    private static final String GOETY_SERVANT = "goety:zombie_servant";
    private static final String ARS_SUMMON = "ars_nouveau:summon_wolf";
    /** 既不是弹射物也不是召唤物的法术实体（含原版可追溯主人的实体）。 */
    private static final List<String> SPELL_ENTITIES = List.of(
            "irons_spellbooks:sculk_tentacle", "irons_spellbooks:root", "irons_spellbooks:wall_of_fire", "irons_spellbooks:ice_tomb",
            "goety:fang", "goety:corrupted_beam", "goety:lightning_trap", "goety:spike", "goety:brew_effect_cloud", "goety:brew_effect_gas",
            "minecraft:area_effect_cloud", "minecraft:evoker_fangs", "minecraft:tnt");

    /** P 的女仆施法（法术伤害、弹射物）打不到 P 这一方。 */
    @DisplayName("女仆的法术伤害和弹射物打不到主人一方")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void maidSparesOwnerSide(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> maidSparesOwnerSide(ctx, p));
    }

    /** P 的女仆不会以 P 这一方为目标。 */
    @DisplayName("女仆不会以主人一方为目标")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void maidDoesNotTargetOwnerSide(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> maidDoesNotTargetOwnerSide(ctx, p));
    }

    /** P 的召唤物、仆从、宠物打不到也不以 P 的女仆为目标。 */
    @DisplayName("主人的召唤物、仆从、宠物打不到也不以主人的女仆为目标")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void summonsSpareOwnerMaids(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> summonsSpareOwnerMaids(ctx, p));
    }

    /** 另一名玩家的女仆、宠物、召唤物和野生女仆照常能被 P 的女仆打到。 */
    @DisplayName("另一名玩家的女仆、宠物、召唤物和野生女仆照常能被打到")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void otherPlayerSideIsHittable(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> otherPlayerSideIsHittable(ctx, p));
    }

    /** P、Q 同队：双方的女仆和召唤物互不伤害；Q 换到另一支队伍后按主人判定，又能打到。 */
    @DisplayName("两名玩家同队时双方女仆和召唤物互不伤害，换队后又能打到")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void sameTeamSpared(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> sameTeamSpared(ctx, p));
    }

    /** 玩家本身不再被视为有主人：两名不同队的玩家不是友方，P 的女仆能打到 Q。 */
    @DisplayName("玩家本身不被视为有主人，不同队的两名玩家不是友方")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void playersAreNotOwned(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> playersAreNotOwned(ctx, p));
    }

    /**
     * 女仆放出的法术实体（铁魔法触手/根须/火墙/冰墓，Goety 尖牙/光束/陷阱/地刺/药雾，原版药水云/唤魔者尖牙/TNT）
     * 以实体本身为直接来源、没有记账者时，仍追溯到女仆：打不到主人一方，能打到野生僵尸；女仆不以它们为目标。
     */
    @DisplayName("女仆放出的法术实体追溯到女仆：打不到主人一方，能打到野生僵尸")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void spellEntitiesTraceToCaster(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> spellEntitiesTraceToCaster(ctx, p));
    }

    /** 原版 TraceableEntity 按其主人判定：野生唤魔者的尖牙照常伤害玩家的狼。 */
    @DisplayName("野生唤魔者的尖牙按其主人判定，照常伤害玩家的狼")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void wildTraceableHitsPets(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> wildTraceableHitsPets(ctx, p));
    }

    /**
     * UsefulMagic 能设主人的召唤物（magic_heart_entity）：主人设为 P 后，P 的女仆打不到它。
     * 其余实体是 Boss 或主人只能是生物，没有可设为玩家的主人，只记录不判定。
     * 结论交人工判断：UsefulMagic 召唤物是否需要额外适配。
     */
    @DisplayName("实用魔法能设主人的召唤物，主人设为玩家后其女仆打不到它")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void usefulMagicSummonsSpared(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> usefulMagicSummons(ctx, p));
    }

    /** 主人的弓箭、三叉戟、法术、TNT（间接伤害）打不到自己的女仆、狼、召唤物，也不会引起反击。 */
    @DisplayName("主人的弓箭、三叉戟、法术、TNT 间接伤害打不到自己一方，也不引起反击")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerIndirectSparesOwnSide(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> ownerIndirectSparesOwnSide(ctx, p));
    }

    /** 主人近战打自己的女仆：本模组不按友伤取消，交给车万女仆原有规则处理。 */
    @DisplayName("主人近战打自己的女仆不按友伤取消，交给车万女仆原有规则")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerMeleeFollowsMaidRule(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> ownerMeleeFollowsMaidRule(ctx, p));
    }

    /** 主人对野生女仆、另一名玩家的女仆和宠物照常造成伤害（近战和远程）。 */
    @DisplayName("主人对野生女仆、他人的女仆和宠物照常造成近战与远程伤害")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void ownerHitsOthers(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> ownerHitsOthers(ctx, p));
    }

    /** P、Q 同队且关闭友伤：P 近战和远程都打不到 Q 的女仆、狼、召唤物；打开友伤后照常能打。 */
    @DisplayName("同队关闭友伤时主人近战和远程都打不到队友一方，打开友伤后能打到")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void teamFriendlyFireOff(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> teamFriendlyFireOff(ctx, p));
    }

    /** 玩家之间的伤害不经本模组判定，仍按原版队伍规则。 */
    @DisplayName("玩家之间的伤害不经本模组判定，仍按原版队伍规则")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void playersFollowVanilla(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        ctx.run(() -> playersFollowVanilla(ctx, p));
    }

    /** 装 Goety 时：玩家的间接法术伤害不打自己的仆从、也不打属于自己女仆的仆从；女仆把这些仆从当友方。 */
    @DisplayName("装有 Goety 时，玩家的间接法术伤害不打自己及自己女仆的仆从，女仆视其为友方")
    @Test
    @Requires(value = Requires.Capability.CLIENT, mods = "goety")
    void goetyServantsOfOwnerSide(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player player, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer p = Players.host(ctx, player);
        if (!ctx.get(() -> goetyServantsOfOwnerSide(ctx, p))) {
            throw ctx.skip("Goety 未加载");
        }
    }

    /** P 这一方：P、P 的两只女仆、狼、猫、铁魔法召唤物、Goety 仆从、新生魔艺召唤物。 */
    private static Map<String, LivingEntity> ownerSide(Stage ctx, ServerPlayer p, EntityMaid caster) {
        Map<String, LivingEntity> side = new LinkedHashMap<>();
        side.put("主人", p);
        side.put("同主人的另一只女仆", Owners.maid(ctx, p, 2, 0, 2));
        side.put("主人的狼", Owners.pet(ctx, "minecraft:wolf", p, -2, 0, 2));
        side.put("主人的猫", Owners.pet(ctx, "minecraft:cat", p, 2, 0, -2));
        side.put("主人的铁魔法召唤物", (LivingEntity) Owners.owned(ctx, ISS_SUMMON, p, -2, 0, -2));
        side.put("主人的 Goety 仆从", (LivingEntity) Owners.owned(ctx, GOETY_SERVANT, p, 4, 0, 0));
        side.put("主人的新生魔艺召唤物", (LivingEntity) Owners.owned(ctx, ARS_SUMMON, p, -4, 0, 0));
        side.values().removeIf(entity -> entity == null || entity == caster);
        return side;
    }

    /** Q 这一方：Q 的女仆、狼、铁魔法召唤物，以及没有主人的野生女仆。 */
    private static Map<String, LivingEntity> otherSide(Stage ctx, ServerPlayer q) {
        Map<String, LivingEntity> side = new LinkedHashMap<>();
        side.put("另一名玩家的女仆", Owners.maid(ctx, q, 0, 0, 6));
        side.put("另一名玩家的狼", Owners.pet(ctx, "minecraft:wolf", q, 2, 0, 6));
        side.put("另一名玩家的铁魔法召唤物", (LivingEntity) Owners.owned(ctx, ISS_SUMMON, q, -2, 0, 6));
        side.put("野生女仆", Actors.stillMaid(ctx, 4, 0, 6));
        side.values().removeIf(java.util.Objects::isNull);
        return side;
    }

    private static void maidSparesOwnerSide(Stage ctx, ServerPlayer p) {
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, LivingEntity> side = ownerSide(ctx, p, caster);
        Hits.expectAll(ctx, "女仆法术伤害打主人一方", side, Hits.magic(caster, caster), false);
        Arrow arrow = arrow(ctx, caster);
        Hits.expectAll(ctx, "女仆射出的箭打主人一方", side,
                Hits.source(ctx.level(), DamageTypes.ARROW, arrow, caster), false);
        Hits.expectAll(ctx, "女仆的持续法术（无记账者）打主人一方", side, Hits.magic(caster, null), false);
    }

    private static void maidDoesNotTargetOwnerSide(Stage ctx, ServerPlayer p) {
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, String> targets = new TreeMap<>();
        ownerSide(ctx, p, caster).forEach((name, entity) -> targets.put(name, String.valueOf(Owners.aim(caster, entity))));
        Map<String, String> expected = new TreeMap<>();
        targets.keySet().forEach(name -> expected.put(name, "null"));
        ctx.check(targets).as("女仆以主人一方为目标后的实际目标").isEqualTo(expected);
    }

    private static void summonsSpareOwnerMaids(Stage ctx, ServerPlayer p) {
        EntityMaid maid = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, LivingEntity> side = ownerSide(ctx, p, maid);
        side.remove("主人");
        side.remove("同主人的另一只女仆");
        Map<String, Boolean> lands = new TreeMap<>();
        Map<String, String> aims = new TreeMap<>();
        side.forEach((name, attacker) -> {
            lands.put(name, Hits.lands(maid, maid.damageSources().mobAttack(attacker)));
            if (attacker instanceof Mob mob) {
                aims.put(name, String.valueOf(Owners.aim(mob, maid)));
            }
        });
        ctx.check(lands.containsValue(true)).as("主人一方的召唤物打到主人女仆 " + lands).isFalse();
        ctx.check(aims.values().stream().allMatch("null"::equals)).as("主人一方的召唤物以主人女仆为目标 " + aims).isTrue();
    }

    private static void otherPlayerSideIsHittable(Stage ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Hits.expectAll(ctx, "女仆法术伤害打另一名玩家一方", otherSide(ctx, q), Hits.magic(caster, caster), true);
    }

    private static void sameTeamSpared(Stage ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        Owners.team(ctx, "tlms_ally_a", true, p, q);
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, LivingEntity> others = otherSide(ctx, q);
        others.remove("野生女仆");
        Hits.expectAll(ctx, "同队时女仆打另一名玩家一方", others, Hits.magic(caster, caster), false);
        Owners.team(ctx, "tlms_ally_b", true, q);
        Hits.expectAll(ctx, "不同队时女仆打另一名玩家一方", others, Hits.magic(caster, caster), true);
        ctx.check(MaidSpellAllyResolver.areFriendly(caster, Owners.maid(ctx, p, 2, 0, 3)))
                .as("不同队时同主人的两只女仆仍是友方").isTrue();
    }

    private static void playersAreNotOwned(Stage ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        ctx.check(MaidSpellAllyResolver.areFriendly(p, q)).as("两名玩家是友方").isFalse();
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        ctx.check(MaidSpellAllyResolver.areFriendly(caster, q)).as("女仆与另一名玩家是友方").isFalse();
    }

    private static void spellEntitiesTraceToCaster(Stage ctx, ServerPlayer p) {
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, LivingEntity> side = ownerSide(ctx, p, caster);
        side.put("施法女仆", caster);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, -6, true);
        Map<String, Map<String, Boolean>> spared = new TreeMap<>();
        Map<String, Boolean> hitsEnemy = new TreeMap<>();
        Map<String, String> targeted = new TreeMap<>();
        int x = -6;
        for (String type : SPELL_ENTITIES) {
            Entity spell = Owners.owned(ctx, type, caster, x++, 0, -3);
            if (spell == null) {
                continue;
            }
            DamageSource source = Hits.magic(spell, null);
            Map<String, Boolean> matrix = Hits.matrix(side, source);
            matrix.values().removeIf(landed -> !landed);
            if (!matrix.isEmpty()) {
                spared.put(type, matrix);
            }
            hitsEnemy.put(type, Hits.lands(zombie, source));
            if (spell instanceof LivingEntity living) {
                targeted.put(type, String.valueOf(Owners.aim(caster, living)));
            }
        }
        ctx.record("hitsEnemy", hitsEnemy);
        ctx.check(spared).as("法术实体打到主人一方（类型 → 被打到的目标）").isEmpty();
        ctx.check(hitsEnemy.containsValue(false)).as("有法术实体打不到野生僵尸 " + hitsEnemy).isFalse();
        ctx.check(targeted.values().stream().allMatch("null"::equals)).as("女仆以自己的法术实体为目标 " + targeted).isTrue();
    }

    private static void wildTraceableHitsPets(Stage ctx, ServerPlayer p) {
        LivingEntity wolf = Owners.pet(ctx, "minecraft:wolf", p, 2, 0, 2);
        Mob evoker = Actors.spawn(ctx, "minecraft:evoker", -3, 0, 0, true);
        Entity fangs = Owners.owned(ctx, "minecraft:evoker_fangs", evoker, 0, 0, 2);
        ctx.check(Hits.lands(wolf, Hits.magic(fangs, evoker))).as("野生唤魔者的尖牙打主人的狼").isTrue();
        ctx.check(Hits.lands(wolf, Hits.magic(fangs, null))).as("野生唤魔者的尖牙（无记账者）打主人的狼").isTrue();
    }

    private static void usefulMagicSummons(Stage ctx, ServerPlayer p) {
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, Boolean> lands = new TreeMap<>();
        Map<String, String> unsupported = new TreeMap<>();
        int x = -6;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().equals("usefulmagic")) {
                continue;
            }
            Entity probe = type.create(ctx.level());
            boolean living = probe instanceof LivingEntity;
            if (probe != null) {
                probe.discard();
            }
            if (!living) {
                continue;
            }
            try {
                Entity summon = Owners.owned(ctx, id.toString(), p, x++, 0, -4);
                lands.put(id.toString(), Hits.lands((LivingEntity) summon, Hits.magic(caster, caster)));
            } catch (IllegalStateException e) {
                unsupported.put(id.toString(), e.getMessage());
            }
        }
        ctx.record("noOwnerSetter", unsupported);
        ctx.record("maidHitsSummon", lands);
        ctx.check(lands.keySet()).as("实际测到的 UsefulMagic 召唤物").isNotEmpty();
        ctx.check(lands.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList())
                .as("被主人自己的女仆打到的 UsefulMagic 召唤物").isEmpty();
    }

    private static void ownerIndirectSparesOwnSide(Stage ctx, ServerPlayer p) {
        Map<String, LivingEntity> side = ownerSide(ctx, p, null);
        side.remove("主人");
        Map<String, DamageSource> sources = new LinkedHashMap<>();
        sources.put("箭", Hits.source(ctx.level(), DamageTypes.ARROW, arrow(ctx, p), p));
        ThrownTrident trident = new ThrownTrident(ctx.level(), p, new ItemStack(Items.TRIDENT));
        ctx.cleanup(trident::discard);
        sources.put("三叉戟", Hits.source(ctx.level(), DamageTypes.TRIDENT, trident, p));
        sources.put("法术", Hits.source(ctx.level(), DamageTypes.INDIRECT_MAGIC, Owners.owned(ctx, "minecraft:area_effect_cloud", p, 0, 0, -3), p));
        Entity tnt = Owners.owned(ctx, "minecraft:tnt", p, 0, 0, -5);
        sources.put("TNT", ctx.level().damageSources().explosion(tnt, p));
        sources.forEach((name, source) -> Hits.expectAll(ctx, "主人的" + name + "打自己一方", side, source, false));
        side.forEach((name, entity) -> {
            if (entity instanceof Mob mob) {
                ctx.check(mob.getTarget()).as(name + " 被误伤后的目标").isNull();
            }
        });
    }

    private static void ownerMeleeFollowsMaidRule(Stage ctx, ServerPlayer p) {
        EntityMaid maid = Owners.maid(ctx, p, 0, 0, 2);
        ctx.check(MaidSpellAllyResolver.isFriendlyDamage(maid, p, p)).as("主人近战打自己女仆算友伤").isFalse();
        ctx.record("maidDamageFromOwnerMelee", Hits.dealt(maid, p.damageSources().playerAttack(p), Hits.PROBE));
    }

    private static void ownerHitsOthers(Stage ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        Map<String, LivingEntity> others = otherSide(ctx, q);
        Hits.expectAll(ctx, "主人近战打别人一方", others, p.damageSources().playerAttack(p), true);
        Hits.expectAll(ctx, "主人射箭打别人一方", others, Hits.source(ctx.level(), DamageTypes.ARROW, arrow(ctx, p), p), true);
    }

    private static void teamFriendlyFireOff(Stage ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        var team = Owners.team(ctx, "tlms_ally_ff", false, p, q);
        Map<String, LivingEntity> others = otherSide(ctx, q);
        others.remove("野生女仆");
        DamageSource arrow = Hits.source(ctx.level(), DamageTypes.ARROW, arrow(ctx, p), p);
        Hits.expectAll(ctx, "关闭友伤时主人近战打队友一方", others, p.damageSources().playerAttack(p), false);
        Hits.expectAll(ctx, "关闭友伤时主人射箭打队友一方", others, arrow, false);
        team.setAllowFriendlyFire(true);
        Hits.expectAll(ctx, "打开友伤后主人近战打队友一方", others, p.damageSources().playerAttack(p), true);
        Hits.expectAll(ctx, "打开友伤后主人射箭打队友一方", others, arrow, true);
    }

    private static void playersFollowVanilla(Stage ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        ctx.check(MaidSpellAllyResolver.isFriendlyDamage(q, p, p)).as("无队伍时玩家打玩家算友伤").isFalse();
        Owners.team(ctx, "tlms_ally_pvp", false, p, q);
        ctx.check(MaidSpellAllyResolver.isFriendlyDamage(q, p, p)).as("同队关闭友伤时玩家打玩家算本模组友伤").isFalse();
    }

    /** 返回 false 表示召唤物类型未注册（Goety 未加载）。 */
    private static boolean goetyServantsOfOwnerSide(Stage ctx, ServerPlayer p) {
        EntityMaid maid = Owners.maid(ctx, p, 0, 0, 3);
        LivingEntity own = (LivingEntity) Owners.owned(ctx, GOETY_SERVANT, p, 2, 0, 0);
        LivingEntity maids = (LivingEntity) Owners.owned(ctx, GOETY_SERVANT, maid, -2, 0, 0);
        if (own == null || maids == null) {
            return false;
        }
        Entity cloud = Owners.owned(ctx, "minecraft:area_effect_cloud", p, 0, 0, -3);
        DamageSource indirect = Hits.source(ctx.level(), DamageTypes.INDIRECT_MAGIC, cloud, p);
        ctx.check(Hits.lands(own, indirect)).as("玩家间接法术打自己的仆从").isFalse();
        ctx.check(Hits.lands(maids, indirect)).as("玩家间接法术打自己女仆的仆从").isFalse();
        ctx.check(MaidSpellAllyResolver.areFriendly(maid, own)).as("女仆与主人的仆从是友方").isTrue();
        ctx.check(MaidSpellAllyResolver.areFriendly(maid, maids)).as("女仆与自己的仆从是友方").isTrue();
        return true;
    }

    private static Arrow arrow(Stage ctx, LivingEntity shooter) {
        Arrow arrow = new Arrow(ctx.level(), shooter, new ItemStack(Items.ARROW), null);
        ctx.cleanup(arrow::discard);
        return arrow;
    }
}
