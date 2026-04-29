package com.elthisboy.mobai.ai;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro compartido entre ZombieAIMixin y SkeletonAIMixin.
 *
 * Almacena por BlockPos:
 *  - cuántos mobs lo están atacando (attackers)
 *  - cuántos ticks lleva siendo golpeado (breakTicks) — GLOBAL, no por mob
 *
 * El breakTicks global hace que el progreso sea real y compartido:
 * si 4 zombies atacan el mismo bloque, cada tick todos suman al mismo contador,
 * así que el bloque realmente cae 4x más rápido.
 *
 * Formula de daño por tick:
 *   Se incrementa breakTicks en 1 por cada mob que ataca (hasta maxContrib por mob),
 *   de forma que N mobs = N veces más rápido (con tope para no ser instantáneo).
 */
public final class BlockAttackRegistry {

    private static final class BlockData {
        int attackers  = 0;
        int breakTicks = 0;
    }

    private static final Map<BlockPos, BlockData> DATA = new ConcurrentHashMap<>();

    private BlockAttackRegistry() {}

    /** Registra que un mob está atacando este bloque este tick. */
    public static void registerAttack(BlockPos pos) {
        DATA.computeIfAbsent(pos, k -> new BlockData()).attackers =
            Math.max(1, DATA.get(pos).attackers); // asegurar que existe
    }

    /** Incrementa los ticks de ruptura del bloque (llamado una vez por mob por ciclo). */
    public static void addBreakProgress(BlockPos pos) {
        BlockData d = DATA.computeIfAbsent(pos, k -> new BlockData());
        d.attackers++;
        d.breakTicks++;
    }

    /** Devuelve los ticks acumulados de ruptura para este bloque. */
    public static int getBreakTicks(BlockPos pos) {
        BlockData d = DATA.get(pos);
        return d != null ? d.breakTicks : 0;
    }

    /** Devuelve el número de atacantes actuales. */
    public static int getAttackers(BlockPos pos) {
        BlockData d = DATA.get(pos);
        return d != null ? Math.max(1, d.attackers) : 1;
    }

    /** Elimina el registro de un bloque (fue roto o ya no se ataca). */
    public static void remove(BlockPos pos) {
        DATA.remove(pos);
    }

    /** Resetea el conteo de atacantes cada tick (se llama desde el tick del bloque). */
    public static void resetAttackerCount(BlockPos pos) {
        BlockData d = DATA.get(pos);
        if (d != null) d.attackers = 0;
    }

    /** Limpia todo. */
    public static void clear() {
        DATA.clear();
    }
}
