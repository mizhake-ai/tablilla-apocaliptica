package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
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

    /** Cosas fragiles que una onda de choque arranca: hojas, troncos, madera, lana y todo lo que no tiene colision. */
    public static boolean flimsy(ServerLevel level, BlockState st, BlockPos p) {
        if (st.isAir()) return false;
        if (st.getDestroySpeed(level, p) < 0) return false;
        if (!st.getFluidState().isEmpty()) return false;
        return st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.PLANKS)
                || st.is(BlockTags.WOODEN_STAIRS) || st.is(BlockTags.WOODEN_SLABS) || st.is(BlockTags.WOODEN_DOORS)
                || st.is(BlockTags.WOODEN_FENCES) || st.is(BlockTags.WOOL) || st.is(BlockTags.BEDS)
                || st.getCollisionShape(level, p).isEmpty();
    }

    /** Arranca arboles y cosas fragiles de una columna, chamusca el suelo y a veces deja fuego. */
    public static void scorchColumn(ServerLevel level, int x, int z, float groundChance, float fireChance, RandomSource rnd) {
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int top = surfaceTop(level, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top + 22; y >= top - 14; y--) {
            p.set(x, y, z);
            BlockState st = level.getBlockState(p);
            if (flimsy(level, st, p)) clear(level, p);
        }
        int top2 = surfaceTop(level, x, z);
        p.set(x, top2, z);
        BlockState ground = level.getBlockState(p);
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.PODZOL)
                || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.ROOTED_DIRT)) {
            if (rnd.nextFloat() < groundChance) {
                level.setBlock(p, (rnd.nextFloat() < 0.12F ? Blocks.BLACKSTONE : Blocks.COARSE_DIRT).defaultBlockState(), FLAGS);
            }
        }
        if (fireChance > 0.0F && rnd.nextFloat() < fireChance) {
            p.set(x, top2 + 1, z);
            if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.FIRE.defaultBlockState(), FLAGS);
        }
    }

    /** Chamusca un circulo entero. */
    public static void scorch(ServerLevel level, int cx, int cz, int radius, float groundChance, float fireChance, RandomSource rnd) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                scorchColumn(level, cx + dx, cz + dz, groundChance, fireChance, rnd);
            }
        }
    }

    /** Vacia una columna desde top hasta floor (incluido), sin tocar lo irrompible. */
    public static void cutColumn(ServerLevel level, int x, int z, int top, int floor) {
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int f = Math.max(floor, level.getMinBuildHeight() + 1);
        for (int y = Math.min(top, level.getMaxBuildHeight() - 1); y >= f; y--) {
            p.set(x, y, z);
            BlockState st = level.getBlockState(p);
            if (!breakable(level, p, st)) continue;
            clear(level, p);
        }
    }
}
