package com.elthisboy.mobai.mixin;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro compartido entre ZombieAIMixin y SkeletonAIMixin.
 *
 * Cuenta cuántos mobs están atacando cada BlockPos en cualquier momento.
 * Esto permite que el daño al bloque se acumule cuando hay varios atacantes:
 *
 *   ticksNeeded = baseTicks / sqrt(nAtacantes)
 *
 * Ejemplos con base = 60 ticks (3s):
 *   1 mob  → 60 ticks (3.0s)
 *   2 mobs → 42 ticks (2.1s)
 *   4 mobs → 30 ticks (1.5s)
 *   9 mobs → 20 ticks (1.0s)
 *  16 mobs → 15 ticks (0.75s)  ← mínimo ~10% del base
 */
public final class BlockAttackRegistry {

    private static final Map<BlockPos, Integer> ATTACKERS = new ConcurrentHashMap<>();

    private BlockAttackRegistry() {}

    public static void increment(BlockPos pos) {
        ATTACKERS.merge(pos, 1, Integer::sum);
    }

    public static void decrement(BlockPos pos) {
        ATTACKERS.computeIfPresent(pos, (k, v) -> v <= 1 ? null : v - 1);
    }

    /** Devuelve el número de atacantes actuales, mínimo 1. */
    public static int getAttackers(BlockPos pos) {
        return Math.max(1, ATTACKERS.getOrDefault(pos, 1));
    }

    /** Limpia todo (útil al cambiar de mundo o recargar). */
    public static void clear() {
        ATTACKERS.clear();
    }
}
