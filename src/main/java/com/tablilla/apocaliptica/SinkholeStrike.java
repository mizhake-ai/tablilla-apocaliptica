package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** La Sima: nada cae del cielo; el suelo se agrieta y se hunde losa por losa, con su tierra y sus arboles, en una sima irregular hasta la bedrock. */
public class SinkholeStrike extends BaseStrike {
    public static final int DURATION = SinkholePlan.START + SinkholePlan.SPAN + 120;

    private final SinkholePlan plan;
    private int next = 0;

    public SinkholeStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.plan = new SinkholePlan(seed, scale);
    }

    @Override
    protected Laser laser() {
        return Laser.SINKHOLE;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        int done = 0;
        while (next < plan.count && plan.tick[next] <= t && done < 3) {
            collapse(plan.px[next], plan.pz[next]);
            next++;
            done++;
        }
    }

    private void collapse(int px, int pz) {
        int half = SinkholePlan.CELL / 2;
        int minY = level.getMinBuildHeight() + 1;
        for (int dx = -half; dx < half; dx++) {
            for (int dz = -half; dz < half; dz++) {
                int x = center.getX() + px + dx;
                int z = center.getZ() + pz + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                Terrain.cutColumn(level, x, z, Terrain.surfaceTop(level, x, z) + 32, minY);
            }
        }
    }
}
