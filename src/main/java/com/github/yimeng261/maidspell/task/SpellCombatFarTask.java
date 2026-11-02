package com.github.yimeng261.maidspell.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;



/**
 * 法术战斗任务 - 统一的索敌和战斗管理
 * 实现IRangedAttackTask接口，为女仆提供法术攻击能力和混合战斗模式
 * 负责统一的目标搜索、验证和管理
 * 索敌系统：
 * - 使用近战任务的索敌方法（TLM 默认索敌条件加法术战斗过滤）
 * - 自动优先攻击距离最近的可见目标
 * - 简化代码并确保与其他攻击任务的一致性
 */
public class SpellCombatFarTask extends SpellCombatMeleeTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("maidspell", "spell_combat_far");
    private static final MutableComponent NAME = Component.translatable("task.maidspell.spell_combat_far");
    private static float SPELL_RANGE;

    public static void setSpellRange(Float range){
        SPELL_RANGE = range;
    }

    @Override
    public @NotNull ResourceLocation getUid() {
        return UID;
    }

    @Override
    public @NotNull MutableComponent getName() {
        return NAME;
    }

    @Override
    public boolean enableLookAndRandomWalk(@NotNull EntityMaid maid) {
        return super.enableLookAndRandomWalk(maid);
    }

    @Override
    public @NotNull ItemStack getIcon() {
        return MaidSpellItems.FAR_TASK_ICON.get().getDefaultInstance();
    }


    @Override
    protected BehaviorControl<EntityMaid> createStrafingBehavior(EntityMaid maid) {
        return new SpellStrafingTask();
    }

    /** 远程任务的站位距离带 {@code {min, max}}，与本任务走位一致，已按最大施法距离截断 */
    public static double[] farEngageBand() {
        double max = Config.maxSpellRange;
        return new double[]{Math.min(Config.farRange, max), Math.min(Config.farRange + 5, max)};
    }

    @Override
    protected BehaviorControl<EntityMaid> createSpellCastingBehavior(boolean ridePose) {
        return new FarSpellCombatBehavior(ridePose);
    }

    private static class FarSpellCombatBehavior extends SpellCombatBehavior {
        private SimplifiedSpellCaster currentSpellCaster;

        private FarSpellCombatBehavior(boolean ridePose) {
            super(ridePose);
        }

        @Override
        protected void start(net.minecraft.server.level.@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            currentSpellCaster = new SimplifiedSpellCaster(maid);

            LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            target = resolveIronsSpellbooksOwnerCastTarget(maid, target);
            if (SpellCombatMeleeTask.isValidSpellCombatTarget(maid, target)) {
                currentSpellCaster.setTarget(target);
            }
        }

        @Override
        protected void tick(net.minecraft.server.level.@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            if (currentSpellCaster != null) {
                // 确保目标同步 - 这是唯一的目标更新点
                LivingEntity currentTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                if (SpellCombatMeleeTask.isValidSpellCombatTarget(maid, currentTarget)) {
                    currentSpellCaster.setTarget(currentTarget);
                }
                currentSpellCaster.far_tick();
            }
        }

        @Override
        protected void stop(net.minecraft.server.level.@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
            // 停止和清理SpellCaster
            if (currentSpellCaster != null) {
                currentSpellCaster = null;
            }
        }
    }

    static class SpellStrafingTask extends SpellCombatMeleeTask.SpellStrafingTask {
        private final double optimalMinDistance = SimplifiedSpellCaster.FAR_RANGE; // 最佳最小距离
        private final double maxAttackDistance = SPELL_RANGE;
        private final double rangeRange = 5;

        public SpellStrafingTask() {
            super();
        }

        @Override
        protected void tick(@NotNull ServerLevel worldIn, EntityMaid maid, long gameTime) {
            // 如果女仆处于坐下状态，不执行走位逻辑
            if (maid.isOrderedToSit()) {
                return;
            }
            SpellCombatFarTask task = new SpellCombatFarTask();
            if (!task.hasSpellBook(maid)) {
                return;
            }

            maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent((target) -> {
                if(!SpellCombatMeleeTask.isValidSpellCombatTarget(maid, target)) {
                    stop(worldIn, maid, gameTime);
                    return;
                }
                double distance = maid.distanceTo(target);
                double optimalMaxDistance = optimalMinDistance + rangeRange; // 最佳最大距离

                // 如果在攻击距离内且能看到目标，开始计时
                if (distance < maxAttackDistance && maid.canSee(target)) {
                    ++this.strafingTime;
                } else {
                    this.strafingTime = -1;
                }

                // 随机改变走位方向，增加不可预测性
                if (this.strafingTime >= 20) {
                    if (maid.getRandom().nextFloat() < 0.3) {
                        this.strafingClockwise = !this.strafingClockwise;
                    }
                    if (maid.getRandom().nextFloat() < 0.3) {
                        this.strafingBackwards = !this.strafingBackwards;
                    }
                    this.strafingTime = 0;
                }

                // 执行走位逻辑
                if (this.strafingTime > -1) {
                    if (distance > optimalMaxDistance) {
                        this.strafingBackwards = false;
                    } else if (distance < optimalMinDistance) {
                        this.strafingBackwards = true;
                    }

                    float forwardSpeed = this.strafingBackwards ? -0.7F : 0.7F;
                    float strafeSpeed = this.strafingClockwise ? 0.7F : -0.7F;

                    maid.getMoveControl().strafe(forwardSpeed, strafeSpeed);
                    maid.setYRot(Mth.rotateIfNecessary(maid.getYRot(), maid.yHeadRot, 0.0F));

                }
                // 移除BehaviorUtils.lookAtEntity调用，让CombatLookControlTask统一管理视线
                // BehaviorUtils.lookAtEntity(maid, target);
            });
        }

    }
}
