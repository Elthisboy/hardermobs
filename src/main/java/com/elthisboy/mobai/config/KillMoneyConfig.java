package com.elthisboy.mobai.config;

import com.elthisboy.mobai.MobAi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuración del sistema Kill Money.
 * Archivo: config/mobai/kill_money.json
 *
 * Controla si los jugadores ganan dinero al matar mobs,
 * y cuánto dinero otorga cada tipo de mob.
 */
public class KillMoneyConfig {

    private static final Path CONFIG_PATH = Paths.get("config", "mobai", "kill_money.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Activa o desactiva completamente el sistema de dinero por kills. */
    public boolean killMoneyEnabled = true;

    /**
     * Nombre exacto del objetivo del scoreboard donde se suma el dinero.
     * Debe coincidir exactamente con el nombre en el servidor (respeta may\u00fasculas).
     * Ejemplos: "money", "Money", "Coins", "dinero"
     */
    public String scoreboardObjective = "money";


    /**
     * Mapa de tipo de mob (ej: "minecraft:zombie") → cantidad de dinero que otorga.
     * Los mobs no listados aquí no otorgan dinero.
     */
    public Map<String, Integer> mobRewards = new LinkedHashMap<>();

    // =========================================================
    // DEFAULTS
    // =========================================================

    private static KillMoneyConfig createDefault() {
        KillMoneyConfig cfg = new KillMoneyConfig();
        cfg.killMoneyEnabled = true;

        // Mobs comunes / pasivos
        cfg.mobRewards.put("minecraft:bat",           1);
        cfg.mobRewards.put("minecraft:tadpole",       1);
        cfg.mobRewards.put("minecraft:cod",           2);
        cfg.mobRewards.put("minecraft:salmon",        2);
        cfg.mobRewards.put("minecraft:cat",           3);
        cfg.mobRewards.put("minecraft:chicken",       3);
        cfg.mobRewards.put("minecraft:frog",          3);
        cfg.mobRewards.put("minecraft:rabbit",        3);
        cfg.mobRewards.put("minecraft:squid",         3);
        cfg.mobRewards.put("minecraft:tropical_fish", 3);
        cfg.mobRewards.put("minecraft:armadillo",     5);
        cfg.mobRewards.put("minecraft:axolotl",       5);
        cfg.mobRewards.put("minecraft:bee",           5);
        cfg.mobRewards.put("minecraft:cow",           5);
        cfg.mobRewards.put("minecraft:endermite",     5);
        cfg.mobRewards.put("minecraft:fox",           5);
        cfg.mobRewards.put("minecraft:glow_squid",    5);
        cfg.mobRewards.put("minecraft:ocelot",        5);
        cfg.mobRewards.put("minecraft:parrot",        5);
        cfg.mobRewards.put("minecraft:pig",           5);
        cfg.mobRewards.put("minecraft:pufferfish",    5);
        cfg.mobRewards.put("minecraft:sheep",         5);
        cfg.mobRewards.put("minecraft:silverfish",    5);
        cfg.mobRewards.put("minecraft:turtle",        5);
        cfg.mobRewards.put("minecraft:wolf",          5);
        cfg.mobRewards.put("minecraft:dolphin",       8);
        cfg.mobRewards.put("minecraft:donkey",        8);
        cfg.mobRewards.put("minecraft:goat",          8);
        cfg.mobRewards.put("minecraft:llama",         8);
        cfg.mobRewards.put("minecraft:mooshroom",     8);
        cfg.mobRewards.put("minecraft:mule",          8);
        cfg.mobRewards.put("minecraft:trader_llama",  8);
        cfg.mobRewards.put("minecraft:allay",         10);
        cfg.mobRewards.put("minecraft:camel",         10);
        cfg.mobRewards.put("minecraft:horse",         10);
        cfg.mobRewards.put("minecraft:husk",          10);
        cfg.mobRewards.put("minecraft:panda",         10);
        cfg.mobRewards.put("minecraft:slime",         10);
        cfg.mobRewards.put("minecraft:strider",       10);
        cfg.mobRewards.put("minecraft:vex",           10);
        cfg.mobRewards.put("minecraft:zombie",        10);

        // Mobs hostiles medios
        cfg.mobRewards.put("minecraft:bogged",        15);
        cfg.mobRewards.put("minecraft:drowned",       15);
        cfg.mobRewards.put("minecraft:magma_cube",    15);
        cfg.mobRewards.put("minecraft:piglin",        15);
        cfg.mobRewards.put("minecraft:polar_bear",    15);
        cfg.mobRewards.put("minecraft:skeleton",      15);
        cfg.mobRewards.put("minecraft:sniffer",       15);
        cfg.mobRewards.put("minecraft:stray",         15);
        cfg.mobRewards.put("minecraft:zombie_villager",15);
        cfg.mobRewards.put("minecraft:zombified_piglin",15);
        cfg.mobRewards.put("minecraft:cave_spider",   20);
        cfg.mobRewards.put("minecraft:guardian",      20);
        cfg.mobRewards.put("minecraft:phantom",       20);
        cfg.mobRewards.put("minecraft:pillager",      20);
        cfg.mobRewards.put("minecraft:spider",        20);
        cfg.mobRewards.put("minecraft:witch",         20);
        cfg.mobRewards.put("minecraft:creeper",       25);
        cfg.mobRewards.put("minecraft:enderman",      25);
        cfg.mobRewards.put("minecraft:hoglin",        25);
        cfg.mobRewards.put("minecraft:zoglin",        25);

        // Mobs difíciles / jefes menores
        cfg.mobRewards.put("minecraft:blaze",         30);
        cfg.mobRewards.put("minecraft:ghast",         30);
        cfg.mobRewards.put("minecraft:shulker",       30);
        cfg.mobRewards.put("minecraft:vindicator",    30);
        cfg.mobRewards.put("minecraft:wither_skeleton",30);
        cfg.mobRewards.put("minecraft:breeze",        35);
        cfg.mobRewards.put("minecraft:piglin_brute",  40);
        cfg.mobRewards.put("minecraft:evoker",        50);
        cfg.mobRewards.put("minecraft:ravager",       75);
        cfg.mobRewards.put("minecraft:elder_guardian",100);

        // Jefes principales
        cfg.mobRewards.put("minecraft:warden",        200);
        cfg.mobRewards.put("minecraft:wither",        500);
        cfg.mobRewards.put("minecraft:ender_dragon",  1000);

        return cfg;
    }

    // =========================================================
    // CARGA / GUARDADO
    // =========================================================

    public static KillMoneyConfig loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    KillMoneyConfig loaded = GSON.fromJson(r, KillMoneyConfig.class);
                    if (loaded != null) {
                        MobAi.LOGGER.info("[MobAI] Kill Money config cargada ({} mobs configurados).",
                                loaded.mobRewards.size());
                        return loaded;
                    }
                }
            }
        } catch (IOException e) {
            MobAi.LOGGER.warn("[MobAI] Error leyendo kill_money.json: {}", e.getMessage());
        }
        KillMoneyConfig def = createDefault();
        def.save();
        MobAi.LOGGER.info("[MobAI] Kill Money config creada con valores por defecto.");
        return def;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            MobAi.LOGGER.error("[MobAI] Error guardando kill_money.json: {}", e.getMessage());
        }
    }

    /**
     * Devuelve la recompensa para un tipo de mob, o 0 si no está configurado.
     * @param entityId Identificador con namespace, ej: "minecraft:zombie"
     */
    public int getReward(String entityId) {
        return mobRewards.getOrDefault(entityId, 0);
    }
}
