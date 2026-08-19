package com.github.yimeng261.maidspell.entity;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class MagicalWinefoxBossEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_SERIAL =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> CAST_ANIMATION =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> PHASE_TWO =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRANSITIONING =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int ACTION_NONE = -1;
    private static final int ACTION_STAFF_1 = 0;
    private static final int ACTION_STAFF_2 = 1;
    private static final int ACTION_SWORD_1 = 2;
    private static final int ACTION_SWORD_2 = 3;
    private static final int ACTION_SWORD_3 = 4;
    private static final int ACTION_SWORD_4 = 5;
    private static final int ACTION_PHASE_TRANSITION = 6;
    private static final int ACTION_SPEAR_THROW = 7;
    private static final int ACTION_CAST = 8;
    private static final int SWORD_COMBO_RESET_TICKS = 40;
    private static final int PHASE_TRANSITION_TICKS = 120;
    private static final int PHASE_TRANSITION_KNOCKBACK_TICK = 55;
    private static final int SPEAR_THROW_TICKS = 64;
    private static final int SPEAR_RELEASE_TICK = 40;
    private static final int DEFEAT_ANIMATION_TICKS = 40;
    private static final double COMBAT_TELEPORT_DISTANCE = 15.0D;
    private static final double TRANSITION_KNOCKBACK_RADIUS = 5.0D;
    private static final double TRANSITION_KNOCKBACK_STRENGTH = 4.0D;

    private static final RawAnimation PHASE_ONE_IDLE = RawAnimation.begin().thenLoop("phase_one_idle");
    private static final RawAnimation PHASE_TWO_IDLE = RawAnimation.begin().thenLoop("phase_two_idle");
    private static final RawAnimation STAFF_FORM = RawAnimation.begin().thenLoop("staff_form");
    private static final RawAnimation SWORD_FORM = RawAnimation.begin().thenLoop("sword_form");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
    private static final RawAnimation JUMP = RawAnimation.begin().thenLoop("jump");
    private static final RawAnimation DEFEAT = RawAnimation.begin().thenPlayAndHold("defeat");
    private static final RawAnimation AMBIENT_PARTS = RawAnimation.begin().thenLoop("ambient_parts");
    private static final RawAnimation BLINK = RawAnimation.begin().thenLoop("blink");
    private static final RawAnimation TAIL_IDLE = RawAnimation.begin().thenLoop("tail_idle");
    private static final RawAnimation TAIL_WALK = RawAnimation.begin().thenLoop("tail_walk");
    private static final RawAnimation TAIL_RUN = RawAnimation.begin().thenLoop("tail_run");
    private static final RawAnimation TAIL_JUMP = RawAnimation.begin().thenLoop("tail_jump");
    private static final RawAnimation MAGIC_RINGS = RawAnimation.begin().thenLoop("magic_rings");
    private static final RawAnimation HOLD_SWORD = RawAnimation.begin().thenPlayAndHold("hold_mainhand:sword");
    private static final RawAnimation HOLD_BOW = RawAnimation.begin().thenLoop("hold_mainhand:bow");
    private static final RawAnimation STAFF_ATTACK_1 = RawAnimation.begin().thenPlay("staff_attack_1");
    private static final RawAnimation STAFF_ATTACK_2 = RawAnimation.begin().thenPlay("staff_attack_2");
    private static final RawAnimation SWORD_ATTACK_1 = RawAnimation.begin().thenPlay("sword_attack_01");
    private static final RawAnimation SWORD_ATTACK_2 = RawAnimation.begin().thenPlay("sword_attack_02");
    private static final RawAnimation SWORD_ATTACK_3 = RawAnimation.begin().thenPlay("sword_attack_03");
    private static final RawAnimation SWORD_ATTACK_4 = RawAnimation.begin().thenPlay("sword_attack_04");
    private static final RawAnimation PHASE_TRANSITION = RawAnimation.begin().thenPlay("phase_transition");
    private static final RawAnimation SPEAR_THROW = RawAnimation.begin().thenPlay("spear_throw");
    private static final Map<String, RawAnimation> ISS_CAST_ANIMATIONS = Map.ofEntries(
            Map.entry("instant_projectile", RawAnimation.begin().thenPlay("iss:instant_projectile")),
            Map.entry("instant_slash", RawAnimation.begin().thenPlay("iss:instant_slash")),
            Map.entry("katana_upslash", RawAnimation.begin().thenPlay("iss:katana_upslash")),
            Map.entry("continuous_thrust", RawAnimation.begin().thenLoop("iss:continuous_thrust")),
            Map.entry("continuous_overhead", RawAnimation.begin().thenLoop("iss:continuous_overhead")),
            Map.entry("long_cast", RawAnimation.begin().thenLoop("iss:long_cast")),
            Map.entry("charged_throw", RawAnimation.begin().thenPlay("iss:charged_throw")),
            Map.entry("charge_wavy", RawAnimation.begin().thenPlay("iss:charge_wavy")),
            Map.entry("charge_raised_hand", RawAnimation.begin().thenPlay("iss:charge_raised_hand")),
            Map.entry("touch_ground", RawAnimation.begin().thenPlay("iss:touch_ground")),
            Map.entry("charge_black_hole", RawAnimation.begin().thenLoop("iss:charge_black_hole")),
            Map.entry("long_cast_finish", RawAnimation.begin().thenPlay("iss:long_cast_finish")),
            Map.entry("charge_arrow", RawAnimation.begin().thenPlay("iss:charge_arrow")),
            Map.entry("charge_spit", RawAnimation.begin().thenPlay("iss:charge_spit")),
            Map.entry("charge_spit_finish", RawAnimation.begin().thenPlay("iss:charge_spit_finish")),
            Map.entry("instant_self", RawAnimation.begin().thenPlay("iss:instant_self")),
            Map.entry("horizontal_slash_one_handed",
                    RawAnimation.begin().thenPlay("iss:horizontal_slash_one_handed")),
            Map.entry("overhead_two_handed_swing",
                    RawAnimation.begin().thenPlay("iss:overhead_two_handed_swing")),
            Map.entry("cross_arms", RawAnimation.begin().thenLoop("iss:cross_arms")),
            Map.entry("cast_t_pose", RawAnimation.begin().thenPlay("iss:cast_t_pose")),
            Map.entry("stomp", RawAnimation.begin().thenPlay("iss:stomp")));

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private int nextSwordVariant;
    private int nextStaffVariant;
    private int lastSwordSwingTick = Integer.MIN_VALUE;
    private int phaseTransitionTicks;
    private int spearThrowTicks;
    private int lastHandledActionSerial = Integer.MIN_VALUE;
    private boolean phaseTransitionKnockbackReleased;
    private boolean spearProjectileReleased;
    private boolean actionAnimationPlaying;

    public MagicalWinefoxBossEntity(EntityType<? extends MagicalWinefoxBossEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 80;
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    @Override
    protected float getFlyingSpeed() {
        return 0.04F;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isNoGravity() && this.getTarget() != null && this.getTarget().isAlive()) {
            // Combat flight is horizontal; the vanilla flying controller's binary Y input can overshoot badly.
            Vec3 delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, 0.0D, delta.z);
            super.travel(new Vec3(travelVector.x, 0.0D, travelVector.z));
            delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, 0.0D, delta.z);
            return;
        }
        super.travel(travelVector);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WinefoxBossSpellBridge.addOptionalAttributes(Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FLYING_SPEED, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanFloat(true);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ACTION, ACTION_NONE);
        this.entityData.define(ACTION_SERIAL, 0);
        this.entityData.define(CAST_ANIMATION, "");
        this.entityData.define(PHASE_TWO, false);
        this.entityData.define(TRANSITIONING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new WinefoxCombatGoal(this));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    public boolean isHoldingSword() {
        return this.isPhaseTwo();
    }

    public boolean isHoldingBow() {
        return !this.isPhaseTwo();
    }

    public boolean isPhaseTwo() {
        return this.entityData.get(PHASE_TWO);
    }

    public boolean isTransitioning() {
        return this.entityData.get(TRANSITIONING);
    }

    private void beginAction(int action) {
        this.entityData.set(ACTION, action);
        this.entityData.set(ACTION_SERIAL, this.entityData.get(ACTION_SERIAL) + 1);
    }

    private int nextGroundSwordAction() {
        if (this.lastSwordSwingTick == Integer.MIN_VALUE
                || this.tickCount - this.lastSwordSwingTick > SWORD_COMBO_RESET_TICKS) {
            this.nextSwordVariant = 0;
        }
        this.lastSwordSwingTick = this.tickCount;
        return switch (this.nextSwordVariant++ & 3) {
            case 1 -> ACTION_SWORD_2;
            case 2 -> ACTION_SWORD_3;
            case 3 -> ACTION_SWORD_4;
            default -> ACTION_SWORD_1;
        };
    }

    private int nextStaffAction() {
        return this.nextStaffVariant++ % 2 == 0 ? ACTION_STAFF_1 : ACTION_STAFF_2;
    }

    @Override
    public void swing(InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            if (this.isTransitioning()) {
                return;
            }
            if (this.isPhaseTwo()) {
                this.beginAction(this.nextGroundSwordAction());
            } else {
                this.beginAction(this.nextStaffAction());
            }
        }
        super.swing(hand);
    }

    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, distanceFactor);
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontalDistance * 0.2, dz, 1.6F, 4.0F);
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        this.level().addFreshEntity(arrow);
        this.swing(InteractionHand.MAIN_HAND);
    }

    private void performFallbackSpear(LivingEntity target) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, 1.0F);
        Vec3 direction = target.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
        arrow.setBaseDamage(20.0D);
        arrow.setNoGravity(true);
        arrow.shoot(direction.x, direction.y, direction.z, 3.5F, 0.0F);
        this.level().addFreshEntity(arrow);
    }

    private void triggerCastStartAnimation(WinefoxBossSpellAction action) {
        this.triggerCastAnimation(action, false);
    }

    private void triggerCastFinishAnimation(WinefoxBossSpellAction action) {
        this.triggerCastAnimation(action, true);
    }

    private void triggerCastAnimation(WinefoxBossSpellAction action, boolean finish) {
        if (this.isTransitioning() || this.spearThrowTicks > 0) {
            return;
        }
        String animationPath = WinefoxBossSpellBridge.getCastAnimation(action, finish);
        if (WinefoxBossSpellBridge.STOP_CAST_ANIMATION.equals(animationPath)) {
            this.stopCastAnimation();
        } else if (animationPath != null && ISS_CAST_ANIMATIONS.containsKey(animationPath)) {
            this.entityData.set(CAST_ANIMATION, animationPath);
            this.beginAction(ACTION_CAST);
        }
    }

    private void stopCastAnimation() {
        if (this.entityData.get(ACTION) == ACTION_CAST) {
            this.entityData.set(ACTION, ACTION_NONE);
        }
        this.entityData.set(CAST_ANIMATION, "");
    }

    private boolean isBusyCombatAction() {
        return this.isTransitioning() || this.spearThrowTicks > 0;
    }

    private boolean startSpearThrow(LivingEntity target) {
        if (!this.isPhaseTwo() || this.isBusyCombatAction()) {
            return false;
        }
        this.teleportAwayFrom(target, COMBAT_TELEPORT_DISTANCE);
        this.spearThrowTicks = SPEAR_THROW_TICKS;
        this.spearProjectileReleased = false;
        this.beginAction(ACTION_SPEAR_THROW);
        return true;
    }

    private boolean teleportAwayFrom(LivingEntity target, double distance) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Vec3 away = this.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-4D) {
            away = target.getLookAngle().multiply(-1.0D, 0.0D, -1.0D);
        }
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();

        Vec3 origin = this.position();
        int[] verticalOffsets = {0, 1, 2, -1, 3, -2, 4};
        double[] distanceScales = {1.0D, 0.85D, 0.7D, 0.55D, 0.4D};
        for (double distanceScale : distanceScales) {
            Vec3 horizontal = away.scale(distance * distanceScale);
            for (int verticalOffset : verticalOffsets) {
                Vec3 candidate = origin.add(horizontal).add(0.0D, verticalOffset, 0.0D);
                BlockPos candidatePos = BlockPos.containing(candidate);
                AABB destinationBox = this.getBoundingBox().move(candidate.subtract(origin));
                if (!serverLevel.getWorldBorder().isWithinBounds(candidatePos)
                        || !serverLevel.getFluidState(candidatePos).isEmpty()
                        || !serverLevel.noCollision(this, destinationBox)) {
                    continue;
                }
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(0.5D), this.getZ(),
                        32, 0.35D, 0.7D, 0.35D, 0.2D);
                this.teleportTo(candidate.x, candidate.y, candidate.z);
                this.setDeltaMovement(Vec3.ZERO);
                this.resetFallDistance();
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(0.5D), this.getZ(),
                        32, 0.35D, 0.7D, 0.35D, 0.2D);
                this.playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.0F);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.prioritizePlayerTarget();

        if (!this.isPhaseTwo() && !this.isTransitioning()
                && this.getHealth() <= this.getMaxHealth() * 0.5F) {
            this.startPhaseTransition();
        }
        if (this.isTransitioning()) {
            this.tickPhaseTransition();
        } else if (this.spearThrowTicks > 0) {
            this.tickSpearThrow();
        }

        LivingEntity target = this.getTarget();
        boolean combatFlight = (target != null && target.isAlive()) || this.isBusyCombatAction();
        this.setNoGravity(combatFlight);
        if (combatFlight) {
            this.resetFallDistance();
        }
    }

    private void startPhaseTransition() {
        this.phaseTransitionTicks = PHASE_TRANSITION_TICKS;
        this.phaseTransitionKnockbackReleased = false;
        this.entityData.set(TRANSITIONING, true);
        this.beginAction(ACTION_PHASE_TRANSITION);
    }

    private void tickPhaseTransition() {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        int elapsedTicks = PHASE_TRANSITION_TICKS - this.phaseTransitionTicks;
        if (!this.phaseTransitionKnockbackReleased
                && elapsedTicks >= PHASE_TRANSITION_KNOCKBACK_TICK) {
            this.phaseTransitionKnockbackReleased = true;
            this.releasePhaseTransitionKnockback();
        }
        if (--this.phaseTransitionTicks > 0) {
            return;
        }

        this.entityData.set(TRANSITIONING, false);
        this.entityData.set(PHASE_TWO, true);
        this.entityData.set(ACTION, ACTION_NONE);
        this.nextSwordVariant = 0;
        WinefoxBossSpellBridge.cast(this, this.getTarget(), WinefoxBossSpellAction.VOID_PHASE, 1);
    }

    private void releasePhaseTransitionKnockback() {
        AABB area = this.getBoundingBox().inflate(TRANSITION_KNOCKBACK_RADIUS);
        for (Entity entity : this.level().getEntities(this, area,
                entity -> entity.isAlive() && !entity.isSpectator())) {
            Vec3 horizontal = entity.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
            if (horizontal.lengthSqr() < 1.0E-4D) {
                double angle = this.random.nextDouble() * Mth.TWO_PI;
                horizontal = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            }
            horizontal = horizontal.normalize().scale(TRANSITION_KNOCKBACK_STRENGTH);
            entity.push(horizontal.x, 0.75D, horizontal.z);
        }
    }

    private void tickSpearThrow() {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        int elapsedTicks = SPEAR_THROW_TICKS - this.spearThrowTicks;
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.getLookControl().setLookAt(target, 45.0F, 45.0F);
        }
        if (!this.spearProjectileReleased && elapsedTicks >= SPEAR_RELEASE_TICK) {
            this.spearProjectileReleased = true;
            if (target != null && target.isAlive()
                    && !WinefoxBossSpellBridge.cast(this, target, WinefoxBossSpellAction.SPEAR_THROW, 1)) {
                this.performFallbackSpear(target);
            }
        }
        if (--this.spearThrowTicks <= 0) {
            this.spearThrowTicks = 0;
            this.entityData.set(ACTION, ACTION_NONE);
        }
    }

    private void prioritizePlayerTarget() {
        if (this.tickCount % 10 != 0) {
            return;
        }
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget instanceof Player player && isAttackablePlayer(player)) {
            return;
        }
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        Player nearestPlayer = this.level().getEntitiesOfClass(Player.class,
                        this.getBoundingBox().inflate(range), MagicalWinefoxBossEntity::isAttackablePlayer)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (nearestPlayer != null) {
            this.setTarget(nearestPlayer);
        }
    }

    private static boolean isAttackablePlayer(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return this.isTransitioning() || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float adjustedAmount = amount;
        if (isMaidDamage(source)) {
            adjustedAmount *= 0.5F;
        }
        if (this.isPhaseTwo()) {
            adjustedAmount *= 0.5F;
        }
        return super.hurt(source, adjustedAmount);
    }

    private static boolean isMaidDamage(DamageSource source) {
        Entity causingEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();
        if (causingEntity instanceof EntityMaid || directEntity instanceof EntityMaid) {
            return true;
        }
        return directEntity instanceof Projectile projectile && projectile.getOwner() instanceof EntityMaid;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WinefoxPhaseTwo", this.isPhaseTwo());
        tag.putBoolean("WinefoxTransitioning", this.isTransitioning());
        tag.putInt("WinefoxTransitionTicks", this.phaseTransitionTicks);
        tag.putInt("WinefoxSpearThrowTicks", this.spearThrowTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(PHASE_TWO, tag.getBoolean("WinefoxPhaseTwo"));
        this.phaseTransitionTicks = tag.getInt("WinefoxTransitionTicks");
        boolean transitioning = tag.getBoolean("WinefoxTransitioning") && this.phaseTransitionTicks > 0;
        this.entityData.set(TRANSITIONING, transitioning);
        this.phaseTransitionKnockbackReleased = transitioning
                && this.phaseTransitionTicks <= PHASE_TRANSITION_TICKS - PHASE_TRANSITION_KNOCKBACK_TICK;
        this.spearThrowTicks = tag.getInt("WinefoxSpearThrowTicks");
        this.spearProjectileReleased = this.spearThrowTicks > 0
                && this.spearThrowTicks <= SPEAR_THROW_TICKS - SPEAR_RELEASE_TICK;
        if (transitioning && !this.level().isClientSide) {
            this.beginAction(ACTION_PHASE_TRANSITION);
        } else if (this.spearThrowTicks > 0 && !this.level().isClientSide) {
            this.beginAction(ACTION_SPEAR_THROW);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private PlayState mainAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DEFEAT);
        }
        if (this.hurtTime > 0) {
            return state.setAndContinue(this.isPhaseTwo() ? PHASE_TWO_IDLE : PHASE_ONE_IDLE);
        }
        if (!this.onGround()) {
            return state.setAndContinue(this.isNoGravity() ? FLY : JUMP);
        }
        if (state.isMoving()) {
            return state.setAndContinue(this.getDeltaMovement().horizontalDistanceSqr() > 0.08 ? RUN : WALK);
        }
        return state.setAndContinue(this.isPhaseTwo() ? PHASE_TWO_IDLE : PHASE_ONE_IDLE);
    }

    private PlayState weaponFormAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        return state.setAndContinue(this.isPhaseTwo() ? SWORD_FORM : STAFF_FORM);
    }

    private PlayState tailAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (!this.onGround()) {
            return state.setAndContinue(TAIL_JUMP);
        }
        if (state.isMoving()) {
            return state.setAndContinue(this.getDeltaMovement().horizontalDistanceSqr() > 0.08
                    ? TAIL_RUN : TAIL_WALK);
        }
        return state.setAndContinue(TAIL_IDLE);
    }

    private PlayState staffHoldAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isPhaseTwo() || this.isDeadOrDying() || this.isTransitioning() || this.hurtTime > 0
                || (this.onGround() && !state.isMoving())) {
            return PlayState.STOP;
        }
        return state.setAndContinue(HOLD_BOW);
    }

    private PlayState swordHoldAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (!this.isPhaseTwo() || this.isDeadOrDying() || this.isTransitioning() || this.hurtTime > 0) {
            return PlayState.STOP;
        }
        return state.setAndContinue(HOLD_SWORD);
    }

    private PlayState actionAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isDeadOrDying()) {
            this.actionAnimationPlaying = false;
            return PlayState.STOP;
        }
        int action = this.entityData.get(ACTION);
        if (action == ACTION_NONE) {
            this.lastHandledActionSerial = this.entityData.get(ACTION_SERIAL);
            this.actionAnimationPlaying = false;
            return PlayState.STOP;
        }
        int serial = this.entityData.get(ACTION_SERIAL);
        if (serial != this.lastHandledActionSerial) {
            state.getController().forceAnimationReset();
            this.lastHandledActionSerial = serial;
        } else if (state.getController().hasAnimationFinished()) {
            this.actionAnimationPlaying = false;
            return PlayState.STOP;
        }
        this.actionAnimationPlaying = true;
        if (action == ACTION_CAST) {
            RawAnimation castAnimation = ISS_CAST_ANIMATIONS.get(this.entityData.get(CAST_ANIMATION));
            if (castAnimation == null) {
                this.actionAnimationPlaying = false;
                return PlayState.STOP;
            }
            return state.setAndContinue(castAnimation);
        }
        return switch (action) {
            case ACTION_STAFF_2 -> state.setAndContinue(STAFF_ATTACK_2);
            case ACTION_SWORD_1 -> state.setAndContinue(SWORD_ATTACK_1);
            case ACTION_SWORD_2 -> state.setAndContinue(SWORD_ATTACK_2);
            case ACTION_SWORD_3 -> state.setAndContinue(SWORD_ATTACK_3);
            case ACTION_SWORD_4 -> state.setAndContinue(SWORD_ATTACK_4);
            case ACTION_PHASE_TRANSITION -> state.setAndContinue(PHASE_TRANSITION);
            case ACTION_SPEAR_THROW -> state.setAndContinue(SPEAR_THROW);
            default -> state.setAndContinue(STAFF_ATTACK_1);
        };
    }

    public boolean isActionAnimationPlaying() {
        return this.actionAnimationPlaying;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<MagicalWinefoxBossEntity> mainController =
                new AnimationController<>(this, "main", 2, this::mainAnimation)
                        .setSoundKeyframeHandler(this::handleSoundKeyframe);
        AnimationController<MagicalWinefoxBossEntity> actionController =
                new AnimationController<>(this, "action", 0, this::actionAnimation)
                        .setSoundKeyframeHandler(this::handleSoundKeyframe);
        controllers.add(
                new AnimationController<>(this, "ambient_parts", 0, state -> state.setAndContinue(AMBIENT_PARTS)),
                new AnimationController<>(this, "blink", 0, state -> state.setAndContinue(BLINK)),
                new AnimationController<>(this, "weapon_form", 0, this::weaponFormAnimation),
                mainController,
                new AnimationController<>(this, "tail", 2, this::tailAnimation),
                new AnimationController<>(this, "staff_hold", 2, this::staffHoldAnimation),
                new AnimationController<>(this, "sword_hold", 2, this::swordHoldAnimation),
                actionController,
                new AnimationController<>(this, "magic_rings", 0, state -> state.setAndContinue(MAGIC_RINGS)));
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= DEFEAT_ANIMATION_TICKS && !this.level().isClientSide && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    private void handleSoundKeyframe(SoundKeyframeEvent<MagicalWinefoxBossEntity> event) {
        if (!this.level().isClientSide) {
            return;
        }
        var sound = MaidSpellSounds.getWinefoxSound(event.getKeyframeData().getSound());
        if (sound != null) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE,
                    1.0F, 1.0F, false);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    private static final class WinefoxCombatGoal extends Goal {
        private static final List<WinefoxBossSpellAction> PHASE_ONE_SPELLS = List.of(
                WinefoxBossSpellAction.MAGIC_MISSILE,
                WinefoxBossSpellAction.MAGIC_ARROW,
                WinefoxBossSpellAction.SUMMON_SWORDS,
                WinefoxBossSpellAction.FIREBALL,
                WinefoxBossSpellAction.LIGHTNING_LANCE,
                WinefoxBossSpellAction.HEAL,
                WinefoxBossSpellAction.MODIFIED_STARFALL,
                WinefoxBossSpellAction.MAGIC_SHOTGUN);
        private static final List<WinefoxBossSpellAction> PHASE_TWO_CLOSE_SPELLS = List.of(
                WinefoxBossSpellAction.ECHOING_STRIKES,
                WinefoxBossSpellAction.SHADOW_SLASH,
                WinefoxBossSpellAction.MODIFIED_TELEPORT,
                WinefoxBossSpellAction.HEAL,
                WinefoxBossSpellAction.FLAMING_STRIKE,
                WinefoxBossSpellAction.DIVINE_SMITE);
        private static final List<WinefoxBossSpellAction> PHASE_TWO_FAR_SPELLS = List.of(
                WinefoxBossSpellAction.SHADOW_SLASH,
                WinefoxBossSpellAction.MODIFIED_TELEPORT,
                WinefoxBossSpellAction.SWORD_PRISON);

        private final MagicalWinefoxBossEntity boss;
        private final EnumMap<WinefoxBossSpellAction, Integer> spellCooldowns =
                new EnumMap<>(WinefoxBossSpellAction.class);
        private int spellDecisionCooldown;
        private int meleeCooldown;
        private int closeRangeTicks;
        private int escapeTeleportCheckCooldown;
        private int modifiedTeleportCheckCooldown;
        private int counterspellCheckCooldown;
        private int spearCheckCooldown;
        private int voidPhaseCheckCooldown;
        private int movementRefreshCooldown;
        private double orbitDirection = 1.0D;
        private double preferredHeight;
        private boolean phaseTwo;
        @Nullable
        private WinefoxBossSpellAction burstAction;
        private int burstShots;
        private int burstDelay;
        private int burstSpellLevel;
        @Nullable
        private WinefoxBossSpellAction pendingAction;
        private int pendingSpellLevel;
        private int pendingCastTicks;

        private WinefoxCombatGoal(MagicalWinefoxBossEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.boss.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.phaseTwo = this.boss.isPhaseTwo();
            this.spellDecisionCooldown = 0;
            this.escapeTeleportCheckCooldown = 80;
            this.modifiedTeleportCheckCooldown = 120;
            this.counterspellCheckCooldown = 20;
            this.spearCheckCooldown = 400;
            this.voidPhaseCheckCooldown = 400;
            this.refreshMovementPattern();

            LivingEntity target = this.boss.getTarget();
            if (!this.phaseTwo && target != null) {
                this.boss.teleportAwayFrom(target, COMBAT_TELEPORT_DISTANCE);
                if (this.castAction(target, WinefoxBossSpellAction.MAGIC_SHOTGUN,
                        1 + this.boss.random.nextInt(5))) {
                    this.spellCooldowns.put(WinefoxBossSpellAction.MAGIC_SHOTGUN,
                            this.getSpellCooldown(WinefoxBossSpellAction.MAGIC_SHOTGUN));
                }
                this.spellDecisionCooldown = 8;
            }
        }

        @Override
        public void stop() {
            this.boss.getNavigation().stop();
            this.burstAction = null;
            this.burstShots = 0;
            if (this.pendingAction != null) {
                this.boss.stopCastAnimation();
            }
            this.pendingAction = null;
            this.pendingCastTicks = 0;
            this.closeRangeTicks = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.boss.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }

            this.tickCooldowns();
            this.boss.getLookControl().setLookAt(target, 45.0F, 45.0F);

            if (this.phaseTwo != this.boss.isPhaseTwo()) {
                this.enterPhaseTwoCombat();
            }
            if (this.boss.isBusyCombatAction()) {
                return;
            }

            double horizontalDistance = horizontalDistance(this.boss, target);
            if (this.phaseTwo) {
                this.tickPhaseTwo(target, horizontalDistance);
            } else {
                this.tickPhaseOne(target, horizontalDistance);
            }
        }

        private void tickPhaseOne(LivingEntity target, double horizontalDistance) {
            if (horizontalDistance < 3.0D) {
                ++this.closeRangeTicks;
            } else {
                this.closeRangeTicks = 0;
            }
            this.movePhaseOne(target, horizontalDistance);

            boolean shouldTeleport = false;
            if (this.modifiedTeleportCheckCooldown <= 0) {
                this.modifiedTeleportCheckCooldown = 120;
                shouldTeleport = this.boss.random.nextFloat() < 0.2F;
            }
            if (this.escapeTeleportCheckCooldown <= 0 && horizontalDistance < 5.0D) {
                this.escapeTeleportCheckCooldown = 80;
                boolean closeRangeTeleport = this.boss.random.nextFloat() < 0.25F;
                shouldTeleport = shouldTeleport || closeRangeTeleport;
            }
            if (shouldTeleport
                    && this.isSpellReady(WinefoxBossSpellAction.MODIFIED_TELEPORT)
                    && this.castAction(target, WinefoxBossSpellAction.MODIFIED_TELEPORT, 4)) {
                this.spellCooldowns.put(WinefoxBossSpellAction.MODIFIED_TELEPORT,
                        this.getSpellCooldown(WinefoxBossSpellAction.MODIFIED_TELEPORT));
                this.spellDecisionCooldown = 8;
                this.closeRangeTicks = 0;
                return;
            }

            if (this.tickPendingCast(target)) {
                return;
            }

            if (this.counterspellCheckCooldown <= 0) {
                this.counterspellCheckCooldown = 20;
                if (WinefoxBossSpellBridge.isCasting(target)
                        && this.isSpellReady(WinefoxBossSpellAction.COUNTERSPELL)
                        && this.boss.random.nextFloat() < 0.25F) {
                    if (this.castAction(target, WinefoxBossSpellAction.COUNTERSPELL, 1)) {
                        this.spellCooldowns.put(WinefoxBossSpellAction.COUNTERSPELL,
                                this.getSpellCooldown(WinefoxBossSpellAction.COUNTERSPELL));
                        this.spellDecisionCooldown = 8;
                        return;
                    }
                }
            }

            if (this.tickBurst(target) || this.spellDecisionCooldown > 0) {
                return;
            }
            this.spellDecisionCooldown = 8;
            if (!this.boss.getSensing().hasLineOfSight(target)) {
                return;
            }

            WinefoxBossSpellAction action = this.chooseSpell(PHASE_ONE_SPELLS, target, horizontalDistance);
            if (action == null) {
                this.boss.performRangedAttack(target, 1.0F);
                return;
            }
            if (action == WinefoxBossSpellAction.MAGIC_MISSILE) {
                this.startBurst(action, 3 + this.boss.random.nextInt(3), 5);
                this.spellCooldowns.put(action, this.getSpellCooldown(action));
                this.tickBurst(target);
                return;
            }
            int spellLevel = this.randomSpellLevel(action);
            if (this.startOrCastAction(target, action, spellLevel)) {
                this.spellCooldowns.put(action, this.getSpellCooldown(action));
            }
        }

        private void tickPhaseTwo(LivingEntity target, double horizontalDistance) {
            this.movePhaseTwo(target, horizontalDistance);

            if (this.tickPendingCast(target)) {
                return;
            }

            if (this.spearCheckCooldown <= 0) {
                this.spearCheckCooldown = 400;
                if (this.boss.random.nextFloat() < 0.5F && this.boss.startSpearThrow(target)) {
                    return;
                }
            }
            if (this.voidPhaseCheckCooldown <= 0) {
                this.voidPhaseCheckCooldown = 400;
                if (!WinefoxBossSpellBridge.hasVoidPhase(this.boss)
                        && this.boss.random.nextFloat() < 0.5F
                        && this.castAction(target, WinefoxBossSpellAction.VOID_PHASE, 1)) {
                    this.spellCooldowns.put(WinefoxBossSpellAction.VOID_PHASE, 400);
                }
            }

            if (this.meleeCooldown <= 0 && this.boss.distanceToSqr(target) <= 9.0D) {
                this.boss.swing(InteractionHand.MAIN_HAND);
                this.boss.doHurtTarget(target);
                this.meleeCooldown = 12;
            }

            if (this.tickBurst(target) || this.spellDecisionCooldown > 0) {
                return;
            }
            this.spellDecisionCooldown = 20;
            if (!this.boss.getSensing().hasLineOfSight(target)) {
                return;
            }

            WinefoxBossSpellAction action = this.choosePhaseTwoSpell(target, horizontalDistance);
            if (action == null) {
                return;
            }
            if (action == WinefoxBossSpellAction.MAGIC_SHOTGUN) {
                this.startBurst(action, 1 + this.boss.random.nextInt(2),
                        1 + this.boss.random.nextInt(5));
                this.spellCooldowns.put(action, this.getSpellCooldown(action));
                this.tickBurst(target);
                return;
            }
            int spellLevel = this.randomSpellLevel(action);
            if (this.startOrCastAction(target, action, spellLevel)) {
                this.spellCooldowns.put(action, this.getSpellCooldown(action));
            }
        }

        private void movePhaseOne(LivingEntity target, double horizontalDistance) {
            if (--this.movementRefreshCooldown <= 0) {
                this.refreshMovementPattern();
            }
            Vec3 away = horizontalDirection(target.position(), this.boss.position());
            Vec3 tangent = new Vec3(-away.z, 0.0D, away.x).scale(this.orbitDirection);
            Vec3 movementDirection;
            double speedModifier;
            boolean retreating = horizontalDistance < 3.0D && this.closeRangeTicks >= 80;

            if (retreating) {
                movementDirection = away;
                speedModifier = 2.0D;
            } else if (horizontalDistance > 15.0D) {
                movementDirection = away.scale(-1.0D);
                speedModifier = 1.5D;
            } else {
                double radialCorrection = Mth.clamp((horizontalDistance - 7.5D) * 0.2D, -0.8D, 0.8D);
                movementDirection = tangent.add(away.scale(-radialCorrection)).normalize();
                speedModifier = 0.7D;
            }

            double desiredY = retreating ? this.boss.getY() : target.getY() + this.preferredHeight;
            Vec3 desired = this.boss.position().add(movementDirection.scale(3.0D));
            desired = new Vec3(desired.x, desiredY, desired.z);
            this.moveTowardClearPosition(desired, speedModifier, retreating ? 0.0D : 4.0D);
        }

        private void movePhaseTwo(LivingEntity target, double horizontalDistance) {
            Vec3 desired = new Vec3(target.getX(), target.getY(), target.getZ());
            double speedModifier = horizontalDistance <= 8.0D ? 0.7D : 1.5D;
            this.moveTowardClearPosition(desired, speedModifier, 1.5D);
        }

        private void moveTowardClearPosition(Vec3 desired, double speedModifier, double maxLift) {
            Vec3 origin = this.boss.position();
            AABB destinationBox = this.boss.getBoundingBox().move(desired.subtract(origin));
            if (this.boss.horizontalCollision || !this.boss.level().noCollision(this.boss, destinationBox)) {
                double liftStep = maxLift <= 1.5D ? 0.5D : 1.0D;
                for (double lift = liftStep; lift <= maxLift; lift += liftStep) {
                    Vec3 lifted = desired.add(0.0D, lift, 0.0D);
                    AABB liftedBox = this.boss.getBoundingBox().move(lifted.subtract(origin));
                    if (this.boss.level().noCollision(this.boss, liftedBox)) {
                        desired = lifted;
                        break;
                    }
                }
            }
            this.boss.getNavigation().stop();
            this.boss.getMoveControl().setWantedPosition(desired.x, desired.y, desired.z, speedModifier);
        }

        @Nullable
        private WinefoxBossSpellAction choosePhaseTwoSpell(LivingEntity target, double horizontalDistance) {
            List<WinefoxBossSpellAction> rangePool = horizontalDistance <= 3.0D
                    ? PHASE_TWO_CLOSE_SPELLS
                    : PHASE_TWO_FAR_SPELLS;
            List<WinefoxBossSpellAction> pool = new ArrayList<>(rangePool.size() + 1);
            pool.add(WinefoxBossSpellAction.MAGIC_SHOTGUN);
            pool.addAll(rangePool);
            return this.chooseSpell(pool, target, horizontalDistance);
        }

        @Nullable
        private WinefoxBossSpellAction chooseSpell(List<WinefoxBossSpellAction> pool,
                                                    LivingEntity target, double horizontalDistance) {
            List<WinefoxBossSpellAction> eligible = new ArrayList<>();
            for (WinefoxBossSpellAction action : pool) {
                if (!this.isSpellReady(action)) {
                    continue;
                }
                if (action == WinefoxBossSpellAction.HEAL
                        && this.boss.getHealth() > this.boss.getMaxHealth() * 0.8F) {
                    continue;
                }
                if (action == WinefoxBossSpellAction.SWORD_PRISON && horizontalDistance < 3.0D) {
                    continue;
                }
                eligible.add(action);
            }
            if (eligible.isEmpty()) {
                return null;
            }
            return eligible.get(this.boss.random.nextInt(eligible.size()));
        }

        private boolean tickBurst(LivingEntity target) {
            if (this.burstAction == null || this.burstShots <= 0) {
                return false;
            }
            if (this.burstDelay > 0) {
                --this.burstDelay;
                return true;
            }
            this.castAction(target, this.burstAction, this.burstSpellLevel);
            --this.burstShots;
            this.burstDelay = this.burstAction == WinefoxBossSpellAction.MAGIC_MISSILE ? 3 : 6;
            if (this.burstShots <= 0) {
                this.burstAction = null;
            }
            return true;
        }

        private void startBurst(WinefoxBossSpellAction action, int shots, int spellLevel) {
            this.burstAction = action;
            this.burstShots = shots;
            this.burstDelay = 0;
            this.burstSpellLevel = spellLevel;
        }

        private boolean castAction(LivingEntity target, WinefoxBossSpellAction action, int spellLevel) {
            return this.castAction(target, action, spellLevel, false);
        }

        private boolean castAction(LivingEntity target, WinefoxBossSpellAction action,
                                   int spellLevel, boolean finishAnimation) {
            boolean cast = WinefoxBossSpellBridge.cast(this.boss, target, action, spellLevel);
            if (!cast) {
                if (action == WinefoxBossSpellAction.MODIFIED_TELEPORT) {
                    cast = this.boss.teleportAwayFrom(target, 8.0D);
                } else if (action != WinefoxBossSpellAction.HEAL
                        && action != WinefoxBossSpellAction.COUNTERSPELL
                        && action != WinefoxBossSpellAction.VOID_PHASE
                        && action != WinefoxBossSpellAction.ECHOING_STRIKES) {
                    this.boss.performRangedAttack(target, 1.0F);
                    return true;
                }
            }
            if (cast) {
                if (finishAnimation) {
                    this.boss.triggerCastFinishAnimation(action);
                } else {
                    this.boss.triggerCastStartAnimation(action);
                }
            }
            return cast;
        }

        private boolean startOrCastAction(LivingEntity target,
                                          WinefoxBossSpellAction action, int spellLevel) {
            int castTime = WinefoxBossSpellBridge.getCastTimeTicks(this.boss, action, spellLevel);
            if (castTime <= 0) {
                return this.castAction(target, action, spellLevel);
            }
            this.pendingAction = action;
            this.pendingSpellLevel = spellLevel;
            this.pendingCastTicks = castTime;
            this.boss.triggerCastStartAnimation(action);
            return false;
        }

        private boolean tickPendingCast(LivingEntity target) {
            if (this.pendingAction == null) {
                return false;
            }
            if (--this.pendingCastTicks <= 0) {
                WinefoxBossSpellAction action = this.pendingAction;
                int spellLevel = this.pendingSpellLevel;
                this.pendingAction = null;
                this.pendingCastTicks = 0;
                if (this.castAction(target, action, spellLevel, true)) {
                    this.spellCooldowns.put(action, this.getSpellCooldown(action));
                }
            }
            return true;
        }

        private int randomSpellLevel(WinefoxBossSpellAction action) {
            return switch (action) {
                case COUNTERSPELL, VOID_PHASE -> 1;
                case MAGIC_SHOTGUN -> 1 + this.boss.random.nextInt(5);
                case SUMMON_SWORDS, MODIFIED_TELEPORT -> 4;
                default -> 5;
            };
        }

        private int getSpellCooldown(WinefoxBossSpellAction action) {
            int fallbackTicks;
            if (!this.phaseTwo) {
                fallbackTicks = switch (action) {
                    case MAGIC_MISSILE -> 40;
                    case MAGIC_ARROW -> 24;
                    case SUMMON_SWORDS -> 600;
                    case FIREBALL -> 48;
                    case LIGHTNING_LANCE -> 60;
                    case HEAL -> 120;
                    case MODIFIED_STARFALL -> 120;
                    case MAGIC_SHOTGUN -> 32;
                    case COUNTERSPELL -> 40;
                    default -> 80;
                };
                return WinefoxBossSpellBridge.getCooldownTicks(action, 0.2D, fallbackTicks);
            }
            fallbackTicks = switch (action) {
                case MAGIC_SHOTGUN -> 40;
                case ECHOING_STRIKES, SHADOW_SLASH -> 150;
                case MODIFIED_TELEPORT, DIVINE_SMITE -> 100;
                case HEAL -> 300;
                case FLAMING_STRIKE -> 160;
                default -> 120;
            };
            return WinefoxBossSpellBridge.getCooldownTicks(action, 0.5D, fallbackTicks);
        }

        private boolean isSpellReady(WinefoxBossSpellAction action) {
            return !this.spellCooldowns.containsKey(action);
        }

        private void tickCooldowns() {
            this.spellCooldowns.replaceAll((action, ticks) -> ticks - 1);
            this.spellCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
            if (this.spellDecisionCooldown > 0) {
                --this.spellDecisionCooldown;
            }
            if (this.meleeCooldown > 0) {
                --this.meleeCooldown;
            }
            if (this.escapeTeleportCheckCooldown > 0) {
                --this.escapeTeleportCheckCooldown;
            }
            if (this.modifiedTeleportCheckCooldown > 0) {
                --this.modifiedTeleportCheckCooldown;
            }
            if (this.counterspellCheckCooldown > 0) {
                --this.counterspellCheckCooldown;
            }
            if (this.spearCheckCooldown > 0) {
                --this.spearCheckCooldown;
            }
            if (this.voidPhaseCheckCooldown > 0) {
                --this.voidPhaseCheckCooldown;
            }
        }

        private void enterPhaseTwoCombat() {
            this.phaseTwo = true;
            this.burstAction = null;
            this.burstShots = 0;
            this.pendingAction = null;
            this.pendingCastTicks = 0;
            this.closeRangeTicks = 0;
            this.spellDecisionCooldown = 20;
            this.meleeCooldown = 0;
            this.spearCheckCooldown = 400;
            this.voidPhaseCheckCooldown = 400;
            this.refreshMovementPattern();
        }

        private void refreshMovementPattern() {
            this.movementRefreshCooldown = 40 + this.boss.random.nextInt(41);
            this.orbitDirection = this.boss.random.nextBoolean() ? 1.0D : -1.0D;
            this.preferredHeight = this.boss.random.nextDouble() * 3.0D;
        }

        private static double horizontalDistance(Entity first, Entity second) {
            double dx = first.getX() - second.getX();
            double dz = first.getZ() - second.getZ();
            return Math.sqrt(dx * dx + dz * dz);
        }

        private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
            Vec3 direction = to.subtract(from).multiply(1.0D, 0.0D, 1.0D);
            return direction.lengthSqr() < 1.0E-4D
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : direction.normalize();
        }
    }
}
