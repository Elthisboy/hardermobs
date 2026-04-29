package com.elthisboy.mobai.wave;

import java.util.List;

/**
 * Representa la definición de una oleada cargada desde JSON.
 * Archivo: config/mobai/waves/waveN.json
 *
 * Soporte multi-lado (sides):
 *   Si "sides" está definido, la oleada spawneará mobs en múltiples zonas
 *   del mapa simultáneamente — cada side con sus propios spawnPoints,
 *   objetivo y rally. Si un side no define "mobs", hereda los del nivel raíz.
 *
 * Ejemplo mínimo de wave con 2 lados:
 * {
 *   "waveNumber": 1,
 *   "name": "wave.1.name",
 *   "mobs": [ { "type": "minecraft:zombie", "count": 5 } ],
 *   "sides": [
 *     {
 *       "spawnPoints": [{"x":76,"y":0,"z":-80}],
 *       "objectiveX": 66, "objectiveY": 0, "objectiveZ": -12,
 *       "rallyX": 72, "rallyY": 5, "rallyZ": 39, "rallyRadius": 10
 *     },
 *     {
 *       "spawnPoints": [{"x":-6,"y":3,"z":-86}],
 *       "objectiveX": 2, "objectiveY": 1, "objectiveZ": -44,
 *       "rallyX": 73, "rallyY": 5, "rallyZ": 44, "rallyRadius": 10,
 *       "mobs": [ { "type": "minecraft:zombie", "count": 5 } ]
 *     }
 *   ]
 * }
 */
public class WaveDefinition {

    public int waveNumber = 1;
    public String name = "Oleada 1";
    public String startMessage = "¡La oleada ha comenzado!";
    public String endMessage = "¡Oleada completada!";
    public List<MobGroup> mobs;
    public int durationSeconds = 300;
    public boolean spawnInWater = false;
    public boolean hasBoss = false;
    public String bossType = "";
    public String bossName = "";
    public int spawnRadiusOverride = -1;

    /**
     * Puntos de spawn del lado principal (legacy / sin sides).
     * Si "sides" está definido, este campo se ignora.
     */
    public List<SpawnPoint> spawnPoints = null;

    /** Título en pantalla al iniciar la oleada. */
    public String titleOnStart = null;
    public String subtitleOnStart = null;
    public String titleOnComplete = null;
    public String subtitleOnComplete = null;

    /** Comandos a ejecutar al completar. Usa %player% para el nombre del jugador. */
    public List<String> rewardCommands = null;

    /** Si true, inicia automáticamente la siguiente oleada tras el cooldown. */
    public boolean autoNextWave = false;

    /** Segundos de espera antes de la siguiente oleada (-1 = usar config global) */
    public int autoNextDelaySec = -1;

    /**
     * Objetivo principal (legacy / sin sides).
     * Si "sides" está definido, cada side define su propio objetivo.
     */
    public int objectiveX = Integer.MIN_VALUE;
    public int objectiveY = Integer.MIN_VALUE;
    public int objectiveZ = Integer.MIN_VALUE;
    public double objectiveSpeed = 1.0;

    /**
     * Rally principal (legacy / sin sides).
     */
    public int rallyX      = Integer.MIN_VALUE;
    public int rallyY      = Integer.MIN_VALUE;
    public int rallyZ      = Integer.MIN_VALUE;
    public int rallyRadius = 8;

    // =========================================================
    // MULTI-LADO
    // =========================================================

    /**
     * Lista de lados de la oleada.
     * Cuando está definida (no null), la oleada spawneará mobs en cada side
     * de forma independiente con sus propias coordenadas de spawn, objetivo y rally.
     *
     * Los campos objectiveX/Y/Z, rallyX/Y/Z y spawnPoints del nivel raíz
     * se ignoran si "sides" está presente.
     *
     * Si un side no define "mobs", hereda los mobs del nivel raíz de la wave.
     * Si un side no define "objectiveX", los mobs de ese side no tienen objetivo.
     */
    public List<WaveSide> sides = null;

