package com.elthisboy.mobai.wave;

import com.elthisboy.mobai.MobAi;
import com.elthisboy.mobai.ai.MobObjectiveRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WaveManager {

    private static final Path WAVES_DIR = Paths.get("config", "mobai", "waves");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<Integer, WaveDefinition> waves = new LinkedHashMap<>();

    // Estado oleada
    private boolean waveActive        = false;
    private int     currentWave       = 0;
    private int     totalSpawnedCount = 0;
    private int     lastCompletedWave = 0;  // para /mobai wave next
    private int     seriesEndWave     = -1; // oleada final de la serie (-1 = sin serie activa)

    // UUIDs de mobs de la oleada actual
    private final Set<UUID> waveEntityUUIDs = ConcurrentHashMap.newKeySet();
    // UUIDs que fueron vistos vivos al menos una vez (para distinguir "chunk descargado" de "muerto")
    private final Set<UUID> seenAliveUUIDs  = ConcurrentHashMap.newKeySet();

    // Mundo donde se spawneó la oleada — CRUCIAL para no confundir dimensiones
    private RegistryKey<World> waveWorldKey = null;

    // BossBar
    private ServerBossBar bossBar = null;

    // =========================================================
    // CARGA
    // =========================================================

    public void loadWaves() {
        waves.clear();
        try {
            Files.createDirectories(WAVES_DIR);
            createDefaultWavesIfMissing();
            try (var stream = Files.list(WAVES_DIR)) {
                for (Path file : stream.filter(p -> p.toString().endsWith(".json"))
                        .sorted().collect(Collectors.toList())) {
                    try (Reader r = Files.newBufferedReader(file)) {
                        WaveDefinition def = GSON.fromJson(r, WaveDefinition.class);
                        if (def != null && def.waveNumber > 0) {
                            waves.put(def.waveNumber, def);
                            MobAi.LOGGER.info("[MobAI] Oleada {} cargada: {}", def.waveNumber, def.name);
                        }
                    } catch (Exception e) {
                        MobAi.LOGGER.warn("[MobAI] Error leyendo {}: {}", file.getFileName(), e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            MobAi.LOGGER.error("[MobAI] Error cargando oleadas: {}", e.getMessage());
        }
    }

    public int getWaveCount()                      { return waves.size(); }
    public Set<Integer> getAvailableWaveNumbers()  { return waves.keySet(); }
    public WaveDefinition getWave(int n)           { return waves.get(n); }
    public boolean isWaveActive()                  { return waveActive; }
    public int getCurrentWaveNumber()              { return currentWave; }
    public int getLastCompletedWave()              { return lastCompletedWave; }

    /**
     * Devuelve las coordenadas objetivo de la oleada activa, o null si no hay objetivo.
     * Usado por ObjectiveGoalMixin para inyectar el goal en mobs que spawnean.
     */
    public net.minecraft.util.math.BlockPos getActiveObjective() {
        if (!waveActive) return null;
        var def = waves.get(currentWave);
        if (def != null && def.objectiveX != Integer.MIN_VALUE) {
            return new net.minecraft.util.math.BlockPos(def.objectiveX, def.objectiveY, def.objectiveZ);
        }
        return null;
    }
    public boolean isSeriesActive()                { return seriesEndWave > 0; }
    public int getSeriesEndWave()                  { return seriesEndWave; }

    /**
     * Inicia una serie de oleadas desde startWave hasta endWave.
     * Al completar cada oleada se encadena automáticamente la siguiente
     * con el cooldown configurado, hasta llegar a endWave.
     */
    public String startSeries(int startWave, int endWave, ServerPlayerEntity player) {
        if (startWave > endWave)
            return net.minecraft.text.Text.translatable("mobai.series.error_range", startWave, endWave).getString();
        if (waveActive)
            return net.minecraft.text.Text.translatable("mobai.wave.already_active").getString();

        // Verificar que todas las oleadas de la serie existen
        java.util.List<Integer> missing = new java.util.ArrayList<>();
        for (int i = startWave; i <= endWave; i++)
            if (!waves.containsKey(i)) missing.add(i);
        if (!missing.isEmpty())
            return net.minecraft.text.Text.translatable("mobai.series.missing_waves", missing, waves.keySet()).getString();

        seriesEndWave = endWave;
        var config = MobAi.CONFIG;
        ServerWorld world = (ServerWorld) player.getWorld();

        if (config.waveAnnounce) {
            int total = endWave - startWave + 1;
            broadcastWorld(world, net.minecraft.text.Text.translatable("mobai.series.start",
                    startWave, endWave, total));
        }

        return startWave(startWave, player);
    }

    // =========================================================
    // INICIAR
    // =========================================================

    public String startWave(int waveNumber, ServerPlayerEntity player) {
        WaveDefinition def = waves.get(waveNumber);
        if (def == null)
            return net.minecraft.text.Text.translatable("mobai.wave.not_found", waveNumber, waves.keySet()).getString();
        if (waveActive)
            return net.minecraft.text.Text.translatable("mobai.wave.already_active").getString();

        var config = MobAi.CONFIG;
        ServerWorld world = (ServerWorld) player.getWorld();

        if (config.nightOnlyStart && !config.isNightTime(world.getTimeOfDay()))
            return Text.translatable("mobai.wave.night_only").getString();

        BlockPos origin = player.getBlockPos();
        int radius = (def.spawnRadiusOverride > 0) ? def.spawnRadiusOverride : config.waveSpawnRadius;

        if (config.waveAnnounce)
            broadcastWorld(world, net.minecraft.text.Text.translatable("mobai.wave.start",
                resolveText(def.name), resolveText(def.startMessage)));

        // Title en pantalla (no molesto: fade rápido, stay corto)
        String resolvedName    = resolveString(def.name);
        String resolvedStart   = resolveString(def.startMessage);
        String resolvedTitleOn = def.titleOnStart    != null ? resolveString(def.titleOnStart)    : resolvedName.isEmpty() ? null : resolvedName;
        String resolvedSubOn   = def.subtitleOnStart != null ? resolveString(def.subtitleOnStart) : resolvedStart;
        broadcastTitle(world, resolvedTitleOn, resolvedSubOn, 10, 40, 20);

        waveEntityUUIDs.clear();
        seenAliveUUIDs.clear();
        waveWorldKey = world.getRegistryKey(); // guardar la dimensión

        int total = 0;

        if (def.isMultiSide()) {
            // ── Modo multi-lado: spawnear cada side de forma independiente ──
            for (WaveDefinition.WaveSide side : def.sides) {
                var sideDef = buildSideWaveDef(def, side);
                var sideMobs = def.resolveMobsForSide(side);
                if (sideMobs != null)
                    for (var group : sideMobs)
                        total += spawnGroup(group, sideDef, world, origin, radius, side.spawnInWater);
            }
            // Boss se spawna solo en el primer side definido
            if (def.hasBoss && !def.bossType.isEmpty() && !def.sides.isEmpty()) {
                var firstSide = def.sides.get(0);
                var bossWaveDef = buildSideWaveDef(def, firstSide);
                var boss = new WaveDefinition.MobGroup();
                boss.type = def.bossType; boss.count = 1; boss.isBoss = true;
                boss.displayName = resolveString(def.bossName);
                boss.healthMultiplier = config.miniBossHealthMultiplier;
                boss.damageMultiplier = config.miniBossDamageMultiplier;
                boss.speedMultiplier  = config.miniBossSpeedMultiplier;
                boss.canBreakBlocks = true; boss.breakLevel = "deepslate";
                boss.canClimb = true; boss.canSwim = true;
                total += spawnGroup(boss, bossWaveDef, world, origin, radius / 2, firstSide.spawnInWater);
            }
        } else {
            // ── Modo legacy: comportamiento original ──
            if (def.mobs != null)
                for (var group : def.mobs)
                    total += spawnGroup(group, def, world, origin, radius, def.spawnInWater);

            if (def.hasBoss && !def.bossType.isEmpty()) {
                var boss = new WaveDefinition.MobGroup();
                boss.type = def.bossType; boss.count = 1; boss.isBoss = true;
                boss.displayName = resolveString(def.bossName);
                boss.healthMultiplier = config.miniBossHealthMultiplier;
                boss.damageMultiplier = config.miniBossDamageMultiplier;
                boss.speedMultiplier  = config.miniBossSpeedMultiplier;
                boss.canBreakBlocks = true; boss.breakLevel = "deepslate";
                boss.canClimb = true; boss.canSwim = true;
                total += spawnGroup(boss, null, world, origin, radius / 2, def.spawnInWater);
            }
        }

        waveActive = true;
        currentWave = waveNumber;
        totalSpawnedCount = total;

        createBossBar(world, resolveString(def.name), total);
        MobAi.LOGGER.info("[MobAI] Oleada {} iniciada, {} mobs, mundo: {}", waveNumber, total, waveWorldKey.getValue());

        return net.minecraft.text.Text.translatable("mobai.wave.started", waveNumber, total).getString();
    }

    // =========================================================
    // DETENER (manual o amanecer) — siempre con poof
    // =========================================================

    public String stopWave(ServerWorld world) {
        if (!waveActive) return net.minecraft.text.Text.translatable("mobai.wave.not_active").getString();
        despawnWaveMobs(world);
        removeBossBar(world);
        waveActive    = false;
        currentWave   = 0;
        totalSpawnedCount = 0;
        waveWorldKey  = null;
        waveEntityUUIDs.clear();
        seenAliveUUIDs.clear();
        seriesEndWave = -1;
        return net.minecraft.text.Text.translatable("mobai.wave.stop").getString();
    }

    // =========================================================
    // DESPAWN con POOF
    // =========================================================

    public void despawnWaveMobs(ServerWorld world) {
        int count = 0;
        for (UUID uuid : waveEntityUUIDs) {
            Entity e = world.getEntity(uuid);
            if (e != null && e.isAlive()) {
                world.spawnParticles(ParticleTypes.POOF,
                    e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
                world.spawnParticles(ParticleTypes.SMOKE,
                    e.getX(), e.getY() + 1.0, e.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
                e.discard();
                count++;
            }
        }
        waveEntityUUIDs.clear();
        seenAliveUUIDs.clear();
        MobAi.LOGGER.info("[MobAI] {} mobs despawneados con poof.", count);
    }

    // =========================================================
    // BOSSBAR — tick llamado SOLO desde el mundo correcto
    // =========================================================

    /**
     * Solo procesa si el world pasado es EL MISMO donde se spawneó la oleada.
     * Esto evita el bug donde nether/end llaman getEntity() y devuelven null.
     */
    public void tickBossBar(ServerWorld world) {
        if (!waveActive || bossBar == null) return;

        // FILTRO CRÍTICO: ignorar dimensiones distintas al mundo de la oleada
        if (waveWorldKey == null || !world.getRegistryKey().equals(waveWorldKey)) return;

        // Contar mobs vivos — solo en el mundo correcto
        int alive = 0;
        Iterator<UUID> it = waveEntityUUIDs.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity e = world.getEntity(uuid);
            if (e == null) {
                if (seenAliveUUIDs.contains(uuid)) {
                    // Fue visto vivo antes y ahora es null = murio, eliminar
                    it.remove();
                    seenAliveUUIDs.remove(uuid);
                } else {
                    // Nunca fue visto = spawn pendiente o chunk descargado, asumir vivo
                    alive++;
                }
            } else if (!e.isAlive()) {
                it.remove(); // murio confirmado
                seenAliveUUIDs.remove(uuid);
            } else {
                seenAliveUUIDs.add(uuid); // marcar como visto vivo
                alive++;
            }
        }

        if (totalSpawnedCount <= 0) return;

        float progress = alive / (float) totalSpawnedCount;
        bossBar.setPercent(Math.max(0f, Math.min(1f, progress)));

        // Color según cuántos quedan
        BossBar.Color color = progress > 0.6f ? BossBar.Color.RED
                            : progress > 0.3f ? BossBar.Color.YELLOW
                            : BossBar.Color.GREEN;
        bossBar.setColor(color);

        String cleanName = waves.containsKey(currentWave)
            ? resolveString(waves.get(currentWave).name).replaceAll("§.", "")
            : "Oleada " + currentWave;
        bossBar.setName(Text.literal("§6⚔ " + cleanName + " §7— §e" + alive + " §7mobs"));

        // ¿Todos muertos?
        if (alive == 0) {
            waveComplete(world);
            return; // bossBar ya es null tras waveComplete, no continuar
        }

        // Asegurar que todos los jugadores ven la bossbar
        if (bossBar != null) {
            for (var player : world.getPlayers())
                bossBar.addPlayer(player);
        }
    }

    private void waveComplete(ServerWorld world) {
        var config        = MobAi.CONFIG;
        var def           = waves.get(currentWave);
        int completedWave = currentWave;
        int seriesEnd     = seriesEndWave; // guardar antes de resetear

        // Mensaje de oleada completada
        String endMsg = (def != null && def.endMessage != null && !def.endMessage.isEmpty())
            ? resolveString(def.endMessage) : "Wave cleared!";
        if (config.waveAnnounce)
            broadcastWorld(world, net.minecraft.text.Text.translatable("mobai.wave.complete", endMsg));

        // Title de completado — siempre mostrar, con fallback si no está en el JSON
        String titleComplete    = (def != null && def.titleOnComplete != null)    ? resolveString(def.titleOnComplete)    : "§a§l✔ WAVE CLEARED";
        String subtitleComplete = (def != null && def.subtitleOnComplete != null) ? resolveString(def.subtitleOnComplete) : "§7" + endMsg;
        broadcastTitle(world, titleComplete, subtitleComplete, 10, 60, 25);

        // Sonidos de victoria — world.playSound para todos los jugadores
        world.playSound(null, world.getSpawnPos(),
            net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
            net.minecraft.sound.SoundCategory.MASTER, 1.0f, 1.0f);
        world.playSound(null, world.getSpawnPos(),
            net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
            net.minecraft.sound.SoundCategory.MASTER, 1.0f, 1.2f);

        removeBossBar(world);
        MobAi.LOGGER.info("[MobAI] ✔ Oleada {} completada. (seriesEnd={})", completedWave, seriesEnd);
        lastCompletedWave = completedWave;

        // Recompensas
        if (def != null && def.rewardCommands != null && !def.rewardCommands.isEmpty())
            executeRewardCommands(world, def.rewardCommands);

        // Resetear estado de oleada
        waveActive        = false;
        currentWave       = 0;
        totalSpawnedCount = 0;
        waveWorldKey      = null;
        waveEntityUUIDs.clear();
        seenAliveUUIDs.clear();

        // ── Decidir si hay siguiente oleada ──────────────────────────────
        int nextWaveNum = completedWave + 1;

        // Caso 1: Serie activa → avanzar si no llegamos al final
        boolean inSeries = seriesEnd > 0;
        if (inSeries && completedWave >= seriesEnd) {
            // Serie terminada
            seriesEndWave = -1;
            if (config.waveAnnounce)
                broadcastWorld(world, net.minecraft.text.Text.translatable("mobai.series.complete"));
            return;
        }

        // Caso 2: Serie activa pero aún hay oleadas → forzar autoNext
        // Caso 3: No hay serie pero la oleada o config tienen autoNext activado
        boolean autoNext = inSeries
            || (def != null && def.autoNextWave)
            || config.waveAutoNext;

        if (!autoNext) return;
        if (!waves.containsKey(nextWaveNum)) {
            seriesEndWave = -1;
            broadcastWorld(world, net.minecraft.text.Text.translatable("mobai.wave.all_complete"));
            return;
        }

        // Programar la siguiente oleada con countdown
        int delaySecs = (def != null && def.autoNextDelaySec >= 0)
            ? def.autoNextDelaySec
            : config.waveCooldownSeconds;
        int delayTicks = Math.max(delaySecs * 20, 20); // mínimo 1 segundo

        WaveDefinition nextDef = waves.get(nextWaveNum);
        String seriesTag = inSeries
            ? " §7[§e" + nextWaveNum + "§7/§e" + seriesEnd + "§7]"
            : "";
        if (config.waveAnnounce)
            broadcastWorld(world, net.minecraft.text.Text.translatable("mobai.wave.next_incoming",
                nextDef.name.replaceAll("§.", ""), seriesTag, delaySecs));

        // scheduleNextWave es threadsafe — se llama desde el hilo del servidor
        scheduleNextWave(world, nextWaveNum, delayTicks);
    }

    /** Pendiente de ejecución con delay — usamos un contador de ticks manual */
    private int autoNextCountdown = -1;
    private int autoNextWaveNum   = -1;
    private RegistryKey<World> autoNextWorldKey = null;

    private void scheduleNextWave(ServerWorld world, int waveNum, int delayTicks) {
        autoNextCountdown = delayTicks;
        autoNextWaveNum   = waveNum;
        autoNextWorldKey  = world.getRegistryKey();
    }

    /**
     * Llamado desde DayNightWaveMixin cada tick.
     * Gestiona el countdown del auto-next wave.
     */
    public void tickAutoNext(ServerWorld world) {
        if (autoNextCountdown <= 0 || autoNextWaveNum < 0) return;
        if (!world.getRegistryKey().equals(autoNextWorldKey)) return;
        if (waveActive) { autoNextCountdown = -1; autoNextWaveNum = -1; return; } // ya hay oleada

        autoNextCountdown--;

        // Cuenta regresiva: mostrar cada segundo del 10 al 1
        int secsLeft = autoNextCountdown / 20;
        if (autoNextCountdown % 20 == 0 && secsLeft >= 1 && secsLeft <= 10) {
            broadcastWorld(world, net.minecraft.text.Text.translatable("mobai.wave.countdown", secsLeft));
        }

        if (autoNextCountdown <= 0) {
            // Buscar un jugador del mundo para iniciar la oleada
            var players = world.getPlayers();
            if (!players.isEmpty()) {
                String result = startWave(autoNextWaveNum, players.get(0));
                MobAi.LOGGER.info("[MobAI] Auto-next: {}", result);
            }
            autoNextCountdown = -1;
            autoNextWaveNum   = -1;
            autoNextWorldKey  = null;
        }
    }

    private void executeRewardCommands(ServerWorld world, java.util.List<String> commands) {
        var server = world.getServer();
        var commandManager = server.getCommandManager();
        var serverSource = server.getCommandSource();
        var players = world.getPlayers();

        for (String cmd : commands) {
            if (cmd == null || cmd.isBlank()) continue;
            try {
                if (cmd.contains("%player%")) {
                    // Ejecutar una vez por cada jugador en el mundo
                    for (var player : players) {
                        String resolved = cmd.replace("%player%", player.getName().getString());
                        MobAi.LOGGER.info("[MobAI] Reward cmd: {}", resolved);
                        commandManager.executeWithPrefix(serverSource, resolved);
                    }
                } else {
                    // Ejecutar una vez como servidor
                    MobAi.LOGGER.info("[MobAI] Reward cmd: {}", cmd);
                    commandManager.executeWithPrefix(serverSource, cmd);
                }
            } catch (Exception e) {
                MobAi.LOGGER.warn("[MobAI] Error ejecutando reward cmd '{}': {}", cmd, e.getMessage());
            }
        }
    }

    private void createBossBar(ServerWorld world, String waveName, int total) {
        removeBossBar(world);
        String cleanName = waveName.replaceAll("§.", "");  // waveName ya viene resuelto vía resolveString
        bossBar = new ServerBossBar(
            Text.literal("§6⚔ " + cleanName + " §7— §e" + total + " §7mobs"),
            BossBar.Color.RED,
            BossBar.Style.NOTCHED_10
        );
        bossBar.setPercent(1.0f);
        bossBar.setVisible(true);
        for (var player : world.getPlayers())
            bossBar.addPlayer(player);
    }

    private void removeBossBar(ServerWorld world) {
        if (bossBar != null) {
            bossBar.setVisible(false);
            if (world != null)
                for (var player : world.getPlayers())
                    bossBar.removePlayer(player);
            bossBar = null;
        }
    }

    // =========================================================
    // SPAWN
    // =========================================================

    private int spawnGroup(WaveDefinition.MobGroup group, WaveDefinition waveDef,
                            ServerWorld world,
                            BlockPos origin, int radius, boolean inWater) {
        boolean isMiniAny = group.isMiniZombieVanilla || group.isMiniZombieBoosted || group.isMiniZombieKamikaze;
        String baseType   = isMiniAny ? "minecraft:zombie" : group.type;

        Optional<EntityType<?>> optType = Registries.ENTITY_TYPE.getOrEmpty(Identifier.of(baseType));
        if (optType.isEmpty()) { MobAi.LOGGER.warn("[MobAI] Tipo desconocido: {}", baseType); return 0; }

        EntityType<?> entityType = optType.get();
        Random rng = new Random();
        int spawned = 0;

        // Usar spawnPoints fijos si están definidos en el JSON de la oleada
        java.util.List<WaveDefinition.SpawnPoint> fixedPoints =
            (waveDef != null && waveDef.spawnPoints != null && !waveDef.spawnPoints.isEmpty())
            ? waveDef.spawnPoints : null;

        for (int i = 0; i < group.count; i++) {
            BlockPos pos;
            if (fixedPoints != null) {
                // Distribuir cíclicamente entre los puntos definidos
                // Randomizar en un área 3x3 alrededor de cada punto para que no apilen
                WaveDefinition.SpawnPoint sp = fixedPoints.get(i % fixedPoints.size());
                int offsetX = rng.nextInt(7) - 3; // -3 a +3
                int offsetZ = rng.nextInt(7) - 3; // -3 a +3
                pos = new BlockPos(sp.x + offsetX, sp.y, sp.z + offsetZ);
            } else {
                pos = randomSpawnPos(world, origin, radius, inWater, rng);
            }
            if (pos == null) continue;

            Entity entity = entityType.create(world, e -> {}, pos, SpawnReason.COMMAND, false, false);
            if (entity == null) continue;

            entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                rng.nextFloat() * 360f, 0f);

            if (entity instanceof LivingEntity living) {
                applyAttributes(living, group);
                applyName(living, group);
                if (isMiniAny && living instanceof ZombieEntity z) z.setBaby(true);
                if ((group.isKamikaze || group.isMiniZombieKamikaze) && living instanceof MobEntity mob) {
                    mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.TNT));
                    mob.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0f);
                }
            }

            world.spawnEntity(entity);
            waveEntityUUIDs.add(entity.getUuid());

            // Marcar como persistente DESPUÉS de spawnear para que no se resetee
            if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                mob.setPersistent();
            }

            // Partículas en el punto de spawn
            world.spawnParticles(net.minecraft.particle.ParticleTypes.SPLASH,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                12, 0.4, 0.3, 0.4, 0.1);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                4, 0.3, 0.3, 0.3, 0.02);

            // Fix natación: activar swim flag
            if (group.canSwim && entity instanceof net.minecraft.entity.mob.MobEntity spawnedMob) {
                spawnedMob.setSwimming(true);
            }

            // Registrar objetivo + rally en MobObjectiveRegistry
            if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                BlockPos objPos = resolveObjective(waveDef);
                if (objPos != null) {
                    double speed = (waveDef != null && waveDef.objectiveX != Integer.MIN_VALUE)
                        ? waveDef.objectiveSpeed
                        : MobAi.CONFIG.objectiveSpeed;
                    // Rally point (si está definido en la oleada)
                    boolean hasRally = waveDef != null && waveDef.rallyX != Integer.MIN_VALUE;
                    int rx = hasRally ? waveDef.rallyX : Integer.MIN_VALUE;
                    int ry = hasRally ? waveDef.rallyY : 0;
                    int rz = hasRally ? waveDef.rallyZ : 0;
                    int rr = hasRally ? waveDef.rallyRadius : 8;
                    MobObjectiveRegistry.set(mob.getUuid(),
                        objPos.getX(), objPos.getY(), objPos.getZ(), speed,
                        rx, ry, rz, rr);
                }
            }

            spawned++;
        }
        return spawned;
    }

    private void applyAttributes(LivingEntity entity, WaveDefinition.MobGroup group) {
        var cfg = MobAi.CONFIG;

        float hpMult = group.healthMultiplier * cfg.globalHealthMultiplier;
        if (group.isMiniZombieBoosted)  hpMult *= cfg.miniBoostedHealthMult;
        if (group.isMiniZombieKamikaze) hpMult *= 0.5f;
        var hpAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hpAttr != null && hpMult != 1.0f) {
            double h = hpAttr.getBaseValue() * hpMult;
            hpAttr.setBaseValue(h);
            entity.setHealth((float) h);
        }

        float dmgMult = group.damageMultiplier * cfg.globalDamageMultiplier;
        if (group.isMiniZombieBoosted) dmgMult *= cfg.miniBoostedDamageMult;
        var dmgAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmgAttr != null && dmgMult != 1.0f)
            dmgAttr.setBaseValue(dmgAttr.getBaseValue() * dmgMult);

        float spdMult = group.speedMultiplier;
        if (group.isMiniZombieBoosted)  spdMult *= cfg.miniBoostedSpeedMult;
        if (group.isMiniZombieKamikaze) spdMult *= 1.4f;
        if (group.isMiniZombieVanilla)  spdMult *= 1.15f;
        var spdAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (spdAttr != null && spdMult != 1.0f)
            spdAttr.setBaseValue(spdAttr.getBaseValue() * spdMult);

        // Follow range alto: detectar jugadores desde muy lejos
        // Follow range alto para detectar jugadores desde lejos
        var followAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(128.0);
        }

        // Mobs en agua: activar swim + boost velocidad
        if (group.canSwim && entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.setSwimming(true);
            var swimSpd = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (swimSpd != null) swimSpd.setBaseValue(swimSpd.getBaseValue() * 1.8);
        }
    }

    private void applyName(LivingEntity living, WaveDefinition.MobGroup group) {
        String name = null;
        if      (group.isMiniZombieKamikaze) name = net.minecraft.text.Text.translatable("mobai.mob.mini_kamikaze").getString();
        else if (group.isKamikaze)           name = net.minecraft.text.Text.translatable("mobai.mob.kamikaze").getString();
        else if (group.isMiniZombieBoosted)  name = net.minecraft.text.Text.translatable("mobai.mob.mini_boosted").getString();
        else if (group.isMiniZombieVanilla)  name = net.minecraft.text.Text.translatable("mobai.mob.mini_vanilla").getString();
        else if (group.isHealer)             name = net.minecraft.text.Text.translatable("mobai.mob.healer").getString();
        else if (group.isShielder)           name = net.minecraft.text.Text.translatable("mobai.mob.shielder").getString();
        else if (group.isSummoner)           name = net.minecraft.text.Text.translatable("mobai.mob.summoner").getString();
        else if (group.isBoss)               name = net.minecraft.text.Text.translatable("mobai.mob.boss").getString();
        else if (!group.displayName.isEmpty()) name = group.displayName;
        if (name != null) { living.setCustomName(Text.literal(name)); living.setCustomNameVisible(true); }
    }

    /**
     * Genera una posición de spawn en un anillo entre minRadius y maxRadius.
     * Así los mobs nunca spawnean encima del jugador pero tampoco demasiado lejos.
     */
    /**
     * Construye un WaveDefinition temporal que hereda todos los campos de la wave raíz
     * pero sobreescribe spawnPoints, objectiveX/Y/Z, rallyX/Y/Z con los del side dado.
     * Esto permite reutilizar spawnGroup() sin modificarlo.
     */
    private WaveDefinition buildSideWaveDef(WaveDefinition base, WaveDefinition.WaveSide side) {
        WaveDefinition s = new WaveDefinition();
        // Herencia general
        s.waveNumber       = base.waveNumber;
        s.name             = base.name;
        s.durationSeconds  = base.durationSeconds;
        s.spawnInWater     = side.spawnInWater;
        s.hasBoss          = base.hasBoss;
        s.bossType         = base.bossType;
        s.bossName         = base.bossName;
        s.spawnRadiusOverride = base.spawnRadiusOverride;
        // Coordenadas propias del side
        s.spawnPoints      = side.spawnPoints;
        s.objectiveX       = side.objectiveX;
        s.objectiveY       = side.objectiveY;
        s.objectiveZ       = side.objectiveZ;
        s.objectiveSpeed   = side.objectiveSpeed;
        s.rallyX           = side.rallyX;
        s.rallyY           = side.rallyY;
        s.rallyZ           = side.rallyZ;
        s.rallyRadius      = side.rallyRadius;
        // Mobs: spawnGroup los obtiene externamente, pero los dejamos null aquí
        s.mobs             = null;
        return s;
    }

    /**
     * Determina las coordenadas objetivo para los mobs de esta oleada.
     * Prioridad: JSON de oleada → config global → null (sin objetivo).
     */
    private BlockPos resolveObjective(WaveDefinition waveDef) {
        // Objetivo específico de la oleada
        if (waveDef != null && waveDef.objectiveX != Integer.MIN_VALUE) {
            return new BlockPos(waveDef.objectiveX, waveDef.objectiveY, waveDef.objectiveZ);
        }
        // Objetivo global en config
        var cfg = MobAi.CONFIG;
        if (cfg.objectiveX != Integer.MIN_VALUE) {
            return new BlockPos(cfg.objectiveX, cfg.objectiveY, cfg.objectiveZ);
        }
        return null; // sin objetivo fijo
    }

    private BlockPos randomSpawnPos(ServerWorld world, BlockPos origin,
                                     int maxRadius, boolean inWater, Random rng) {
        var config = MobAi.CONFIG;
        int minR = Math.min(config.waveSpawnMinRadius, maxRadius - 2);
        minR = Math.max(minR, 4); // nunca menos de 4 bloques

        // Intentar hasta 8 posiciones antes de rendirse
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            // Distancia aleatoria entre minR y maxRadius (anillo)
            double dist = minR + rng.nextDouble() * (maxRadius - minR);
            int x = origin.getX() + (int)(Math.cos(angle) * dist);
            int z = origin.getZ() + (int)(Math.sin(angle) * dist);

            // Buscar Y válido en un rango vertical
            for (int dy = 10; dy >= -10; dy--) {
                BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
                BlockPos below = pos.down();
                boolean solidBelow = !world.getBlockState(below).isAir()
                    && world.getBlockState(below).isSolidBlock(world, below);
                boolean openAt = world.getBlockState(pos).isAir()
                    || (inWater && world.getBlockState(pos).getFluidState()
                            .isOf(net.minecraft.fluid.Fluids.WATER));
                if (solidBelow && openAt) return pos;
            }
        }
        // Fallback: posición directa al minRadius
        double angle = rng.nextDouble() * Math.PI * 2;
        return new BlockPos(
            origin.getX() + (int)(Math.cos(angle) * minR),
            origin.getY(),
            origin.getZ() + (int)(Math.sin(angle) * minR)
        );
    }

    /**
     * Resuelve un campo de texto del JSON de oleada.
     * Si el valor parece una clave de traducción (sin espacios y con puntos,
     * ej: "wave.1.name"), usa Text.translatable() para que los resource packs
     * puedan sobreescribirlo en sus archivos lang.
     * Si contiene espacios o no tiene puntos, se usa como texto literal.
     */
    private static net.minecraft.text.Text resolveText(String value) {
        if (value == null || value.isEmpty())
            return Text.literal("");
        // Es clave de traducción si no tiene espacios y contiene al menos un punto
        boolean isKey = !value.contains(" ") && value.contains(".");
        return isKey ? Text.translatable(value) : Text.literal(value);
    }

    /** Igual que resolveText pero devuelve el String ya resuelto (para APIs que piden String). */
    private static String resolveString(String value) {
        if (value == null) return "";
        boolean isKey = !value.contains(" ") && value.contains(".");
        return isKey ? Text.translatable(value).getString() : value;
    }

    private void broadcastWorld(ServerWorld world, net.minecraft.text.Text msg) {
        for (var p : world.getPlayers()) p.sendMessage(msg, false);
    }

    private void broadcastWorld(ServerWorld world, String msg) {
        broadcastWorld(world, Text.literal(msg));
    }

    /**
     * Muestra un title/subtitle a todos los jugadores del mundo.
     * fadeIn/stay/fadeOut en ticks. Solo se muestra si title no es null.
     */
    private void broadcastTitle(ServerWorld world, String title, String subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        if (title == null) return;
        net.minecraft.text.Text titleText    = Text.literal(title);
        net.minecraft.text.Text subText      = subtitle != null ? Text.literal(subtitle) : Text.literal("");
        for (var p : world.getPlayers()) {
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(fadeIn, stay, fadeOut));
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(subText));
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(titleText));
        }
    }

    // =========================================================
    // OLEADAS POR DEFECTO
    // =========================================================

    private void createDefaultWavesIfMissing() throws IOException {
        saveIfMissing(1, wave1()); saveIfMissing(2, wave2());
        saveIfMissing(3, wave3()); saveIfMissing(4, wave4());
        saveIfMissing(5, wave5()); saveIfMissing(6, wave6());
    }
    private void saveIfMissing(int n, WaveDefinition def) throws IOException {
        Path p = WAVES_DIR.resolve("wave" + n + ".json");
        if (!Files.exists(p)) try (Writer w = Files.newBufferedWriter(p)) { GSON.toJson(def, w); }
    }

    private WaveDefinition wave1() {
        WaveDefinition w = new WaveDefinition();
        w.waveNumber = 1; w.name = "wave.1.name";
        w.startMessage = "wave.1.startMessage";
        w.endMessage = "wave.1.endMessage"; w.durationSeconds = 180;
        var z = new WaveDefinition.MobGroup(); z.type = "minecraft:zombie"; z.count = 6;
        z.canBreakBlocks = true; z.breakLevel = "stone"; z.canClimb = true; z.climbHeight = 1.5f; z.canSwim = true;
        var miniV = new WaveDefinition.MobGroup(); miniV.type = "minecraft:zombie"; miniV.count = 5; miniV.isMiniZombieVanilla = true;
        w.rewardCommands = java.util.List.of(
            "give %player% minecraft:arrow 10",
            "effect give %player% minecraft:regeneration 10 1"
        );
        w.mobs = java.util.List.of(z, miniV); return w;
    }
    private WaveDefinition wave2() {
        WaveDefinition w = new WaveDefinition();
        w.waveNumber = 2; w.name = "wave.2.name";
        w.startMessage = "wave.2.startMessage";
        w.endMessage = "wave.2.endMessage"; w.durationSeconds = 240;
        var z = new WaveDefinition.MobGroup(); z.type = "minecraft:zombie"; z.count = 5;
        z.healthMultiplier = 1.2f; z.canBreakBlocks = true; z.breakLevel = "stone"; z.canClimb = true; z.canSwim = true;
        var sk = new WaveDefinition.MobGroup(); sk.type = "minecraft:skeleton"; sk.count = 4;
        sk.damageMultiplier = 1.2f; sk.canBreakBlocks = true; sk.breakLevel = "stone";
        var miniB = new WaveDefinition.MobGroup(); miniB.type = "minecraft:zombie"; miniB.count = 4; miniB.isMiniZombieBoosted = true;
        var kami = new WaveDefinition.MobGroup(); kami.type = "minecraft:zombie"; kami.count = 2; kami.isKamikaze = true; kami.speedMultiplier = 1.4f;
        w.mobs = java.util.List.of(z, sk, miniB, kami); return w;
    }
    private WaveDefinition wave3() {
        WaveDefinition w = new WaveDefinition();
        w.waveNumber = 3; w.name = "wave.3.name";
        w.startMessage = "wave.3.startMessage";
        w.endMessage = "wave.3.endMessage"; w.durationSeconds = 300;
        var fz = new WaveDefinition.MobGroup(); fz.type = "minecraft:zombie"; fz.count = 5;
        fz.speedMultiplier = 1.8f; fz.damageMultiplier = 1.3f; fz.canBreakBlocks = true; fz.breakLevel = "deepslate";
        fz.canClimb = true; fz.climbHeight = 2.0f; fz.canSwim = true;
        var sp = new WaveDefinition.MobGroup(); sp.type = "minecraft:spider"; sp.count = 4;
        sp.healthMultiplier = 1.5f; sp.damageMultiplier = 1.4f; sp.speedMultiplier = 1.5f;
        var miniB = new WaveDefinition.MobGroup(); miniB.type = "minecraft:zombie"; miniB.count = 6; miniB.isMiniZombieBoosted = true;
        var miniK = new WaveDefinition.MobGroup(); miniK.type = "minecraft:zombie"; miniK.count = 4; miniK.isMiniZombieKamikaze = true;
        w.mobs = java.util.List.of(fz, sp, miniB, miniK); return w;
    }
    private WaveDefinition wave4() {
        WaveDefinition w = new WaveDefinition();
        w.waveNumber = 4; w.name = "wave.4.name";
        w.startMessage = "wave.4.startMessage";
        w.endMessage = "wave.4.endMessage"; w.durationSeconds = 360;
        w.hasBoss = true; w.bossType = "minecraft:zombie"; w.bossName = "wave.4.bossName";
        var escort = new WaveDefinition.MobGroup(); escort.type = "minecraft:zombie"; escort.count = 6;
        escort.healthMultiplier = 1.5f; escort.damageMultiplier = 1.4f; escort.canBreakBlocks = true;
        escort.breakLevel = "deepslate"; escort.canClimb = true; escort.climbHeight = 2.5f; escort.canSwim = true;
        var miniV = new WaveDefinition.MobGroup(); miniV.type = "minecraft:zombie"; miniV.count = 4; miniV.isMiniZombieVanilla = true;
        var miniB = new WaveDefinition.MobGroup(); miniB.type = "minecraft:zombie"; miniB.count = 4; miniB.isMiniZombieBoosted = true;
        var miniK = new WaveDefinition.MobGroup(); miniK.type = "minecraft:zombie"; miniK.count = 3; miniK.isMiniZombieKamikaze = true;
        var kami  = new WaveDefinition.MobGroup(); kami.type = "minecraft:zombie"; kami.count = 3; kami.isKamikaze = true; kami.speedMultiplier = 1.5f;

        // Healer: prioridad eliminar — cura a todos los cercanos
        var healer = new WaveDefinition.MobGroup(); healer.type = "minecraft:zombie"; healer.count = 2;
        healer.isHealer = true; healer.healthMultiplier = 0.8f; // frágil pero molesto

        // Shielder: escudo, bloquea daño frontal
        var shielder = new WaveDefinition.MobGroup(); shielder.type = "minecraft:zombie"; shielder.count = 3;
        shielder.isShielder = true; shielder.healthMultiplier = 1.5f; shielder.damageMultiplier = 1.3f;

        // Summoner: al morir spawnea 3 minis
        var summoner = new WaveDefinition.MobGroup(); summoner.type = "minecraft:zombie"; summoner.count = 2;
        summoner.isSummoner = true; summoner.healthMultiplier = 1.2f; summoner.speedMultiplier = 0.9f;

        // Title épico para wave4
        w.titleOnStart    = "wave.4.titleOnStart";
        w.subtitleOnStart = "wave.4.subtitleOnStart";
        w.titleOnComplete = "wave.4.titleOnComplete";
        w.subtitleOnComplete = "wave.4.subtitleOnComplete";

        w.mobs = java.util.List.of(escort, miniV, miniB, miniK, kami, healer, shielder, summoner); return w;
    }
    private WaveDefinition wave5() {
        WaveDefinition w = new WaveDefinition();
        w.waveNumber = 5; w.name = "wave.5.name";
        w.startMessage = "wave.5.startMessage";
        w.endMessage = "wave.5.endMessage"; w.durationSeconds = 420;
        w.hasBoss = true; w.bossType = "minecraft:zombie";
        w.bossName = "wave.5.bossName";

        // Zombies pesados con deepslate
        var heavy = new WaveDefinition.MobGroup(); heavy.type = "minecraft:zombie"; heavy.count = 8;
        heavy.healthMultiplier = 2.0f; heavy.damageMultiplier = 1.6f;
        heavy.canBreakBlocks = true; heavy.breakLevel = "deepslate";
        heavy.canClimb = true; heavy.climbHeight = 3.0f; heavy.canSwim = true;

        // Esqueletos francotiradores
        var snipers = new WaveDefinition.MobGroup(); snipers.type = "minecraft:skeleton"; snipers.count = 6;
        snipers.healthMultiplier = 1.4f; snipers.damageMultiplier = 2.0f; snipers.speedMultiplier = 1.3f;
        snipers.canBreakBlocks = true; snipers.breakLevel = "stone_bricks";

        // Los 3 sub-tipos de mini
        var miniV = new WaveDefinition.MobGroup(); miniV.type = "minecraft:zombie"; miniV.count = 5; miniV.isMiniZombieVanilla = true;
        var miniB = new WaveDefinition.MobGroup(); miniB.type = "minecraft:zombie"; miniB.count = 5; miniB.isMiniZombieBoosted = true;
        var miniK = new WaveDefinition.MobGroup(); miniK.type = "minecraft:zombie"; miniK.count = 4; miniK.isMiniZombieKamikaze = true;

        // Kamikazes adultos
        var kami = new WaveDefinition.MobGroup(); kami.type = "minecraft:zombie"; kami.count = 4;
        kami.isKamikaze = true; kami.speedMultiplier = 1.6f; kami.healthMultiplier = 1.3f;

        w.rewardCommands = java.util.List.of(
            "give %player% minecraft:diamond 3",
            "give %player% minecraft:golden_apple 2",
            "effect give %player% minecraft:strength 60 1",
            "effect give %player% minecraft:speed 60 1"
        );
        w.mobs = java.util.List.of(heavy, snipers, miniV, miniB, miniK, kami); return w;
    }

    private WaveDefinition wave6() {
        WaveDefinition w = new WaveDefinition();
        w.waveNumber = 6; w.name = "wave.6.name";
        w.startMessage = "wave.6.startMessage";
        w.endMessage = "wave.6.endMessage";
        w.durationSeconds = 480;
        w.hasBoss = true; w.bossType = "minecraft:wither_skeleton";
        w.bossName = "wave.6.bossName";

        // Blazes — atacantes de fuego a distancia
        var blazes = new WaveDefinition.MobGroup(); blazes.type = "minecraft:blaze"; blazes.count = 6;
        blazes.healthMultiplier = 1.5f; blazes.damageMultiplier = 1.6f; blazes.speedMultiplier = 1.2f;
        blazes.displayName = "§6☁ Blaze";

        // Wither Skeletons — melee oscuro
        var withers = new WaveDefinition.MobGroup(); withers.type = "minecraft:wither_skeleton"; withers.count = 6;
        withers.healthMultiplier = 1.8f; withers.damageMultiplier = 2.0f; withers.speedMultiplier = 1.2f;
        withers.canBreakBlocks = true; withers.breakLevel = "deepslate";
        withers.canClimb = true; withers.climbHeight = 2.0f;
        withers.displayName = "§0☠ Wither Skeleton";

        // Piglins bestiales — berserkers rápidos
        var brutes = new WaveDefinition.MobGroup(); brutes.type = "minecraft:piglin_brute"; brutes.count = 5;
        brutes.healthMultiplier = 1.6f; brutes.damageMultiplier = 2.2f; brutes.speedMultiplier = 1.3f;
        brutes.canBreakBlocks = true; brutes.breakLevel = "stone_bricks";
        brutes.canClimb = true;
        brutes.displayName = "§c⚔ Piglin Brute";

        // Hoglins — tanques de carga
        var hoglins = new WaveDefinition.MobGroup(); hoglins.type = "minecraft:hoglin"; hoglins.count = 4;
        hoglins.healthMultiplier = 2.0f; hoglins.damageMultiplier = 2.5f; hoglins.speedMultiplier = 1.1f;
        hoglins.displayName = "§4⚡ Hoglin";

        // Mini kamikazes del nether (zombified piglin baby)
        var miniNether = new WaveDefinition.MobGroup(); miniNether.type = "minecraft:zombie"; miniNether.count = 8;
        miniNether.isMiniZombieKamikaze = true; miniNether.speedMultiplier = 1.5f;
        miniNether.healthMultiplier = 0.8f;

        // Ghast como disturbio visual/sonoro (si hay espacio abierto)
        var ghasts = new WaveDefinition.MobGroup(); ghasts.type = "minecraft:ghast"; ghasts.count = 2;
        ghasts.healthMultiplier = 1.5f; ghasts.damageMultiplier = 1.3f;
        ghasts.displayName = "§f👻 Ghast";

        w.rewardCommands = java.util.List.of(
            "give %player% minecraft:netherite_ingot 1",
            "give %player% minecraft:diamond 8",
            "give %player% minecraft:golden_apple 3",
            "give %player% minecraft:enchanted_golden_apple 1",
            "effect give %player% minecraft:strength 120 2",
            "effect give %player% minecraft:resistance 120 1",
            "effect give %player% minecraft:fire_resistance 120 0"
        );
        w.mobs = java.util.List.of(blazes, withers, brutes, hoglins, miniNether, ghasts); return w;
    }
}
