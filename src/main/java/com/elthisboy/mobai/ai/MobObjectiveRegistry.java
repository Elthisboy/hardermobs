package com.elthisboy.mobai.ai;

import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro estático de coordenadas objetivo por mob UUID.
 * Gestiona dos fases:
 *   PHASE_OBJECTIVE → avanzar al objetivo principal (pueblo)
 *   PHASE_RALLY     → llegó al objetivo, ahora merodea en el punto de rally
 */
public class MobObjectiveRegistry {

    public static final int PHASE_OBJECTIVE = 0;
    public static final int PHASE_RALLY     = 1;

    public record Objective(
        int x, int y, int z, double speed,   // objetivo principal
        int rallyX, int rallyY, int rallyZ,   // punto de rally (MIN_VALUE = no hay)
        int rallyRadius                        // radio de merodeo
    ) {
        public boolean hasRally() {
            return rallyX != Integer.MIN_VALUE;
        }
    }

    public record MobState(Objective objective, int phase,
                           int wanderX, int wanderY, int wanderZ) {}

    private static final Map<UUID, Objective>  OBJECTIVES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer>    PHASES     = new ConcurrentHashMap<>();
    private static final Map<UUID, int[]>      WANDER_TGT = new ConcurrentHashMap<>();

    public static void set(UUID uuid, int x, int y, int z, double speed,
                            int rallyX, int rallyY, int rallyZ, int rallyRadius) {
        OBJECTIVES.put(uuid, new Objective(x, y, z, speed, rallyX, rallyY, rallyZ, rallyRadius));
        PHASES.put(uuid, PHASE_OBJECTIVE);
        WANDER_TGT.remove(uuid);
    }

    // Sobrecarga sin rally
    public static void set(UUID uuid, int x, int y, int z, double speed) {
        set(uuid, x, y, z, speed, Integer.MIN_VALUE, 0, 0, 8);
    }

    public static Objective get(UUID uuid) {
        return OBJECTIVES.get(uuid);
    }

    public static int getPhase(UUID uuid) {
        return PHASES.getOrDefault(uuid, PHASE_OBJECTIVE);
    }

    public static void setPhase(UUID uuid, int phase) {
        PHASES.put(uuid, phase);
    }

    /** Devuelve el punto de wander actual, generando uno nuevo si no existe */
    public static int[] getOrCreateWanderTarget(UUID uuid, int rallyX, int rallyY, int rallyZ,
                                                  int radius, java.util.Random rng) {
        int[] tgt = WANDER_TGT.get(uuid);
        if (tgt == null) {
            tgt = newWanderTarget(rallyX, rallyY, rallyZ, radius, rng);
            WANDER_TGT.put(uuid, tgt);
        }
        return tgt;
    }

    public static void refreshWanderTarget(UUID uuid, int rallyX, int rallyY, int rallyZ,
                                            int radius, java.util.Random rng) {
        WANDER_TGT.put(uuid, newWanderTarget(rallyX, rallyY, rallyZ, radius, rng));
    }

    private static int[] newWanderTarget(int cx, int cy, int cz, int radius, java.util.Random rng) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist  = 2 + rng.nextDouble() * (radius - 2);
        return new int[]{
            cx + (int)(Math.cos(angle) * dist),
            cy,
            cz + (int)(Math.sin(angle) * dist)
        };
    }

    public static void remove(UUID uuid) {
        OBJECTIVES.remove(uuid);
        PHASES.remove(uuid);
        WANDER_TGT.remove(uuid);
    }

    public static void clear() {
        OBJECTIVES.clear();
        PHASES.clear();
        WANDER_TGT.clear();
    }
}
