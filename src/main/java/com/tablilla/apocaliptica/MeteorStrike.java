package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Lluvia de Estrellas: 72 meteoros arrasan la zona y, al final, uno del tamano de una colina abre un crater de unos 125 bloques. */
public class MeteorStrike extends BaseStrike {
    public static final int DURATION = 640;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        if (d < 0.35) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.65) return r.nextFloat() < 0.6F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
        return r.nextFloat() < 0.5F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
    };

    private final MeteorPlan plan;
    private BowlCrater giant;
    private BlastWave giantWave;
    private int giantPerTick = 1;
    private int giantStart = -1;
    private final int giantWaveR;

    public MeteorStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.plan = new MeteorPlan(seed, scale);
        this.giantWaveR = Mth.clamp(Math.round(200 * scale), 60, 260);
    }

    @Override
    protected Laser laser() {
        return Laser.METEOR;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        for (int i = 0; i < MeteorPlan.COUNT; i++) {
            if (t != plan.impactTick(i)) continue;
            if (plan.giant(i)) {
                startGiant(i);
            } else {
                impact(i);
            }
        }
        if (giant != null && !giant.done()) giant.step(giantPerTick);
        if (giantWave != null && !giantWave.done() && giantStart >= 0) {
            double k = Math.min(1.0, (t - giantStart) / 110.0);
            giantWave.sweep(giantWaveR * (1.0 - (1.0 - k) * (1.0 - k)));
        }
    }

    /** El meteoro de la extincion: un crater enorme, una onda que arrasa todo y un empujon brutal. */
    private void startGiant(int i) {
        giantStart = t;
        int r = Mth.clamp(plan.crater[i], 20, 120);
        giant = new BowlCrater(level, center, r, Mth.clamp(Math.round(r * 0.65F), 14, 90), LINER).walls(LINER);
        giantPerTick = (int) Math.ceil(giant.total() / 100.0);
        giantWave = new BlastWave(level, center, giantWaveR, 0.55F, 0.02F);
        Terrain.hurtArea(level, cx(), cy(), cz(), 170 * scale, 500, 300F, 15, 6.0);
    }

    private void impact(int i) {
        int x = center.getX() + (int) Math.round(plan.ox[i]);
        int z = center.getZ() + (int) Math.round(plan.oz[i]);
        if (!level.hasChunk(x >> 4, z >> 4)) return;

        int rc = Math.max(3, plan.crater[i]);
        boolean big = plan.big[i];
        // primero se arrancan los arboles y se chamusca el suelo alrededor
        Terrain.scorch(level, x, z, Math.min(40, (int) Math.round(rc * 2.0)), 0.5F, 0.02F, rnd);

        int top = Terrain.surfaceTop(level, x, z);
        // onda de choque: empuja a todo lo que anda cerca
        Terrain.hurtArea(level, x + 0.5, top + 1, z + 0.5, rc * (big ? 6.0 : 4.0), 60, big ? 60F : 30F, 6, big ? 3.2 : 2.0);

        BlockPos c = new BlockPos(x, top + rc / 4, z);
        int r2 = rc * rc;
        double shell = rc - 1.6;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -rc; dx <= rc; dx++) {
            for (int dy = -rc; dy <= rc; dy++) {
                for (int dz = -rc; dz <= rc; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r2) continue;
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = level.getBlockState(p);
                    if (!Terrain.breakable(level, p, st)) continue;
                    if (Math.sqrt(d2) >= shell && p.getY() <= top) {
                        level.setBlock(p, liner(), Terrain.FLAGS);
                    } else {
                        Terrain.clear(level, p);
                    }
                }
            }
        }
        raiseRim(x, z, rc);
    }

    /** Tierra levantada alrededor del crater: el borde se alza como un anillo de escombros. */
    private void raiseRim(int x, int z, int rc) {
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
                int h = (int) Math.round(rc * 0.22 * k * (0.5 + rnd.nextDouble()));
                if (h < 1) continue;
                int top = Terrain.surfaceTop(level, px, pz);
                p.set(px, top, pz);
                BlockState base = level.getBlockState(p);
                if (!Terrain.breakable(level, p, base) || !base.getFluidState().isEmpty()) continue;
                BlockState fill = rnd.nextFloat() < 0.3F ? Blocks.COARSE_DIRT.defaultBlockState() : base;
                for (int y = 1; y <= h; y++) {
                    p.set(px, top + y, pz);
                    if (level.getBlockState(p).isAir()) level.setBlock(p, fill, Terrain.FLAGS);
                }
            }
        }
    }

    private BlockState liner() {
        float f = rnd.nextFloat();
        if (f < 0.40F) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (f < 0.65F) return Blocks.OBSIDIAN.defaultBlockState();
        if (f < 0.85F) return Blocks.NETHERRACK.defaultBlockState();
        return Blocks.BLACKSTONE.defaultBlockState();
    }
}
