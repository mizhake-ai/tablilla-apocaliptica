package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Lanza de Ra (Plasma): un rayo de plasma ambar baja del cielo con relampagos ramificados,
 * y si el dano al terreno esta activo funde un crater en obsidiana, magma y vidrio.
 */
public class PlasmaStrike implements Strike {
    private static final int CHARGE = 30;   // 1.5 s
    private static final int BEAM = 90;     // 4.5 s
    private static final int AFTER = 40;    // 2 s
    private static final int RADIUS = 24;
    private static final int DEPTH = 13;

    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.15F), 2.5F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.6F, 0.3F, 1.0F), 1.6F);

    private final ServerLevel level;
    private final BlockPos center;
    private final RandomSource rnd;
    private final List<int[]> columns = new ArrayList<>();
    private int carved = 0;
    private int t = 0;

    public PlasmaStrike(ServerLevel level, BlockPos center) {
        this.level = level;
        this.center = center;
        this.rnd = level.getRandom();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz <= RADIUS * RADIUS) columns.add(new int[] {dx, dz});
            }
        }
        columns.sort(Comparator.comparingInt((int[] c) -> c[0] * c[0] + c[1] * c[1]));
    }

    @Override
    public boolean tick() {
        t++;
        double x = center.getX() + 0.5;
        double z = center.getZ() + 0.5;
        double y = center.getY() + 1.0;
        int top = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 170);

        if (t == 1) Fx.sound(level, x, y, z, SoundEvents.BEACON_ACTIVATE, 10.0F, 0.5F);

        if (t <= CHARGE) {
            charge(x, y, z, top);
        } else if (t <= CHARGE + BEAM) {
            beam(x, y, z, top, t - CHARGE);
        } else {
            after(x, y, z, t - CHARGE - BEAM);
        }

        if (StrikeManager.terrainDamage && t > CHARGE + 4 && t <= CHARGE + BEAM + 10) carveStep();
        return t >= CHARGE + BEAM + AFTER;
    }

    // ---------------------------------------------------------------- carga
    private void charge(double x, double y, double z, int top) {
        double k = (double) t / CHARGE;
        double r = 10.0 * (1.0 - k);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16 + t * 0.2;
            Fx.particles(level, AMBER, x + Math.cos(a) * r, y + 1.0, z + Math.sin(a) * r, 1, 0.1, 0.2, 0.1, 0.0);
        }
        double head = top - (top - y) * k;
        Fx.particles(level, ParticleTypes.END_ROD, x, head, z, 10, 0.25, 1.5, 0.25, 0.0);
        Fx.particles(level, VIOLET, x, head, z, 4, 0.3, 1.5, 0.3, 0.0);
    }

    // ---------------------------------------------------------------- rayo
    private void beam(double x, double y, double z, int top, int bt) {
        if (bt == 1) {
            Fx.sound(level, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, 24.0F, 0.6F);
            Fx.sound(level, x, y, z, SoundEvents.GENERIC_EXPLODE, 24.0F, 0.5F);
            Fx.particles(level, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        }

        // la columna de plasma
        for (int yy = center.getY(); yy < top; yy += 14) {
            Fx.particles(level, AMBER, x, yy + 7, z, 10, 0.35, 5.0, 0.35, 0.0);
            Fx.particles(level, ParticleTypes.END_ROD, x, yy + 7, z, 6, 0.2, 6.0, 0.2, 0.0);
        }
        // nucleo brillante cerca del suelo
        Fx.particles(level, ParticleTypes.END_ROD, x, y + 3, z, 25, 0.6, 3.0, 0.6, 0.02);

        // relampagos ramificados
        if (bt % 3 == 0) fork(x, y, z, top);

        // golpe en el suelo
        Fx.particles(level, ParticleTypes.LAVA, x, y, z, 6, 3.0, 0.5, 3.0, 0.0);
        Fx.particles(level, ParticleTypes.LARGE_SMOKE, x, y + 1, z, 12, 4.0, 1.0, 4.0, 0.05);
        Fx.particles(level, ParticleTypes.FLAME, x, y + 0.5, z, 15, 5.0, 0.4, 5.0, 0.1);

        // onda expansiva
        if (bt <= 20) {
            double r = bt * 1.8;
            int n = (int) Math.min(60, r * 2 + 8);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                Fx.particles(level, ParticleTypes.CLOUD, x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r,
                        2, 0.0, 0.2, 0.0, 0.02);
            }
        }

        // rayo visual y sonido de fondo
        if (bt % 20 == 0) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(center));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            Fx.sound(level, x, y, z, SoundEvents.BEACON_AMBIENT, 16.0F, 0.5F);
        }

        // dano a los que esten muy cerca (solo con terreno activo)
        if (StrikeManager.terrainDamage && bt % 5 == 0) {
            AABB box = new AABB(x - 6, y - 6, z - 6, x + 6, y + 100, z + 6);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                e.hurt(level.damageSources().inFire(), 8.0F);
                e.setSecondsOnFire(6);
            }
        }
    }

    private void fork(double x, double y, double z, int top) {
        double span = Math.max(1.0, (top - y - 20) * 0.7);
        for (int b = 0; b < 3; b++) {
            double py = y + 8 + rnd.nextDouble() * span;
            double ang = rnd.nextDouble() * Math.PI * 2;
            double px = x;
            double pz = z;
            for (int s = 0; s < 12; s++) {
                ang += (rnd.nextDouble() - 0.5) * 0.9;
                px += Math.cos(ang) * 2.2;
                pz += Math.sin(ang) * 2.2;
                py -= 1.0 + rnd.nextDouble() * 1.5;
                Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, px, py, pz, 2, 0.05, 0.05, 0.05, 0.0);
                Fx.particles(level, VIOLET, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    // ---------------------------------------------------------------- despues
    private void after(double x, double y, double z, int at) {
        if (at == 1) Fx.sound(level, x, y, z, SoundEvents.BEACON_DEACTIVATE, 10.0F, 0.6F);
        Fx.particles(level, ParticleTypes.LARGE_SMOKE, x, y + 2, z, 10, 5.0, 1.5, 5.0, 0.03);
        if (at % 4 == 0) Fx.particles(level, ParticleTypes.FLAME, x, y + 0.5, z, 12, 6.0, 0.3, 6.0, 0.02);
    }

    // ---------------------------------------------------------------- terreno
    private void carveStep() {
        int perTick = (int) Math.ceil(columns.size() / 70.0);
        for (int k = 0; k < perTick && carved < columns.size(); k++) {
            int[] c = columns.get(carved++);
            carveColumn(c[0], c[1]);
        }
    }

    private void carveColumn(int dx, int dz) {
        int cx = center.getX() + dx;
        int cz = center.getZ() + dz;
        if (!level.hasChunk(cx >> 4, cz >> 4)) return;

        double d = Math.sqrt((double) (dx * dx + dz * dz)) / RADIUS;
        int depth = (int) Math.round(DEPTH * Math.sqrt(Math.max(0.0, 1.0 - d * d)));
        int floorY = center.getY() - depth;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        int topY = Math.min(level.getMaxBuildHeight() - 1, Math.max(center.getY(), surface) + 20);

        BlockState air = Blocks.AIR.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int yy = topY; yy >= floorY; yy--) {
            p.set(cx, yy, cz);
            BlockState st = level.getBlockState(p);
            if (st.isAir() || st.getDestroySpeed(level, p) < 0) continue;
            level.setBlock(p, air, flags);
        }

        if (d > 0.88) return;
        p.set(cx, floorY - 1, cz);
        BlockState below = level.getBlockState(p);
        if (below.isAir() || below.getDestroySpeed(level, p) < 0) return;

        BlockState fused;
        if (d < 0.30) {
            fused = Blocks.MAGMA_BLOCK.defaultBlockState();
        } else if (d < 0.65) {
            fused = Blocks.OBSIDIAN.defaultBlockState();
        } else {
            fused = rnd.nextFloat() < 0.55F ? Blocks.GLASS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        }
        level.setBlock(p, fused, flags);
    }
}
