package com.elthisboy.mobai.mixin;

import com.elthisboy.mobai.MobAi;
import com.elthisboy.mobai.ai.MobObjectiveRegistry;
import com.elthisboy.mobai.ai.BlockAttackRegistry;
import net.minecraft.block.*;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
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

@Mixin(AbstractSkeletonEntity.class)
public class SkeletonAIMixin {

    @Unique private int      sk_cooldown      = 0;
    @Unique private int      sk_navCooldown   = 0;
    @Unique private BlockPos sk_targetBlock   = null;
    @Unique private BlockPos sk_lastRegistered = null;

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        AbstractSkeletonEntity self = (AbstractSkeletonEntity)(Object) this;
        World world = self.getWorld();
        if (world.isClient()) return;

        var config = MobAi.CONFIG;
        if (config == null || !config.skeletonBreakBlocks) return;

        // Avanzar al objetivo si no hay target
        // En agua: velocidad directa (el pathfinder terrestre no funciona)
        if (self.isTouchingWater()) {
            self.setSwimming(true);
            boolean hasTarget = self.getTarget() != null && self.getTarget().isAlive();

            if (hasTarget) {
                double tx = self.getTarget().getX(), ty = self.getTarget().getY(), tz = self.getTarget().getZ();
                double ex = self.getX(), ez = self.getZ();
                double distSq = (tx-ex)*(tx-ex) + (tz-ez)*(tz-ez);
                if (distSq > 4) {
                    double dist = Math.sqrt(distSq);
                    double vy = self.isSubmergedInWater() ? 0.18 : Math.max(self.getVelocity().y, 0.04);
                    self.setVelocity((tx-ex)/dist * 0.10, vy, (tz-ez)/dist * 0.10);
                    self.velocityModified = true;
                    self.getLookControl().lookAt(tx, ty, tz);
                }
            } else {
                var obj = MobObjectiveRegistry.get(self.getUuid());
                if (obj != null) {
                    double tx = obj.x() + 0.5, ty = obj.y(), tz = obj.z() + 0.5;
                    double ex = self.getX(), ez = self.getZ();
                    double distSq = (tx-ex)*(tx-ex) + (tz-ez)*(tz-ez);
                    if (distSq > 9) {
                        double dist = Math.sqrt(distSq);
                        double vy = self.isSubmergedInWater() ? 0.12 : Math.max(self.getVelocity().y, 0.04);
                        self.setVelocity((tx-ex)/dist * 0.08, vy, (tz-ez)/dist * 0.08);
                        self.velocityModified = true;
                        self.getLookControl().lookAt(tx, ty, tz);
                    }
                } else if (self.isSubmergedInWater()) {
                    self.setVelocity(self.getVelocity().x, 0.10, self.getVelocity().z);
                }
            }
        } else {
            // En tierra: pathfinding cada 40 ticks
            sk_navCooldown--;
            if (sk_navCooldown <= 0) {
                sk_navCooldown = 40;
                if (self.getTarget() == null) {
                    var obj = MobObjectiveRegistry.get(self.getUuid());
                    if (obj != null && self.getBlockPos().getSquaredDistance(
                            new net.minecraft.util.math.BlockPos(obj.x(), obj.y(), obj.z())) > 9)
                        self.getNavigation().startMovingTo(obj.x()+0.5, obj.y(), obj.z()+0.5, obj.speed());
                }
            }
        }

        sk_cooldown--;
        if (sk_cooldown <= 0) {
            sk_cooldown = 3;
            tryBreak(self, world, config.skeletonBreakSpeed, config.skeletonBreakLevel);
        }
    }

    @Unique
    private void tryBreak(AbstractSkeletonEntity entity, World world, int baseSpeed, String breakLevel) {
        if (entity.getTarget() == null || entity.distanceTo(entity.getTarget()) > 6.0) {
            clearTarget(world, entity); return;
        }

        int dx = (int) Math.signum(entity.getTarget().getX() - entity.getX());
        int dz = (int) Math.signum(entity.getTarget().getZ() - entity.getZ());
        if (dx == 0 && dz == 0) return;

        BlockPos target = null;
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos c = entity.getBlockPos().add(dx, dy, dz);
            if (canBreak(world.getBlockState(c), breakLevel)) { target = c; break; }
        }

        if (target == null) { clearTarget(world, entity); return; }

        if (!target.equals(sk_targetBlock)) {
            clearTarget(world, entity);
            sk_targetBlock = target;
        }

        if (!target.equals(sk_lastRegistered)) {
            if (sk_lastRegistered != null) BlockAttackRegistry.remove(sk_lastRegistered);
            sk_lastRegistered = target;
        }
        BlockAttackRegistry.addBreakProgress(target);

        BlockState state    = world.getBlockState(target);
        int baseTicks       = calculateBaseTicks(state, baseSpeed);
        int globalTicks     = BlockAttackRegistry.getBreakTicks(target);
        int attackers       = BlockAttackRegistry.getAttackers(target);

        int stage = Math.min((int)((globalTicks / (float) baseTicks) * 9), 9);
        if (world instanceof ServerWorld sw)
            sw.setBlockBreakingInfo(entity.getId(), target, stage);

        if (globalTicks % 10 == 0) {
            world.playSound(null, target, getBreakSound(state),
                SoundCategory.HOSTILE, 0.9f, 0.9f + world.random.nextFloat() * 0.2f);
        }

        int ticksNeeded = Math.max((int)(baseTicks / (float) attackers), baseTicks / 10);
        if (globalTicks >= ticksNeeded) {
            world.breakBlock(target, true, entity);
            if (world instanceof ServerWorld sw) sw.setBlockBreakingInfo(entity.getId(), target, -1);
            BlockAttackRegistry.remove(target);
            sk_targetBlock    = null;
            sk_lastRegistered = null;
        }
    }

    @Unique
    private void clearTarget(World world, AbstractSkeletonEntity entity) {
        if (sk_targetBlock != null && world instanceof ServerWorld sw)
            sw.setBlockBreakingInfo(entity.getId(), sk_targetBlock, -1);
        if (sk_lastRegistered != null) { BlockAttackRegistry.remove(sk_lastRegistered); sk_lastRegistered = null; }
        sk_targetBlock = null;
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
