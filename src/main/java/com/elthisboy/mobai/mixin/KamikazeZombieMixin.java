package com.elthisboy.mobai.mixin;

import com.elthisboy.mobai.MobAi;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zombies kamikaze con TNT en la cabeza.
 * Funciona tanto para adultos (isKamikaze) como para mini kamikazes (isMiniZombieKamikaze).
 * Identificación: nombre personalizado que contiene "Kamikaze".
 *
 * Comportamiento:
 *  1. Cuando llega a triggerDistance del target → cuenta regresiva (2s por defecto)
 *  2. Sonido + partículas de fuego pulsantes durante la cuenta
 *  3. Aviso en actionbar del jugador
 *  4. Explosión + discard() al llegar a 0
 *  5. Si el target se aleja x3 la distancia → cancelar cuenta
 */
@Mixin(ZombieEntity.class)
public class KamikazeZombieMixin {

    @Unique private int     mobai_countdown    = -1;
    @Unique private boolean mobai_isKamikaze   = false;
    @Unique private boolean mobai_isMini        = false;
    @Unique private boolean mobai_checkedName  = false;

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity)(Object) this;
        World world = self.getWorld();
        if (world.isClient()) return;

        var config = MobAi.CONFIG;
        if (config == null || !config.kamikazeEnabled) return;

        // Detectar si es kamikaze (solo una vez por entidad)
        if (!mobai_checkedName) {
            mobai_checkedName = true;
            var nameText = self.getCustomName();
            if (nameText != null) {
                String n = nameText.getString();
                mobai_isKamikaze = n.contains("Kamikaze");
                mobai_isMini     = n.contains("Mini");
            }
        }
        if (!mobai_isKamikaze) return;

        if (self.getTarget() == null || !self.getTarget().isAlive()) {
            cancelCountdown(self, world);
            return;
        }

        double dist = self.distanceTo(self.getTarget());
        float triggerDist = mobai_isMini
            ? config.miniKamikazeTriggerDist
            : config.kamikazeTriggerDistance;

        // Iniciar cuenta regresiva
        if (dist <= triggerDist && mobai_countdown < 0) {
            mobai_countdown = config.kamikazeCountdownSeconds * 20; // seg → ticks

            if (self.getTarget() instanceof ServerPlayerEntity player) {
                player.sendMessage(
                    mobai_isMini
                        ? Text.translatable("mobai.mob.mini_kamikaze_warning")
                        : Text.translatable("mobai.mob.kamikaze_warning"),
                    true); // actionbar
            }
        }

        if (mobai_countdown > 0) {
            mobai_countdown--;

            // Cancelar si el target se alejó mucho
            if (dist > triggerDist * 3) {
                cancelCountdown(self, world);
                return;
            }

            // Sonido que se acelera conforme se acerca a 0
            int totalTicks = config.kamikazeCountdownSeconds * 20;
            int interval = Math.max(2, (int)((mobai_countdown / (float) totalTicks) * 10) + 2);
            if (mobai_countdown % interval == 0) {
                float pitch = 1.0f + (totalTicks - mobai_countdown) / (float) totalTicks;
                world.playSound(null, self.getBlockPos(),
                    SoundEvents.ENTITY_CREEPER_PRIMED,
                    SoundCategory.HOSTILE, 1.0f, pitch);
            }

            // Partículas de fuego pulsantes
            if (world instanceof ServerWorld sw && mobai_countdown % 4 == 0) {
                sw.spawnParticles(ParticleTypes.FLAME,
                    self.getX(), self.getY() + 0.5, self.getZ(),
                    4, 0.2, 0.3, 0.2, 0.02);
            }

            // ¡BOOM!
            if (mobai_countdown <= 0) {
                float explosionPower = mobai_isMini
                    ? config.miniKamikazeExplosion
                    : config.kamikazeExplosionRadius;

                if (world instanceof ServerWorld sw) {
                    // Flash de partículas
                    sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        self.getX(), self.getY() + 0.5, self.getZ(),
                        1, 0, 0, 0, 0);

                    // Explosión visual + daño SOLO a jugadores (no a otros mobs)
                    kamikazeExplode(self, sw, explosionPower, config.kamikazeBreaksBlocks);
                }
                self.discard();
            }
        }
    }

    @Unique
    private void kamikazeExplode(ZombieEntity self, net.minecraft.server.world.ServerWorld world,
                                  float power, boolean breakBlocks) {
        double x = self.getX(), y = self.getY(), z = self.getZ();

        // Efecto visual y sonido de explosión — sin usar createExplosion que daña todo
        // Partículas grandes de explosión
        world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
            x, y + 0.5, z, 1, 0, 0, 0, 0);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
            x, y + 0.5, z, 8, power * 0.5, power * 0.3, power * 0.5, 0.1);
        // Sonido de explosión enviado a todos los clientes
        world.playSound(null, x, y, z,
            net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE,
            net.minecraft.sound.SoundCategory.HOSTILE,
            4.0f, (0.7f + world.random.nextFloat() * 0.3f));

        // Romper bloques si está configurado — usando una explosión separada SIN entidades
        if (breakBlocks) {
            world.createExplosion(null, x, y, z, power, World.ExplosionSourceType.TNT);
            // Nota: source=null → no daña entidades, solo rompe bloques
        }

        // Daño SOLO a jugadores — proporcional a la distancia
        double radius = power * 2.0;
        double radiusSq = radius * radius;
        for (var player : world.getPlayers()) {
            double distSq = player.squaredDistanceTo(x, y, z);
            if (distSq <= radiusSq) {
                float ratio = 1.0f - (float)(Math.sqrt(distSq) / radius);
                float damage = power * 7.0f * ratio;
                if (damage > 0) {
                    player.damage(world.getDamageSources().explosion(self, self), damage);
                }
            }
        }
        // Los mobs NO reciben daño — intencional
    }

    @Unique
    private void cancelCountdown(ZombieEntity self, World world) {
        mobai_countdown = -1;
    }
}
