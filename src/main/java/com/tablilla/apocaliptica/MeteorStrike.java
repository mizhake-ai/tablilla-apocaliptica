package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Lluvia de Estrellas (Meteor Storm): decenas de meteoros que arrasan bosques y abren crateres por toda la zona. */
public class MeteorStrike extends BaseStrike {
    public static final int DURATION = 440;

    private final MeteorPlan plan;

    public MeteorStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.plan = new MeteorPlan(seed, scale);
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
            if (t == plan.impactTick(i)) impact(i);
        }
    }

    private void impact(int i) {
        int x = center.getX() + (int) Math.round(plan.ox[i]);
        int z = center.getZ() + (int) Math.round(plan.oz[i]);
        if (!level.hasChunk(x >> 4, z >> 4)) return;

        int rc = Math.max(3, plan.crater[i]);
        // primero se arrancan los arboles y se chamusca el suelo alrededor
        Terrain.scorch(level, x, z, Math.min(40, (int) Math.round(rc * 2.0)), 0.5F, 0.02F, rnd);

        int top = Terrain.surfaceTop(level, x, z);
        Terrain.hurtArea(level, x + 0.5, top + 1, z + 0.5, rc * 2.4, 40, 40F, 6, 1.8);

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
    }

    private BlockState liner() {
        float f = rnd.nextFloat();
        if (f < 0.40F) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (f < 0.65F) return Blocks.OBSIDIAN.defaultBlockState();
        if (f < 0.85F) return Blocks.NETHERRACK.defaultBlockState();
        return Blocks.BLACKSTONE.defaultBlockState();
    }
}
