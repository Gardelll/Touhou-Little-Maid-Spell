package com.github.yimeng261.maidspell.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class WinefoxBossSpellBridge {
    public static final String STOP_CAST_ANIMATION = "#stop";
    private static final String IRONS_MOD_ID = "irons_spellbooks";
    private static final String DELEGATE_CLASS =
            "com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.WinefoxBossIronsSpellBridge";
    private static final Delegate DELEGATE = createDelegate();

    private WinefoxBossSpellBridge() {
    }

    public static AttributeSupplier.Builder addOptionalAttributes(AttributeSupplier.Builder builder) {
        DELEGATE.addAttributes(builder);
        return builder;
    }

    public static boolean cast(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target,
                               WinefoxBossSpellAction action, int spellLevel) {
        return DELEGATE.cast(boss, target, action, spellLevel);
    }

    public static boolean isCasting(@Nullable LivingEntity entity) {
        return entity != null && DELEGATE.isCasting(entity);
    }

    public static boolean hasVoidPhase(MagicalWinefoxBossEntity boss) {
        return DELEGATE.hasVoidPhase(boss);
    }

    public static int getCooldownTicks(WinefoxBossSpellAction action, double multiplier,
                                       int fallbackTicks) {
        return DELEGATE.getCooldownTicks(action, multiplier, fallbackTicks);
    }

    public static int getCastTimeTicks(MagicalWinefoxBossEntity boss,
                                       WinefoxBossSpellAction action, int spellLevel) {
        return DELEGATE.getCastTimeTicks(boss, action, spellLevel);
    }

    /**
     * Returns the Iron's Spellbooks animation path used by this boss action.
     * The path is intentionally kept as data here so the common boss entity does
     * not load optional Iron's Spellbooks classes when that mod is absent.
     */
    @Nullable
    public static String getCastAnimation(WinefoxBossSpellAction action, boolean finish) {
        return DELEGATE.getCastAnimation(action, finish);
    }

    private static Delegate createDelegate() {
        if (!ModList.get().isLoaded(IRONS_MOD_ID)) {
            return NoopDelegate.INSTANCE;
        }
        try {
            Class<?> type = Class.forName(DELEGATE_CLASS, true,
                    WinefoxBossSpellBridge.class.getClassLoader());
            return (Delegate) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            MaidSpellMod.LOGGER.error("Failed to initialize Magical Winefox spell combat", exception);
            return NoopDelegate.INSTANCE;
        }
    }

    public interface Delegate {
        void addAttributes(AttributeSupplier.Builder builder);

        boolean cast(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target,
                     WinefoxBossSpellAction action, int spellLevel);

        boolean isCasting(LivingEntity entity);

        boolean hasVoidPhase(MagicalWinefoxBossEntity boss);

        int getCooldownTicks(WinefoxBossSpellAction action, double multiplier, int fallbackTicks);

        int getCastTimeTicks(MagicalWinefoxBossEntity boss,
                             WinefoxBossSpellAction action, int spellLevel);

        @Nullable
        String getCastAnimation(WinefoxBossSpellAction action, boolean finish);
    }

    private enum NoopDelegate implements Delegate {
        INSTANCE;

        @Override
        public void addAttributes(AttributeSupplier.Builder builder) {
        }

        @Override
        public boolean cast(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target,
                            WinefoxBossSpellAction action, int spellLevel) {
            return false;
        }

        @Override
        public boolean isCasting(LivingEntity entity) {
            return false;
        }

        @Override
        public boolean hasVoidPhase(MagicalWinefoxBossEntity boss) {
            return false;
        }

        @Override
        public int getCooldownTicks(WinefoxBossSpellAction action, double multiplier,
                                    int fallbackTicks) {
            return fallbackTicks;
        }

        @Override
        public int getCastTimeTicks(MagicalWinefoxBossEntity boss,
                                    WinefoxBossSpellAction action, int spellLevel) {
            return 0;
        }

        @Override
        public String getCastAnimation(WinefoxBossSpellAction action, boolean finish) {
            return null;
        }
    }
}
