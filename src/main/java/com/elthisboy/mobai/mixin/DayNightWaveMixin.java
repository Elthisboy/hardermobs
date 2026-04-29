package com.elthisboy.mobai.mixin;

import com.elthisboy.mobai.MobAi;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Maneja el ciclo día/noche durante las oleadas:
 *
 * - Si hay oleada activa: CONGELA el tiempo en la noche.
 *   Después de cada tick, el tiempo se retrocede 1 tick para cancelar
 *   el avance natural de Minecraft. El sol no sale mientras haya oleada.
 *
 * - Al completar/detener la oleada: el tiempo reanuda normalmente.
 *
 * - Si despawnAtDawn=true Y el jugador usa /time set para forzar el día,
 *   la oleada se detiene igual (comportamiento de seguridad).
 */
@Mixin(ServerWorld.class)
public class DayNightWaveMixin {

    @Unique private boolean mobai_wasNight      = false;
    @Unique private boolean mobai_dawnWarnGiven = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ServerWorld world = (ServerWorld)(Object) this;
        var config  = MobAi.CONFIG;
        var waveMgr = MobAi.WAVE_MANAGER;
        if (config == null || waveMgr == null) return;

        // Actualizar bossbar cada 20 ticks
        if (world.getTime() % 20 == 0)
            waveMgr.tickBossBar(world);

        // Countdown de auto-next
        waveMgr.tickAutoNext(world);

        long time    = world.getTimeOfDay() % 24000L;
        boolean isNight = config.isNightTime(time);

        // ── Sin oleada activa: comportamiento normal ──────────────────
        if (!waveMgr.isWaveActive()) {
            mobai_wasNight    = isNight;
            mobai_dawnWarnGiven = false;
            return;
        }

        // ── Oleada activa: CONGELAR el tiempo en noche ────────────────
        // Retroceder 1 tick para cancelar el avance que acaba de ocurrir.
        // Efecto neto: el tiempo se queda estático durante toda la oleada.
        if (isNight) {
            world.setTimeOfDay(world.getTimeOfDay() - 1);
        }

        // ── Si el tiempo llegó a día (p.ej. /time set day manual) ─────
        // Mostrar aviso y detener oleada si despawnAtDawn está activo
        if (!mobai_dawnWarnGiven && time >= (config.dawnTick - 200) && config.waveAnnounce) {
            mobai_dawnWarnGiven = true;
            broadcast(world, Text.translatable("mobai.wave.dawn_warning"));
        }

        if (mobai_wasNight && !isNight && config.despawnAtDawn) {
            waveMgr.stopWave(world);
            if (config.waveAnnounce)
                broadcast(world, Text.translatable("mobai.wave.dawn_vanish"));
            mobai_dawnWarnGiven = false;
        }

        mobai_wasNight = isNight;
    }

    @Unique
    private void broadcast(ServerWorld world, Text msg) {
        for (var p : world.getPlayers()) p.sendMessage(msg, false);
    }
}
