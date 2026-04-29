package com.elthisboy.mobai.config;

import com.elthisboy.mobai.MobAi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;

/**
 * Configuración central del mod MobAI.
 * Archivo: config/mobai/mobai_config.json
 * Comando: /mobai config set <param> <valor>
 */
public class MobAIConfig {

    private static final Path CONFIG_PATH = Paths.get("config", "mobai", "mobai_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ======= ZOMBIES =======
    public float   zombieClimbHeight        = 1.5f;
    public boolean zombieBreakWood          = true;
    /** Niveles: "wood", "stone", "stone_bricks", "deepslate" */
    public String  zombieBreakLevel         = "deepslate";
    /**
     * Ticks base para romper madera. Multiplicadores automáticos:
     *   stone        x2  | stone_bricks  x3.5  | deepslate  x6
     */
    public int     zombieBreakSpeed         = 60;
    public float   zombieDamageMultiplier   = 1.0f;
    public double  zombieMemoryRange        = 128.0;
    public float   zombieSpeed              = 0.23f;
    public boolean zombieCanSwim            = true;
    public float   zombieBonusHealth        = 0.0f;

    // ======= ESQUELETOS =======
    public boolean skeletonBreakBlocks      = true;
    /** Niveles: "wood", "stone", "stone_bricks", "deepslate" */
    public String  skeletonBreakLevel       = "deepslate";
    public int     skeletonBreakSpeed       = 80;
    public float   skeletonDamageMultiplier = 1.0f;
    public double  skeletonMemoryRange      = 128.0;
    public float   skeletonSpeed            = 0.25f;
    public float   skeletonShootRange       = 15.0f;

    // ======= ZOMBIE KAMIKAZE (adulto con TNT) =======
    public boolean kamikazeEnabled          = true;
    /** Radio de explosión en bloques */
    public float   kamikazeExplosionRadius  = 3.0f;
    /** Si la explosión rompe bloques del mundo */
    public boolean kamikazeBreaksBlocks     = false;
    /** Distancia al jugador que activa la cuenta regresiva */
    public float   kamikazeTriggerDistance  = 2.5f;
    /** Segundos de cuenta regresiva antes de explotar */
    public int     kamikazeCountdownSeconds = 2;

    // ======= MINI ZOMBIES - 3 SUB-TIPOS =======
    /** Sub-tipo 1: Baby zombie vanilla (pequeño y rápido, sin boost de stats) */
    public boolean miniVanillaEnabled       = true;

    /** Sub-tipo 2: Baby zombie boosteado (pequeño + stats mejorados) */
    public boolean miniBoostedEnabled       = true;
    public float   miniBoostedSpeedMult     = 1.3f;
    public float   miniBoostedDamageMult    = 1.2f;
    public float   miniBoostedHealthMult    = 0.6f;

    /** Sub-tipo 3: Baby zombie kamikaze (pequeño + explota al acercarse) */
    public boolean miniKamikazeEnabled      = true;
    public float   miniKamikazeExplosion    = 2.0f;  // radio menor que el adulto
    public float   miniKamikazeTriggerDist  = 2.0f;

    // ======= GLOBAL =======
    public float   globalHealthMultiplier   = 1.0f;
    public float   globalDamageMultiplier   = 1.0f;
    public boolean globalCanSwim            = true;

    // ======= OBJETIVO GLOBAL (pueblo/base a defender) =======
    /**
     * Coordenadas X/Y/Z del objetivo que los mobs intentan alcanzar
     * cuando no tienen un jugador como target.
     * Representa el pueblo/base que deben atacar.
     * Valor Integer.MIN_VALUE = desactivado (comportamiento vanilla).
     * Se puede sobreescribir por oleada en el JSON con objectiveX/Y/Z.
     */
    public int    objectiveX     = Integer.MIN_VALUE;
    public int    objectiveY     = Integer.MIN_VALUE;
    public int    objectiveZ     = Integer.MIN_VALUE;
    /** Velocidad de avance hacia el objetivo (1.0 = normal) */
    public double objectiveSpeed = 1.0;

    // ======= OLEADAS =======
    public int     waveCooldownSeconds      = 30;
    /** Si true, al completar una oleada inicia la siguiente automáticamente tras waveCooldownSeconds */
    public boolean waveAutoNext              = false;
    public int     waveSpawnRadius          = 24;  // radio MÁXIMO de spawn
    /** Radio mínimo — mobs no spawnean MÁS CERCA que esto */
    public int     waveSpawnMinRadius        = 8;
    public boolean waveAnnounce             = true;

    // ======= DÍA/NOCHE =======
    /**
     * Al amanecer: todos los mobs de la oleada desaparecen (poof con partículas de fuego).
     * Si nightOnlyStart=true no se pueden iniciar oleadas de día.
     */
    public boolean nightOnlyStart           = true;
    public boolean despawnAtDawn            = true;
    /** Tick del mundo considerado "amanecer" (vanilla = 23000) */
    public long    dawnTick                 = 23000L;
    /** Tick del mundo considerado "anochecer" (vanilla = 13000) */
    public long    duskTick                 = 13000L;

    // ======= MINI BOSS =======
    public float   miniBossHealthMultiplier = 5.0f;
    public float   miniBossDamageMultiplier = 2.5f;
    public float   miniBossSpeedMultiplier  = 1.2f;

    // =========================================================

    public static MobAIConfig loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    MobAIConfig loaded = GSON.fromJson(r, MobAIConfig.class);
                    if (loaded != null) return loaded;
                }
            }
        } catch (IOException e) {
            MobAi.LOGGER.warn("[MobAI] Error leyendo config: {}", e.getMessage());
        }
        MobAIConfig def = new MobAIConfig();
        def.save();
        return def;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            MobAi.LOGGER.error("[MobAI] Error guardando config: {}", e.getMessage());
        }
    }

    public boolean setField(String name, String value) {
        try {
            var f = this.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Class<?> t = f.getType();
            if      (t == float.class)   f.set(this, Float.parseFloat(value));
            else if (t == double.class)  f.set(this, Double.parseDouble(value));
            else if (t == int.class)     f.set(this, Integer.parseInt(value));
            else if (t == long.class)    f.set(this, Long.parseLong(value));
            else if (t == boolean.class) f.set(this, Boolean.parseBoolean(value));
            else if (t == String.class)  f.set(this, value);
            else return false;
            save();
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        } catch (Exception e) {
            MobAi.LOGGER.warn("[MobAI] setField error {}: {}", name, e.getMessage());
            return false;
        }
    }

    public String getField(String name) {
        try {
            var f = this.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(this);
            return v != null ? v.toString() : "null";
        } catch (Exception e) {
            return "campo no encontrado";
        }
    }

    /** true si el tiempo actual del mundo es de noche */
    public boolean isNightTime(long worldTime) {
        long t = worldTime % 24000L;
        return t >= duskTick || t < dawnTick;
    }
}
