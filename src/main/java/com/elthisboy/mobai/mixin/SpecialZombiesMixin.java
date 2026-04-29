package com.elthisboy.mobai.mixin;

import com.elthisboy.mobai.ai.HealerZombieAI;
import com.elthisboy.mobai.ai.SummonerZombieAI;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin para los 3 zombies especiales:
 *
 * HEALER  (§a✚ Healer)   — no ataca jugadores, cura mobs aliados cercanos
 * SHIELDER(§7🛡 Shielder) — lleva escudo en offhand, bloquea daño frontal
 * SUMMONER(§5✦ Summoner)  — al morir invoca mini-zombies alrededor
 *
 * Nota: el hook de muerte se inyecta en tickMovement con chequeo de !isAlive()
 * porque ZombieEntity no sobreescribe onDeath en 1.21.1 Yarn mappings.
 */
@Mixin(ZombieEntity.class)
public class SpecialZombiesMixin {

    @Unique private boolean sz_checkedType    = false;
    @Unique private boolean sz_isHealer       = false;
    @Unique private boolean sz_isSummoner     = false;
    @Unique private boolean sz_isShielder     = false;
    @Unique private boolean sz_shieldEquipped = false;
    @Unique private boolean sz_deathHandled   = false;

    @Unique
    private void sz_checkType(ZombieEntity self) {
        if (sz_checkedType) return;
        sz_checkedType = true;
        var name = self.getCustomName();
        if (name == null) return;
        String n = name.getString();
        sz_isHealer   = n.contains("Healer");
        sz_isSummoner = n.contains("Summoner");
        sz_isShielder = n.contains("Shielder");
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity)(Object) this;
        World world = self.getWorld();
        if (world.isClient()) return;

        sz_checkType(self);

        // ── HEALER: curar aliados ──────────────────────────────────────
        if (sz_isHealer) {
            HealerZombieAI.tick(self, world, 40, 3.0f, 8.0f);
        }

        // ── SHIELDER: equipar escudo una vez ──────────────────────────
        if (sz_isShielder && !sz_shieldEquipped) {
            sz_shieldEquipped = true;
            self.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            self.setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.OFFHAND, 0.0f);
        }

        // ── SUMMONER: detectar muerte en tickMovement ──────────────────
        // ZombieEntity no sobreescribe onDeath en 1.21.1, así que detectamos
        // cuando !isAlive() por primera vez (justo tras recibir el golpe fatal)
        if (sz_isSummoner && !sz_deathHandled && !self.isAlive()) {
            sz_deathHandled = true;
            if (world instanceof ServerWorld sw) {
                SummonerZombieAI.onDeath(self, sw, 3);
            }
        }
    }
}
