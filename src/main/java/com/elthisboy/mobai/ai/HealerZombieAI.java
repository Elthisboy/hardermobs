package com.elthisboy.mobai.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Lógica del Zombie Curandero.
 * Identifica "§a✚ Healer" en el nombre.
 * - No ataca al jugador (se aleja)
 * - Cada healInterval ticks cura HP a mobs cercanos en el radio
 * - Emite partículas de corazón al curar
 */
public class HealerZombieAI {

    /** Tick-counter almacenado en el mixin usando la UUID como key */
    private static final java.util.Map<java.util.UUID, Integer> HEAL_TIMERS =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isHealer(ZombieEntity entity) {
        var name = entity.getCustomName();
        return name != null && name.getString().contains("Healer");
    }

    public static void tick(ZombieEntity entity, World world,
                             int healInterval, float healAmount, float healRadius) {
        if (world.isClient()) return;

        java.util.UUID id = entity.getUuid();
        int timer = HEAL_TIMERS.getOrDefault(id, 0) - 1;
        HEAL_TIMERS.put(id, timer);

        // Evitar atacar jugadores — limpiar target si es jugador
        if (entity.getTarget() instanceof net.minecraft.server.network.ServerPlayerEntity) {
            entity.setTarget(null);
        }

        if (timer > 0) return;

        // Reiniciar timer
        HEAL_TIMERS.put(id, healInterval);

        // Curar todos los mobs (no jugadores) en el radio
        Box area = entity.getBoundingBox().expand(healRadius);
        List<MobEntity> nearby = world.getEntitiesByClass(MobEntity.class, area,
            mob -> mob != entity && mob.isAlive() && mob.getHealth() < mob.getMaxHealth());

        if (nearby.isEmpty()) return;

        for (MobEntity mob : nearby) {
            mob.heal(healAmount);
        }

        // Partículas de corazón verdes (efecto positivo)
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                entity.getX(), entity.getY() + 1.5, entity.getZ(),
                6, 0.5, 0.5, 0.5, 0.1);
        }

        // Sonido de curación suave
        world.playSound(null, entity.getBlockPos(),
            SoundEvents.ENTITY_VILLAGER_AMBIENT,
            SoundCategory.HOSTILE, 0.4f, 1.5f);
    }

    public static void remove(java.util.UUID id) {
        HEAL_TIMERS.remove(id);
    }
}
