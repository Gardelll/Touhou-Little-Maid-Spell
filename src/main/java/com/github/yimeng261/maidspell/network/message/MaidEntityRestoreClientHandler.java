package com.github.yimeng261.maidspell.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.client.ClientMaidRemovalGuard;
import com.github.yimeng261.maidspell.mixin.accessor.EntityInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public final class MaidEntityRestoreClientHandler {
    private MaidEntityRestoreClientHandler() {
    }

    public static void handle(MaidEntityRestoreMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        ClientLevel level = minecraft.level;
        Entity entity = level.getEntity(message.entityId());
        if (entity != null && (!(entity instanceof EntityMaid) || !entity.getUUID().equals(message.uuid()))) return;
        if (entity == null) {
            entity = createEntity(level, message);
            if (entity == null) {
                return;
            }
            if (!(entity instanceof EntityMaid)) return;
            level.addEntity(entity);
        }

        ClientMaidRemovalGuard.markProtected(message.entityId(), message.uuid());
        ((EntityInvoker) entity).maidspell$invokeUnsetRemoved();
        message.applySyncedState((EntityMaid) entity);
        entity.moveTo(message.x(), message.y(), message.z(), message.yRot(), message.xRot());
        entity.setInvisible(false);
        entity.refreshDimensions();
    }

    static Entity createEntity(ClientLevel level, MaidEntityRestoreMessage message) {
        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(message.entityTypeId());
        if (entityType.isEmpty()) {
            return null;
        }

        Entity entity = entityType.get().create(level);
        if (entity == null) {
            return null;
        }

        entity.setId(message.entityId());
        entity.setUUID(message.uuid());
        if (!message.entityTag().isEmpty()) {
            entity.load(message.entityTag());
            entity.setId(message.entityId());
            entity.setUUID(message.uuid());
        }
        if (entity instanceof EntityMaid maid) message.applySyncedState(maid);
        entity.moveTo(message.x(), message.y(), message.z(), message.yRot(), message.xRot());
        return entity;
    }
}