    /**
     * Representa un lado/zona independiente dentro de una oleada.
     */
    public static class WaveSide {
        /**
         * Mobs propios de este lado.
         * Si es null, se usan los mobs del nivel raíz de la WaveDefinition.
         */
        public List<MobGroup> mobs = null;

        /** Puntos de spawn de este lado. */
        public List<SpawnPoint> spawnPoints = null;

        public int objectiveX = Integer.MIN_VALUE;
        public int objectiveY = Integer.MIN_VALUE;
        public int objectiveZ = Integer.MIN_VALUE;
        public double objectiveSpeed = 1.0;

        public int rallyX      = Integer.MIN_VALUE;
        public int rallyY      = Integer.MIN_VALUE;
        public int rallyZ      = Integer.MIN_VALUE;
        public int rallyRadius = 8;

        public boolean spawnInWater = false;
    }

    // =====================================================

    /** Punto de spawn fijo para mapas custom */
    public static class SpawnPoint {
        public int x = 0;
        public int y = 64;
        public int z = 0;
    }

    public static class MobGroup {
        /** ID del mob: "minecraft:zombie", "minecraft:skeleton", etc. */
        public String type = "minecraft:zombie";
        public int count = 5;
        public String displayName = "";
        public float healthMultiplier  = 1.0f;
        public float damageMultiplier  = 1.0f;
        public float speedMultiplier   = 1.0f;
        public boolean canBreakBlocks  = false;
        /** "wood", "stone", "stone_bricks", "deepslate" */
        public String breakLevel       = "wood";
        public boolean canClimb        = false;
        public float climbHeight       = 1.5f;
        public boolean canSwim         = false;
        public List<String> equipment  = null;

        public boolean isMiniZombieVanilla   = false;
        public boolean isMiniZombieBoosted   = false;
        public boolean isMiniZombieKamikaze  = false;
        public boolean isKamikaze = false;
        public boolean isHealer = false;
        public int healIntervalTicks = 40;
        public float healAmount = 3.0f;
        public float healRadius = 8.0f;
        public boolean isShielder = false;
        public boolean isSummoner = false;
        public int summonCount = 3;
        public boolean isBoss = false;

        @Override
        public String toString() {
            String subtype = isKamikaze ? "[KAMIKAZE]"
                : isMiniZombieKamikaze ? "[MINI-KAMIKAZE]"
                : isMiniZombieBoosted  ? "[MINI-BOOSTED]"
                : isMiniZombieVanilla  ? "[MINI]"
                : isHealer             ? "[HEALER]"
                : isShielder           ? "[SHIELDER]"
                : isSummoner           ? "[SUMMONER]"
                : isBoss               ? "[BOSS]"
                : "";
            return String.format("%dx %s %s (HP x%.1f, SPD x%.1f)",
                count, type, subtype, healthMultiplier, speedMultiplier);
        }
    }

    /**
     * Resuelve la lista de mobs efectiva para un side dado.
     * Si el side tiene sus propios mobs, los usa; si no, cae en los del nivel raíz.
     */
    public List<MobGroup> resolveMobsForSide(WaveSide side) {
        return (side.mobs != null && !side.mobs.isEmpty()) ? side.mobs : mobs;
    }

    /** ¿Tiene la wave múltiples lados definidos? */
    public boolean isMultiSide() {
        return sides != null && !sides.isEmpty();
    }

    @Override
    public String toString() {
        int total = (mobs != null) ? mobs.stream().mapToInt(m -> m.count).sum() : 0;
        if (hasBoss) total++;
        if (isMultiSide()) total *= sides.size();
        return String.format("Oleada %d: %s (%d mobs, %s lados)",
            waveNumber, name, total, isMultiSide() ? sides.size() : 1);
    }
}
