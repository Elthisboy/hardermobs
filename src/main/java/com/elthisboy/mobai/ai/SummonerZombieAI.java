package com.elthisboy.mobai.ai;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Lógica del Zombie Invocador.
 * Identifica "§5✦ Summoner" en el nombre.
 * Al morir spawnea N mini-zombies (baby) alrededor.
 */
public class SummonerZombieAI {

    public static boolean isSummoner(ZombieEntity entity) {
        var name = entity.getCustomName();
        return name != null && name.getString().contains("Summoner");
    }

    public static void onDeath(ZombieEntity entity, ServerWorld world, int summonCount) {
        Random rng = new Random();

        // Efecto visual de invocación
        world.spawnParticles(ParticleTypes.PORTAL,
            entity.getX(), entity.getY() + 0.5, entity.getZ(),
            20, 0.5, 0.8, 0.5, 0.1);
        world.playSound(null, entity.getBlockPos(),
            SoundEvents.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.HOSTILE, 1.0f, 0.5f);

        // Spawnear mini-zombies alrededor
        for (int i = 0; i < summonCount; i++) {
            double angle = (i / (double) summonCount) * Math.PI * 2;
            double dist = 1.5 + rng.nextDouble();
            BlockPos spawnPos = new BlockPos(
                (int)(entity.getX() + Math.cos(angle) * dist),
                (int) entity.getY(),
                (int)(entity.getZ() + Math.sin(angle) * dist)
            );

            ZombieEntity mini = EntityType.ZOMBIE.create(world, e -> {}, spawnPos,
                SpawnReason.MOB_SUMMONED, false, false);
            if (mini == null) continue;

            mini.setBaby(true);
            mini.refreshPositionAndAngles(
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                rng.nextFloat() * 360f, 0f);
            mini.setCustomName(Text.translatable("mobai.mob.mini_summoned"));
            mini.setCustomNameVisible(false);

            // Hereda el target del invocador
            if (entity.getTarget() != null) mini.setTarget(entity.getTarget());

            world.spawnEntity(mini);
        }
    }
}
