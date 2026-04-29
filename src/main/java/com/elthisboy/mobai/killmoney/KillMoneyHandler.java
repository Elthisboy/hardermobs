package com.elthisboy.mobai.killmoney;

import com.elthisboy.mobai.MobAi;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Escucha los eventos de combate del servidor para detectar kills de jugadores
 * y suma la recompensa correspondiente al scoreboard "Money".
 *
 * Se activa solo si KillMoneyConfig.killMoneyEnabled = true.
 */
public class KillMoneyHandler {

    public static void register() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            // Solo nos interesan kills de jugadores sobre mobs
            if (!(entity instanceof ServerPlayerEntity player)) return;
            if (!(killedEntity instanceof MobEntity)) return;

            // Verificar que Kill Money esté activado en la config
            if (!MobAi.KILL_MONEY_CONFIG.killMoneyEnabled) return;

            // Obtener el identificador del mob muerto
            Identifier mobId = Registries.ENTITY_TYPE.getId(killedEntity.getType());
            String mobKey = mobId.toString(); // ej: "minecraft:zombie"

            int reward = MobAi.KILL_MONEY_CONFIG.getReward(mobKey);
            if (reward <= 0) return;

            // Nombre del objetivo leído desde la config (configurable)
            String objectiveName = MobAi.KILL_MONEY_CONFIG.scoreboardObjective;

            // Obtener el scoreboard y el objetivo configurado
            Scoreboard scoreboard = world.getScoreboard();
            ScoreboardObjective objective = scoreboard.getNullableObjective(objectiveName);

            if (objective == null) {
                MobAi.LOGGER.warn("[KillMoney] El objetivo '{}' no existe en el scoreboard. " +
                        "Verifica el campo \'scoreboardObjective\' en kill_money.json " +
                        "o créalo con: /scoreboard objectives add {} dummy", objectiveName, objectiveName);
                return;
            }

            // Sumar dinero al jugador
            scoreboard.getOrCreateScore(player, objective).incrementScore(reward);

            // Notificar al jugador en el actionbar
            player.sendMessage(
                Text.literal("+" + reward + " \uD83D\uDCB0")
                    .withColor(0xFFD700), // dorado
                true // true = actionbar
            );

            MobAi.LOGGER.debug("[KillMoney] {} recibió {} de {} ({})",
                    player.getName().getString(), reward, killedEntity.getType().getName().getString(), mobKey);
        });

        MobAi.LOGGER.info("[MobAI] KillMoneyHandler registrado.");
    }
}
