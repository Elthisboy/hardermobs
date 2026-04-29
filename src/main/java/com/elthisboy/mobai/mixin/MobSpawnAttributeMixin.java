package com.elthisboy.mobai.mixin;

import com.elthisboy.mobai.MobAi;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aplica atributos de config al spawn natural de zombies y esqueletos.
 *
 * IMPORTANTE MC 1.21.1: La firma real de MobEntity.initialize es:
 *   initialize(ServerWorldAccess, LocalDifficulty, SpawnReason, EntityData)
 * El mixin DEBE incluir EntityData o Mixin lanza InvalidInjectionException.
 */
@Mixin(MobEntity.class)
public class MobSpawnAttributeMixin {

    @Inject(method = "initialize", at = @At("RETURN"))
    private void onInitialize(ServerWorldAccess world, LocalDifficulty difficulty,
                               SpawnReason spawnReason, EntityData entityData,
                               CallbackInfoReturnable<?> cir) {
        MobEntity self = (MobEntity)(Object)this;
        var config = MobAi.CONFIG;
        if (config == null) return;

        // ======= ZOMBIE =======
        if (self instanceof ZombieEntity) {
            var speedAttr = self.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speedAttr != null && config.zombieSpeed != 0.23f) {
                speedAttr.setBaseValue(config.zombieSpeed);
            }

            var dmgAttr = self.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (dmgAttr != null && config.zombieDamageMultiplier != 1.0f) {
                dmgAttr.setBaseValue(dmgAttr.getBaseValue() * config.zombieDamageMultiplier);
            }

            if (config.zombieBonusHealth > 0) {
                var hpAttr = self.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                if (hpAttr != null) {
                    double newHp = hpAttr.getBaseValue() + config.zombieBonusHealth;
                    hpAttr.setBaseValue(newHp);
                    self.setHealth((float) newHp);
                }
            }

            // Siempre aplicar followRange — vanilla zombie = 35, queremos que sea más alto
            var followAttr = self.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            if (followAttr != null) {
                followAttr.setBaseValue(config.zombieMemoryRange);
            }
        }

        // ======= ESQUELETO =======
        if (self instanceof AbstractSkeletonEntity) {
            var speedAttr = self.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speedAttr != null && config.skeletonSpeed != 0.25f) {
                speedAttr.setBaseValue(config.skeletonSpeed);
            }

            var dmgAttr = self.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (dmgAttr != null && config.skeletonDamageMultiplier != 1.0f) {
                dmgAttr.setBaseValue(dmgAttr.getBaseValue() * config.skeletonDamageMultiplier);
            }

            // Siempre aplicar followRange — vanilla skeleton = 16
            var followAttr = self.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            if (followAttr != null) {
                followAttr.setBaseValue(config.skeletonMemoryRange);
            }
        }
    }
}
