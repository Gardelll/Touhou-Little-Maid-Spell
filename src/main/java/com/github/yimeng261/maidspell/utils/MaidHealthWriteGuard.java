package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.mixin.accessor.SynchedEntityDataAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * 女仆生命写入许可：只放行（女仆、生命数据项、血量）三者都对得上的那一笔写入，
 * 写入途中回调里对别的女仆或别的血量的写入照常受守卫检查。
 */
public final class MaidHealthWriteGuard {
    private static final ThreadLocal<Write> CURRENT = new ThreadLocal<>();

    private MaidHealthWriteGuard() {}

    private static final class Write {
        final EntityMaid maid;
        final SynchedEntityData.DataItem<?> item;
        final float health;
        boolean consumed;

        Write(EntityMaid maid, SynchedEntityData.DataItem<?> item, float health) {
            this.maid = maid;
            this.item = item;
            this.health = health;
        }
    }

    public static boolean permits(EntityMaid maid, SynchedEntityData.DataItem<?> item, float health) {
        Write write = CURRENT.get();
        return write != null && !write.consumed && write.maid == maid && write.item == item
                && Float.compare(write.health, health) == 0;
    }

    public static boolean consume(EntityMaid maid, SynchedEntityData.DataItem<?> item, float health) {
        if (!permits(maid, item, health)) return false;
        CURRENT.get().consumed = true;
        return true;
    }

    /** 写入饰品结算后的女仆生命；魂之书女仆只认伤害结算里的写入，这里不写。 */
    public static void write(EntityMaid maid, EntityDataAccessor<Float> accessor, float health, boolean force) {
        if (!SoulBookHealthProtection.isProtected(maid)) writeAuthorized(maid, accessor, health, force);
    }

    /** 带许可写入女仆生命，魂之书保护也放行；只给伤害结算、读档恢复和测试布置场景用。 */
    public static void writeAuthorized(EntityMaid maid, EntityDataAccessor<Float> accessor, float health, boolean force) {
        SynchedEntityData data = maid.getEntityData();
        var item = ((SynchedEntityDataAccessor) data).maidspell$getItemsById()[accessor.id()];
        Write previous = CURRENT.get();
        CURRENT.set(new Write(maid, item, health));
        try {
            data.set(accessor, health, force);
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
