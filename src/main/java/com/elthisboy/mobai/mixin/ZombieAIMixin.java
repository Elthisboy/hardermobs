package com.elthisboy.mobai.mixin;

import com.elthisboy.mobai.MobAi;
import com.elthisboy.mobai.ai.MobObjectiveRegistry;
import com.elthisboy.mobai.ai.BlockAttackRegistry;
import net.minecraft.block.*;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public class ZombieAIMixin {

    @Unique private int      mobAI_cooldown      = 0;
    @Unique private BlockPos mobAI_targetBlock    = null;
    @Unique private BlockPos mobAI_lastRegistered = null;
    @Unique private int      mobAI_navCooldown    = 0;

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void onTickMovement(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity)(Object) this;
        World world = self.getWorld();
        if (world.isClient()) return;

        var config = MobAi.CONFIG;
        if (config == null) return;

        // ── MOVIMIENTO EN AGUA ────────────────────────────────────────
        if (self.isTouchingWater()) {
            self.setSwimming(true);

            // Destino: target si lo hay, objetivo del mapa si no
            double tx, ty, tz;
            boolean hasTarget = self.getTarget() != null && self.getTarget().isAlive();

            if (hasTarget) {
                tx = self.getTarget().getX();
                ty = self.getTarget().getY();
                tz = self.getTarget().getZ();
            } else {
                var obj = MobObjectiveRegistry.get(self.getUuid());
                if (obj == null) {
                    if (self.isSubmergedInWater())
                        self.setVelocity(self.getVelocity().x, 0.10, self.getVelocity().z);
                    return;
                }
                int phase = MobObjectiveRegistry.getPhase(self.getUuid());
                if (phase == MobObjectiveRegistry.PHASE_RALLY && obj.hasRally()) {
                    // En rally: ir al rally point nadando
                    tx = obj.rallyX() + 0.5;
                    ty = obj.rallyY();
                    tz = obj.rallyZ() + 0.5;
                } else {
                    tx = obj.x() + 0.5;
                    ty = obj.y();
                    tz = obj.z() + 0.5;
                    // Verificar si llegó al objetivo
                    double dx = tx - self.getX(), dz = tz - self.getZ();
                    if (dx*dx + dz*dz <= 16 && obj.hasRally()) {
                        MobObjectiveRegistry.setPhase(self.getUuid(), MobObjectiveRegistry.PHASE_RALLY);
                        tx = obj.rallyX() + 0.5;
                        ty = obj.rallyY();
                        tz = obj.rallyZ() + 0.5;
                    }
                }
            }

            double ex = self.getX(), ez = self.getZ();
            double distSq = (tx - ex) * (tx - ex) + (tz - ez) * (tz - ez);
            double minDist = hasTarget ? 2.0 : 9.0;

            if (distSq > minDist) {
                double dist = Math.sqrt(distSq);
                // Velocidad horizontal moderada — como nadar despacio
                double spd = 0.04; // lento sin target — solo avanzando al objetivo
                // Velocidad vertical: empujar hacia arriba solo si está muy sumergido
                double curVy = self.getVelocity().y;
                double vy = self.isSubmergedInWater() ? 0.12 : (curVy < 0 ? 0.0 : curVy);
                self.setVelocity((tx - ex) / dist * spd, vy, (tz - ez) / dist * spd);
                self.velocityModified = true;
                self.getLookControl().lookAt(tx, ty, tz);
            } else if (hasTarget) {
                self.tryAttack(self.getTarget());
            }
            return; // no ejecutar AI terrestre en agua
        }

        // ── EN TIERRA ─────────────────────────────────────────────────
        if (config.zombieClimbHeight > 1.0f)
            tryClimb(self, world, config.zombieClimbHeight);

        // Avanzar al objetivo / merodear en rally cada 40 ticks sin target
        if (self.getTarget() == null) {
            mobAI_navCooldown--;
            if (mobAI_navCooldown <= 0) {
                mobAI_navCooldown = 40;
                var obj = MobObjectiveRegistry.get(self.getUuid());
                if (obj != null) {
                    int phase = MobObjectiveRegistry.getPhase(self.getUuid());

                    if (phase == MobObjectiveRegistry.PHASE_OBJECTIVE) {
                        // Fase 1: avanzar al objetivo principal
                        double tx = obj.x() + 0.5, ty = obj.y(), tz = obj.z() + 0.5;
                        double distSq = self.getBlockPos().getSquaredDistance(
                            new BlockPos(obj.x(), obj.y(), obj.z()));
                        if (distSq <= 16 && obj.hasRally()) {
                            // Llegó al objetivo → cambiar a fase rally
                            MobObjectiveRegistry.setPhase(self.getUuid(),
                                MobObjectiveRegistry.PHASE_RALLY);
                        } else if (distSq > 9) {
                            self.getNavigation().startMovingTo(tx, ty, tz, 0.4);
                        }
                    } else {
                        // Fase 2: merodear alrededor del rally point
                        java.util.Random rng = new java.util.Random();
                        int[] wt = MobObjectiveRegistry.getOrCreateWanderTarget(
                            self.getUuid(), obj.rallyX(), obj.rallyY(), obj.rallyZ(),
                            obj.rallyRadius(), rng);
                        double distSq = self.getBlockPos().getSquaredDistance(
                            new BlockPos(wt[0], wt[1], wt[2]));
                        if (distSq <= 4) {
                            // Llegó al punto de wander → elegir uno nuevo
                            MobObjectiveRegistry.refreshWanderTarget(
                                self.getUuid(), obj.rallyX(), obj.rallyY(), obj.rallyZ(),
                                obj.rallyRadius(), rng);
                        } else {
                            self.getNavigation().startMovingTo(wt[0]+0.5, wt[1], wt[2]+0.5, 0.5);
                        }
                    }
                }
            }
        }

        if (config.zombieBreakWood) {
            mobAI_cooldown--;
            if (mobAI_cooldown <= 0) {
                mobAI_cooldown = 3;
                tryBreak(self, world, config.zombieBreakSpeed);
            }
        }
    }

    // ── ESCALADA ──────────────────────────────────────────────
    @Unique
    private void tryClimb(ZombieEntity entity, World world, float maxHeight) {
        double vx = entity.getVelocity().x, vz = entity.getVelocity().z;
        if (Math.abs(vx) < 0.01 && Math.abs(vz) < 0.01) return;

        BlockPos pos = entity.getBlockPos();
        int dx = (int) Math.signum(vx), dz = (int) Math.signum(vz);
        BlockPos front = pos.add(dx, 0, dz);

        if (world.getBlockState(front).isSolidBlock(world, front)
                && world.getBlockState(front.up()).isAir()
                && world.getBlockState(pos.up()).isAir()
                && entity.isOnGround()) {
            entity.setVelocity(vx, 0.35f + (maxHeight - 1.0f) * 0.15f, vz);
        }
    }

    // ── RUPTURA CON PROGRESO GLOBAL ───────────────────────────
    @Unique
    private void tryBreak(ZombieEntity entity, World world, int baseSpeed) {
        if (entity.getTarget() == null) return;

        int dx = (int) Math.signum(entity.getTarget().getX() - entity.getX());
        int dz = (int) Math.signum(entity.getTarget().getZ() - entity.getZ());
        if (dx == 0 && dz == 0) { clearTarget(world, entity); return; }

        String level = MobAi.CONFIG.zombieBreakLevel;
        BlockPos target = null;
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos c = entity.getBlockPos().add(dx, dy, dz);
            if (canBreak(world.getBlockState(c), level)) { target = c; break; }
        }

        if (target == null) { clearTarget(world, entity); return; }

        if (!target.equals(mobAI_targetBlock)) {
            clearTarget(world, entity);
            mobAI_targetBlock = target;
        }

        if (!target.equals(mobAI_lastRegistered)) {
            if (mobAI_lastRegistered != null) BlockAttackRegistry.remove(mobAI_lastRegistered);
            mobAI_lastRegistered = target;
        }
        BlockAttackRegistry.addBreakProgress(target);

        BlockState state  = world.getBlockState(target);
        int baseTicks     = calculateBaseTicks(state, baseSpeed);
        int globalTicks   = BlockAttackRegistry.getBreakTicks(target);
        int attackers     = BlockAttackRegistry.getAttackers(target);

        int stage = Math.min((int)((globalTicks / (float) baseTicks) * 9), 9);
        if (world instanceof ServerWorld sw)
            sw.setBlockBreakingInfo(entity.getId(), target, stage);

        if (globalTicks % 6 == 0) {
            net.minecraft.sound.SoundEvent hitSound = getBreakSound(state);
            world.playSound(null, target, hitSound,
                SoundCategory.HOSTILE, 1.2f, 0.7f + world.random.nextFloat() * 0.4f);
            if (globalTicks % 30 == 0) {
                world.playSound(null, target,
                    net.minecraft.sound.SoundEvents.BLOCK_STONE_BREAK,
                    SoundCategory.BLOCKS, 0.5f, 0.8f + world.random.nextFloat() * 0.3f);
            }
        }

        int ticksNeeded = Math.max((int)(baseTicks / (float) attackers), baseTicks / 10);
        if (globalTicks >= ticksNeeded) {
            world.breakBlock(target, true, entity);
            if (world instanceof ServerWorld sw) sw.setBlockBreakingInfo(entity.getId(), target, -1);
            BlockAttackRegistry.remove(target);
            mobAI_targetBlock    = null;
            mobAI_lastRegistered = null;
        }
    }

    @Unique
    private void clearTarget(World world, ZombieEntity entity) {
        if (mobAI_targetBlock != null && world instanceof ServerWorld sw)
            sw.setBlockBreakingInfo(entity.getId(), mobAI_targetBlock, -1);
        if (mobAI_lastRegistered != null) {
            BlockAttackRegistry.remove(mobAI_lastRegistered);
            mobAI_lastRegistered = null;
        }
        mobAI_targetBlock = null;
    }

    @Unique
    private int calculateBaseTicks(BlockState state, int base) {
        if (isDeepslateVariant(state))              return base * 6;
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) {
            float h = state.getHardness(null, null);
            if (h >= 2.0f) return (int)(base * 3.5f);
            return base * 2;
        }
        return base;
    }

    @Unique
    private boolean canBreak(BlockState state, String breakLevel) {
        if (state.isAir()) return false;
        Block block = state.getBlock();
        float hardness = state.getHardness(null, null);
        if (hardness < 0) return false;
        if (block instanceof BedBlock || block instanceof ChestBlock
                || block instanceof EnderChestBlock || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock) return false;

        boolean isWood      = state.isIn(BlockTags.AXE_MINEABLE)
                           || block instanceof DoorBlock || block instanceof FenceGateBlock
                           || block instanceof TrapdoorBlock || block instanceof FenceBlock;
        boolean isStone     = state.isIn(BlockTags.PICKAXE_MINEABLE) && hardness <= 3.0f;
        boolean isDeepslate = isDeepslateVariant(state);

        return switch (breakLevel.toLowerCase()) {
            case "deepslate"    -> isWood || isStone || isDeepslate;
            case "stone_bricks" -> isWood || isStone;
            case "stone"        -> isWood || isStone;
            default             -> isWood;
        };
    }

    @Unique
    private boolean isDeepslateVariant(BlockState state) {
        if (isDeepslateByName(state)) return true;
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) {
            float h = state.getHardness(null, null);
            return h >= 3.0f && h <= 4.5f;
        }
        return false;
    }

    @Unique
    private boolean isDeepslateByName(BlockState state) {
        return net.minecraft.registry.Registries.BLOCK.getId(state.getBlock())
                .getPath().contains("deepslate");
    }

    @Unique
    private net.minecraft.sound.SoundEvent getBreakSound(BlockState state) {
        if (isDeepslateVariant(state))              return SoundEvents.BLOCK_DEEPSLATE_HIT;
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) return SoundEvents.BLOCK_STONE_HIT;
        return SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR;
    }
}
