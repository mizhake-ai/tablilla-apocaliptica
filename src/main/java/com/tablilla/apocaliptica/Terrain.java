package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ayudas para destruir terreno y herir seres vivos. */
public final class Terrain {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private Terrain() {}

    public static boolean breakable(ServerLevel level, BlockPos p, BlockState st) {
        return !st.isAir() && st.getDestroySpeed(level, p) >= 0;
    }

    public static void clear(ServerLevel level, BlockPos p) {
        level.setBlock(p, AIR, FLAGS);
    }

    /** Y del bloque mas alto (sin hojas) de esa columna. */
    public static int surfaceTop(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /** Hiere (y empuja hacia afuera) a los seres vivos dentro de un cilindro. */
    public static void hurtArea(ServerLevel level, double x, double y, double z, double radius, double height,
                                float damage, int fireSeconds, double push) {
        AABB box = new AABB(x - radius, y - 10, z - radius, x + radius, y + height, z + radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            Vec3 d = e.position().subtract(x, y, z);
            double dist = Math.sqrt(d.x * d.x + d.z * d.z);
            if (dist > radius) continue;
            float dmg = damage * (float) (1.0 - 0.6 * dist / radius);
            e.hurt(level.damageSources().inFire(), dmg);
            if (fireSeconds > 0) e.setSecondsOnFire(fireSeconds);
            if (push > 0 && dist > 0.01) {
                double nx = d.x / dist;
                double nz = d.z / dist;
                e.setDeltaMovement(e.getDeltaMovement().add(nx * push, push * 0.5, nz * push));
                e.hurtMarked = true;
            }
        }
    }
}
