package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ataque de PRUEBA de la Etapa 0: una columna de luz, un rayo y un crater chico. */
public class TestStrike implements Strike {
    private static final int FALL_TICKS = 40;
    private static final int END_TICKS = 70;
    private static final int CRATER_RADIUS = 5;

    private final ServerLevel level;
    private final BlockPos pos;
    private int t = 0;

    public TestStrike(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
    }

    @Override
    public boolean tick() {
        t++;
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double cy = pos.getY();

        if (t <= FALL_TICKS) {
            double top = Math.min(level.getMaxBuildHeight() - 1, cy + 120);
            double y = top - (top - cy) * ((double) t / FALL_TICKS);
            level.sendParticles(ParticleTypes.END_ROD, cx, y, cz, 8, 0.2, 0.6, 0.2, 0.0);
        }

        if (t == FALL_TICKS) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(pos));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            level.explode(null, cx, cy + 1, cz, 4.0F, Level.ExplosionInteraction.NONE);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy + 1, cz, 1, 0, 0, 0, 0);
            if (StrikeManager.terrainDamage) carve();
        }

        return t >= END_TICKS;
    }

    private void carve() {
        BlockState air = Blocks.AIR.defaultBlockState();
        int r = CRATER_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (state.isAir() || state.getDestroySpeed(level, p) < 0) continue;
                    level.setBlock(p, air, 2);
                }
            }
        }
    }
}
