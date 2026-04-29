package com.elthisboy.mobai;

import com.elthisboy.mobai.command.MobAICommand;
import com.elthisboy.mobai.config.MobAIConfig;
import com.elthisboy.mobai.config.KillMoneyConfig;
import com.elthisboy.mobai.killmoney.KillMoneyHandler;
import com.elthisboy.mobai.wave.WaveManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobAi implements ModInitializer {
    public static final String MOD_ID = "MobAi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static MobAIConfig CONFIG;
    public static KillMoneyConfig KILL_MONEY_CONFIG;
    public static WaveManager WAVE_MANAGER;

    @Override
    public void onInitialize() {
        LOGGER.info("[MobAI] Inicializando mod de IA avanzada de mobs...");

        CONFIG = MobAIConfig.loadOrCreate();
        LOGGER.info("[MobAI] Configuracion cargada.");

        KILL_MONEY_CONFIG = KillMoneyConfig.loadOrCreate();
        LOGGER.info("[MobAI] Kill Money config cargada. Habilitado: {}", KILL_MONEY_CONFIG.killMoneyEnabled);

        WAVE_MANAGER = new WaveManager();
        WAVE_MANAGER.loadWaves();
        LOGGER.info("[MobAI] Oleadas cargadas: {} definiciones encontradas.", WAVE_MANAGER.getWaveCount());

        KillMoneyHandler.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MobAICommand.register(dispatcher);
        });

        LOGGER.info("[MobAI] Mod listo! Usa /mobai para empezar.");
    }
}