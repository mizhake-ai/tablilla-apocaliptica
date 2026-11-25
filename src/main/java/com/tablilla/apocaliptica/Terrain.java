package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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

    /**
     * Columna de una onda de choque. f = distancia relativa al radio de la onda (0 centro, 1 borde).
     * Cerca: arranca arboles enteros y chamusca. Medio: ademas arranca trozos de terreno. Lejos: solo deshoja y arranca cesped.
     */
    public static void blastColumn(ServerLevel level, int x, int z, double f, float groundChance, float fireChance, RandomSource rnd) {
        if (f <= 0.55) {
            scorchColumn(level, x, z, groundChance, fireChance, rnd);
            if (f >= 0.2 && rnd.nextFloat() < 0.07F) {
                if (!level.hasChunk(x >> 4, z >> 4)) return;
                int top = surfaceTop(level, x, z);
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                for (int k = 0; k < 2; k++) {
                    p.set(x, top - k, z);
                    BlockState st = level.getBlockState(p);
                    if (breakable(level, p, st) && st.getFluidState().isEmpty()) clear(level, p);
                }
            }
            return;
        }
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int top = surfaceTop(level, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top + 22; y >= top - 14; y--) {
            p.set(x, y, z);
            BlockState st = level.getBlockState(p);
            if (st.isAir() || st.getDestroySpeed(level, p) < 0 || !st.getFluidState().isEmpty()) continue;
            if (st.is(BlockTags.LEAVES) || st.getCollisionShape(level, p).isEmpty()) clear(level, p);
        }
        int top2 = surfaceTop(level, x, z);
        p.set(x, top2, z);
        if (level.getBlockState(p).is(Blocks.GRASS_BLOCK) && rnd.nextFloat() < 0.25F) {
            level.setBlock(p, Blocks.DIRT.defaultBlockState(), FLAGS);
        }
    }

    /** Empuja (mas fuerte cerca, mas suave lejos) todo lo que esta en el frente de la onda: criaturas, jugadores, objetos. */
    public static void shockPush(ServerLevel level, double x, double y, double z, double innerRad, double outerRad,
                                 double strength, double falloffR) {
        AABB box = new AABB(x - outerRad, y - 12, z - outerRad, x + outerRad, y + 120, z + outerRad);
        for (Entity e : level.getEntities((Entity) null, box, en -> en instanceof LivingEntity || en instanceof ItemEntity)) {
            if (e.isSpectator()) continue;
            double dx = e.getX() - x;
            double dz = e.getZ() - z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < innerRad || d > outerRad || d < 0.01) continue;
            double f = Math.max(0.2, 1.0 - d / falloffR);
            double s = strength * 1.5 * f;
            e.setDeltaMovement(e.getDeltaMovement().add(dx / d * s, s * 0.45, dz / d * s));
            e.hurtMarked = true;
        }
    }

    /** Tierra levantada alrededor de un crater: el borde se alza como un anillo de escombros. */
    public static void raiseRim(ServerLevel level, int x, int z, int rc, double heightFactor, RandomSource rnd) {
        double outer = rc * 1.5;
        int range = (int) Math.ceil(outer);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < rc * 0.92 || d > outer) continue;
                int px = x + dx;
                int pz = z + dz;
                if (!level.hasChunk(px >> 4, pz >> 4)) continue;
                double k = 1.0 - (d - rc * 0.92) / (outer - rc * 0.92);
                int h = (int) Math.round(rc * heightFactor * k * (0.5 + rnd.nextDouble()));
                if (h < 1) continue;
                int top = surfaceTop(level, px, pz);
                p.set(px, top, pz);
                BlockState base = level.getBlockState(p);
                if (!breakable(level, p, base) || !base.getFluidState().isEmpty()) continue;
                BlockState fill = rnd.nextFloat() < 0.3F ? Blocks.COARSE_DIRT.defaultBlockState() : base;
                for (int y = 1; y <= h; y++) {
                    p.set(px, top + y, pz);
                    if (level.getBlockState(p).isAir()) level.setBlock(p, fill, FLAGS);
                }
            }
        }
    }

    /** Tierra quemada: anillo de suelo chamuscado (con arboles arrancados) entre rIn y rOut. */
    public static void burnRing(ServerLevel level, int x, int z, int rIn, int rOut, float groundChance, float fireChance, RandomSource rnd) {
        int range = rOut;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < rIn || d > rOut) continue;
                scorchColumn(level, x + dx, z + dz, groundChance, fireChance, rnd);
            }
        }
    }

    private static final Block[] RUIN = {Blocks.MAGMA_BLOCK, Blocks.MAGMA_BLOCK, Blocks.MAGMA_BLOCK, Blocks.OBSIDIAN, Blocks.OBSIDIAN,
            Blocks.BLACKSTONE, Blocks.COARSE_DIRT, Blocks.GLASS, Blocks.NETHERRACK, Blocks.CRYING_OBSIDIAN, Blocks.TINTED_GLASS};
    private static final Block[] ORES = {Blocks.IRON_BLOCK, Blocks.COAL_BLOCK, Blocks.COPPER_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE};

    /**
     * Suelo de desastre: cubos sueltos de magma, obsidiana, cristal, tierra quemada, algun mineral, lava y fuego,
     * repartidos en una corona entre rIn y rOut. Los diamantes son muy pocos.
     */
    public static void scatterRuin(ServerLevel level, int cx, int cz, int rIn, int rOut, double perThousandColumns,
                                   double diamondChance, boolean ores, RandomSource rnd) {
        double area = Math.PI * ((double) rOut * rOut - (double) rIn * rIn);
        int n = (int) (area * perThousandColumns / 1000.0);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rIn * (double) rIn + rnd.nextDouble() * (rOut * (double) rOut - rIn * (double) rIn));
            int x = cx + (int) Math.round(Math.cos(a) * d);
            int z = cz + (int) Math.round(Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = surfaceTop(level, x, z);
            p.set(x, top, z);
            BlockState cur = level.getBlockState(p);
            if (!breakable(level, p, cur) || !cur.getFluidState().isEmpty()) continue;
            float f = rnd.nextFloat();
            BlockState put;
            if (rnd.nextDouble() < diamondChance) put = Blocks.DIAMOND_BLOCK.defaultBlockState();
            else if (ores && f < 0.12F) put = ORES[rnd.nextInt(ORES.length)].defaultBlockState();
            else if (f < 0.20F) put = Blocks.LAVA.defaultBlockState();
            else put = RUIN[rnd.nextInt(RUIN.length)].defaultBlockState();
            level.setBlock(p, put, FLAGS);
            // a veces es un monton de 2-3 cubos
            if (rnd.nextFloat() < 0.35F && !put.is(Blocks.LAVA)) {
                p.set(x, top + 1, z);
                if (level.getBlockState(p).isAir()) level.setBlock(p, put, FLAGS);
            }
            if (rnd.nextFloat() < 0.10F) {
                p.set(x, top + 2, z);
                if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.FIRE.defaultBlockState(), FLAGS);
            }
        }
    }

    /** Un cubo de ruina (magma, obsidiana, cristal...) sobre la superficie de una columna. */
    public static void ruinColumn(ServerLevel level, int x, int z, double diamondChance, RandomSource rnd) {
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = surfaceTop(level, x, z);
        p.set(x, top, z);
        BlockState cur = level.getBlockState(p);
        if (!breakable(level, p, cur) || !cur.getFluidState().isEmpty()) return;
        BlockState put = rnd.nextDouble() < diamondChance ? Blocks.DIAMOND_BLOCK.defaultBlockState()
                : RUIN[rnd.nextInt(RUIN.length)].defaultBlockState();
        level.setBlock(p, put, FLAGS);
        if (rnd.nextFloat() < 0.3F) {
            p.set(x, top + 1, z);
            if (level.getBlockState(p).isAir()) level.setBlock(p, put, FLAGS);
        }
    }

    /** Derriba las montanas: corta todo lo que sobresale por encima de maxY (con un poco de relieve). */
    public static void flattenColumn(ServerLevel level, int x, int z, int maxY, RandomSource rnd) {
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int top = surfaceTop(level, x, z);
        int lim = maxY + rnd.nextInt(3);
        if (top > lim) cutColumn(level, x, z, top + 25, lim + 1);
    }
}
