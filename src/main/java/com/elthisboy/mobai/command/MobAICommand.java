package com.elthisboy.mobai.command;

import com.elthisboy.mobai.MobAi;
import com.elthisboy.mobai.MobAi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.*;

/**
 * Registra todos los comandos del mod bajo /mobai
 *
 * Comandos disponibles:
 *   /mobai wave start <numero>    - Inicia una oleada
 *   /mobai wave stop              - Detiene la oleada activa
 *   /mobai wave list              - Lista oleadas disponibles
 *   /mobai wave reload            - Recarga los JSON de oleadas
 *   /mobai config set <param> <valor>  - Cambia un parámetro de config
 *   /mobai config get <param>          - Muestra el valor actual
 *   /mobai config reload               - Recarga el archivo de config
 *   /mobai config list                 - Lista los parámetros principales
 *   /mobai status                 - Estado actual del mod
 */
public class MobAICommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(
            literal("mobai")
            .requires(src -> src.hasPermissionLevel(2)) // OP level 2

            // ======= /mobai wave =======
            .then(literal("wave")

                .then(literal("start")
                    .then(argument("numero", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> {
                            int num = IntegerArgumentType.getInteger(ctx, "numero");
                            ServerCommandSource src = ctx.getSource();
                            ServerPlayerEntity player = src.getPlayer();
                            if (player == null) {
                                src.sendError(Text.translatable("mobai.command.no_player"));
                                return 0;
                            }
                            String result = MobAi.WAVE_MANAGER.startWave(num, player);
                            src.sendFeedback(() -> Text.literal(result), true);
                            return 1;
                        })
                    )
                )

                .then(literal("stop")
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerWorld world = src.getWorld();
                        String result = MobAi.WAVE_MANAGER.stopWave(world);
                        src.sendFeedback(() -> Text.literal(result), true);
                        return 1;
                    })
                )

                .then(literal("list")
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        var waveNums = MobAi.WAVE_MANAGER.getAvailableWaveNumbers();
                        if (waveNums.isEmpty()) {
                            src.sendFeedback(() -> Text.translatable("mobai.command.no_waves"), false);
                        } else {
                            src.sendFeedback(() -> Text.translatable("mobai.command.waves_title"), false);
                            for (int num : waveNums) {
                                var def = MobAi.WAVE_MANAGER.getWave(num);
                                int total = (def.mobs != null) ? def.mobs.stream().mapToInt(m -> m.count).sum() : 0;
                                if (def.hasBoss) total += 1;
                                final int finalNum = num;
                                final int finalTotal = total;
                                src.sendFeedback(() -> Text.literal(
                                    Text.translatable("mobai.cmd.wave_entry", finalNum, def.name, finalTotal).getString()
                                    + (def.hasBoss ? ", §4BOSS§7" : "") + ")"
                                ), false);
                            }
                        }
                        return 1;
                    })
                )

                .then(literal("info")
                    .then(argument("numero", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> {
                            int num = IntegerArgumentType.getInteger(ctx, "numero");
                            ServerCommandSource src = ctx.getSource();
                            var def = MobAi.WAVE_MANAGER.getWave(num);
                            if (def == null) {
                                src.sendError(Text.translatable("mobai.wave.not_found", num, ""));
                                return 0;
                            }
                            src.sendFeedback(() -> Text.literal("§6=== " + def.name + " ==="), false);
                            src.sendFeedback(() -> Text.translatable("mobai.cmd.info_message", def.startMessage), false);
                            src.sendFeedback(() -> Text.translatable("mobai.cmd.info_duration", def.durationSeconds), false);
                            src.sendFeedback(() -> Text.translatable("mobai.cmd.info_water", def.spawnInWater), false);
                            src.sendFeedback(() -> Text.translatable("mobai.cmd.info_boss", def.hasBoss, def.hasBoss ? " (" + def.bossName + ")" : ""), false);
                            if (def.mobs != null) {
                                src.sendFeedback(() -> Text.translatable("mobai.cmd.info_groups"), false);
                                for (var mob : def.mobs) {
                                    src.sendFeedback(() -> Text.literal("  §e• §f" + mob.toString()), false);
                                }
                            }
                            return 1;
                        })
                    )
                )

                .then(literal("reload")
                    .executes(ctx -> {
                        MobAi.WAVE_MANAGER.loadWaves();
                        int count = MobAi.WAVE_MANAGER.getWaveCount();
                        ctx.getSource().sendFeedback(() ->
                            Text.translatable("mobai.waves.reloaded", count), true);
                        return 1;
                    })
                )

                .then(literal("next")
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerPlayerEntity player = src.getPlayer();
                        if (player == null) { src.sendError(Text.translatable("mobai.command.no_player")); return 0; }
                        var mgr = MobAi.WAVE_MANAGER;
                        if (mgr.isWaveActive()) {
                            src.sendError(Text.translatable("mobai.wave.already_active"));
                            return 0;
                        }
                        int next = mgr.getLastCompletedWave() + 1;
                        if (next <= 0) next = 1;
                        String result = mgr.startWave(next, player);
                        src.sendFeedback(() -> Text.literal(result), true);
                        return 1;
                    })
                )

                // /mobai wave series <inicio> <fin>
                .then(literal("series")
                    .then(argument("inicio", IntegerArgumentType.integer(1, 100))
                        .then(argument("fin", IntegerArgumentType.integer(1, 100))
                            .executes(ctx -> {
                                int inicio = IntegerArgumentType.getInteger(ctx, "inicio");
                                int fin    = IntegerArgumentType.getInteger(ctx, "fin");
                                ServerCommandSource src = ctx.getSource();
                                ServerPlayerEntity player = src.getPlayer();
                                if (player == null) {
                                    src.sendError(Text.translatable("mobai.command.no_player"));
                                    return 0;
                                }
                                String result = MobAi.WAVE_MANAGER.startSeries(inicio, fin, player);
                                src.sendFeedback(() -> Text.literal(result), true);
                                return 1;
                            })
                        )
                    )
                )

                // /mobai wave series status — muestra el estado de la serie activa
                .then(literal("series-status")
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        var mgr = MobAi.WAVE_MANAGER;
                        if (!mgr.isSeriesActive()) {
                            src.sendFeedback(() -> Text.translatable("mobai.series.none"), false);
                        } else {
                            String status = mgr.isWaveActive()
                                ? "§aen curso (oleada " + mgr.getCurrentWaveNumber() + ")"
                                : Text.translatable("mobai.cmd.series_between", mgr.getLastCompletedWave()).getString();
                            src.sendFeedback(() -> Text.literal(
                                "§6Serie activa: " + status
                                + " §7/ fin en oleada §e" + mgr.getSeriesEndWave()
                            ), false);
                        }
                        return 1;
                    })
                )
            )

            // ======= /mobai config =======
            .then(literal("config")

                .then(literal("set")
                    .then(argument("parametro", StringArgumentType.word())
                        .then(argument("valor", StringArgumentType.string())
                            .executes(ctx -> {
                                String param = StringArgumentType.getString(ctx, "parametro");
                                String value = StringArgumentType.getString(ctx, "valor");
                                ServerCommandSource src = ctx.getSource();

                                boolean ok = MobAi.CONFIG.setField(param, value);
                                if (ok) {
                                    src.sendFeedback(() -> Text.literal(
                                        Text.translatable("mobai.config.set_ok", param, value).getString()
                                    ), true);
                                } else {
                                    src.sendError(Text.literal(
                                        Text.translatable("mobai.config.set_fail", param).getString()
                                    ));
                                }
                                return ok ? 1 : 0;
                            })
                        )
                    )
                )

                .then(literal("get")
                    .then(argument("parametro", StringArgumentType.word())
                        .executes(ctx -> {
                            String param = StringArgumentType.getString(ctx, "parametro");
                            String value = MobAi.CONFIG.getField(param);
                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("mobai.cmd.config_get", param, value), false);
                            return 1;
                        })
                    )
                )

                .then(literal("reload")
                    .executes(ctx -> {
                        MobAi.CONFIG = MobAi.CONFIG.loadOrCreate();
                        ctx.getSource().sendFeedback(() ->
                            Text.translatable("mobai.config.reloaded"), true);
                        return 1;
                    })
                )

                .then(literal("list")
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        var c = MobAi.CONFIG;
                        src.sendFeedback(() -> Text.translatable("mobai.command.config_title"), false);
                        src.sendFeedback(() -> Text.translatable("mobai.command.config_zombies"), false);
                        src.sendFeedback(() -> Text.literal("  §fzombieClimbHeight §7= §e" + c.zombieClimbHeight), false);
                        src.sendFeedback(() -> Text.literal("  §fzombieBreakWood §7= §e" + c.zombieBreakWood), false);
                        src.sendFeedback(() -> Text.literal("  §fzombieBreakSpeed §7= §e" + c.zombieBreakSpeed), false);
                        src.sendFeedback(() -> Text.literal("  §fzombieDamageMultiplier §7= §e" + c.zombieDamageMultiplier), false);
                        src.sendFeedback(() -> Text.literal("  §fzombieMemoryRange §7= §e" + c.zombieMemoryRange), false);
                        src.sendFeedback(() -> Text.literal("  §fzombieSpeed §7= §e" + c.zombieSpeed), false);
                        src.sendFeedback(() -> Text.literal("  §fzombieCanSwim §7= §e" + c.zombieCanSwim), false);
                        src.sendFeedback(() -> Text.literal("  §fzombieBonusHealth §7= §e" + c.zombieBonusHealth), false);
                        src.sendFeedback(() -> Text.literal("§e--- Esqueletos ---"), false);
                        src.sendFeedback(() -> Text.literal("  §fskeletonBreakBlocks §7= §e" + c.skeletonBreakBlocks), false);
                        src.sendFeedback(() -> Text.literal("  §fskeletonBreakLevel §7= §e" + c.skeletonBreakLevel), false);
                        src.sendFeedback(() -> Text.literal("  §fskeletonBreakSpeed §7= §e" + c.skeletonBreakSpeed), false);
                        src.sendFeedback(() -> Text.literal("  §fskeletonDamageMultiplier §7= §e" + c.skeletonDamageMultiplier), false);
                        src.sendFeedback(() -> Text.literal("  §fskeletonMemoryRange §7= §e" + c.skeletonMemoryRange), false);
                        src.sendFeedback(() -> Text.literal("  §fskeletonSpeed §7= §e" + c.skeletonSpeed), false);
                        src.sendFeedback(() -> Text.literal("  §fskeletonShootRange §7= §e" + c.skeletonShootRange), false);
                        src.sendFeedback(() -> Text.literal("§e--- Global / Oleadas ---"), false);
                        src.sendFeedback(() -> Text.literal("  §fglobalHealthMultiplier §7= §e" + c.globalHealthMultiplier), false);
                        src.sendFeedback(() -> Text.literal("  §fglobalDamageMultiplier §7= §e" + c.globalDamageMultiplier), false);
                        src.sendFeedback(() -> Text.literal("  §fwaveSpawnRadius §7= §e" + c.waveSpawnRadius), false);
                        src.sendFeedback(() -> Text.literal("  §fwaveAnnounce §7= §e" + c.waveAnnounce), false);
                        src.sendFeedback(() -> Text.literal("§e--- Mini Boss ---"), false);
                        src.sendFeedback(() -> Text.literal("  §fminiBossHealthMultiplier §7= §e" + c.miniBossHealthMultiplier), false);
                        src.sendFeedback(() -> Text.literal("  §fminiBossDamageMultiplier §7= §e" + c.miniBossDamageMultiplier), false);
                        src.sendFeedback(() -> Text.literal("  §fminiBossSpeedMultiplier §7= §e" + c.miniBossSpeedMultiplier), false);
                        return 1;
                    })
                )
            )

            // ======= /mobai status =======
            .then(literal("status")
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    src.sendFeedback(() -> Text.literal("§6=== Estado MobAI ==="), false);
                    src.sendFeedback(() -> Text.literal(
                        Text.translatable("mobai.cmd.status_wave", MobAi.WAVE_MANAGER.isWaveActive() ? Text.translatable("mobai.cmd.status_yes", MobAi.WAVE_MANAGER.getCurrentWaveNumber()).getString() : Text.translatable("mobai.cmd.status_no").getString()).getString()
                    ), false);
                    src.sendFeedback(() -> Text.literal(
                        Text.translatable("mobai.cmd.status_count", MobAi.WAVE_MANAGER.getWaveCount()).getString()
                    ), false);
                    src.sendFeedback(() -> Text.literal(
                        Text.translatable("mobai.cmd.status_config", MobAi.CONFIG.zombieClimbHeight, MobAi.CONFIG.globalHealthMultiplier, MobAi.CONFIG.globalDamageMultiplier).getString()
                    ), false);
                    boolean hasObj = MobAi.CONFIG.objectiveX != Integer.MIN_VALUE;
                    src.sendFeedback(() -> Text.translatable(
                        hasObj ? "mobai.cmd.status_objective"
                               : "mobai.cmd.status_no_objective",
                        hasObj ? MobAi.CONFIG.objectiveX : 0,
                        hasObj ? MobAi.CONFIG.objectiveY : 0,
                        hasObj ? MobAi.CONFIG.objectiveZ : 0
                    ), false);
                    return 1;
                })
            )

            // ======= /mobai help =======
            .then(literal("help")
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    src.sendFeedback(() -> Text.translatable("mobai.help.title"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.wave_start"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.wave_stop"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.wave_list"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.wave_info"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.wave_reload"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.wave_next"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.wave_series"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.wave_series_status"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.config_list"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.config_set"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.config_get"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.config_reload"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.status"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.config_file"), false);
                    src.sendFeedback(() -> Text.translatable("mobai.help.waves_file"), false);
                    return 1;
                })
            )
        );

        MobAi.LOGGER.info("[MobAI] Comandos /mobai registrados.");
    }
}
