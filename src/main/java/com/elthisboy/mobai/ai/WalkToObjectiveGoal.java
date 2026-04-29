package com.elthisboy.mobai.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Goal personalizado: caminar hacia las coordenadas objetivo del pueblo/base.
 *
 * Prioridad BAJA — solo actúa cuando el mob no tiene un jugador como target.
 * En cuanto detecta un jugador cerca (por su followRange normal), el AI vanilla
 * toma el control y persigue al jugador normalmente.
 *
 * Uso: se añade al mob al spawnear desde WaveManager si hay objectivePos definido.
 *
 * Registro en el JSON de oleada:
 *   "objectiveX": 100, "objectiveY": 64, "objectiveZ": 200
 */
public class WalkToObjectiveGoal extends Goal {

    private final MobEntity mob;
    private final BlockPos  objective;
    private final double    speed;
    private int             recalcTimer = 0;

    public WalkToObjectiveGoal(MobEntity mob, BlockPos objective, double speed) {
        this.mob       = mob;
        this.objective = objective;
        this.speed     = speed;
        // Este goal controla movimiento XZ
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    /** Solo activo si el mob no tiene target (jugador o entidad hostil) */
    @Override
    public boolean canStart() {
        return mob.getTarget() == null
            && !mob.getNavigation().isFollowingPath()
            && distanceToObjectiveSq() > 4.0; // no hacer nada si ya llegó
    }

    @Override
    public boolean shouldContinue() {
        // Parar si hay un target o llegamos al destino
        return mob.getTarget() == null && distanceToObjectiveSq() > 4.0;
    }

    @Override
    public void start() {
        navigateToObjective();
    }

    @Override
    public void tick() {
        // Recalcular path cada 2 segundos por si el mob se atascó
        recalcTimer--;
        if (recalcTimer <= 0) {
            recalcTimer = 40;
            navigateToObjective();
        }

        // Mirar hacia el objetivo
        mob.getLookControl().lookAt(
            objective.getX() + 0.5,
            objective.getY(),
            objective.getZ() + 0.5
        );
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        recalcTimer = 0;
    }

    private void navigateToObjective() {
        mob.getNavigation().startMovingTo(
            objective.getX() + 0.5,
            objective.getY(),
            objective.getZ() + 0.5,
            speed
        );
    }

    private double distanceToObjectiveSq() {
        return mob.getBlockPos().getSquaredDistance(objective);
    }
}
