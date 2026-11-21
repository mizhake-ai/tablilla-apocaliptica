package com.tablilla.apocaliptica;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** El Abismo (Black Hole): una esfera de oscuridad que se traga el terreno y arrastra todo hacia ella. */
public class BlackHoleStrike extends BaseStrike {
    public static final int EAT_START = 130;
    public static final int EAT_END = 380;
    public static final int DURATION = 450;

    private final int rMax;
    private final int n;
    private final BlockPos hole;
    private long[] keys;
    private int cursor = 0;

    public BlackHoleStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.rMax = Math.max(8, Math.min(60, Math.round(36 * scale)));
        this.n = 2 * rMax + 1;
        this.hole = center.above(5);
    }

    @Override
    protected Laser laser() {
        return Laser.BLACKHOLE;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == EAT_START) build();
        if (keys != null && t >= EAT_START && t <= EAT_END) eat();
        if (t >= 100 && t <= EAT_END + 5) pull();
        if (t == EAT_END) Terrain.hurtArea(level, cx(), cy(), cz(), 60 * scale, 400, 60F, 0, 4.0);
    }

    private void build() {
        int r = rMax;
        long[] tmp = new long[(int) (4.3 * r * r * r) + 64];
        int cnt = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r * r) continue;
                    long idx = ((long) (dx + r) * n + (dy + r)) * n + (dz + r);
                    tmp[cnt++] = ((long) d2 << 32) | idx;
                }
            }
        }
        keys = Arrays.copyOf(tmp, cnt);
        Arrays.sort(keys);
    }

    private void eat() {
        double k = Math.min(1.0, (double) (t - EAT_START) / (EAT_END - EAT_START));
        double sm = k * k * (3 - 2 * k);
        double now = rMax * sm;
        long lim = (long) (now * now);
        if (t >= EAT_END) lim = Long.MAX_VALUE >> 1;
        int done = 0;
        while (cursor < keys.length && (keys[cursor] >>> 32) <= lim && done < 6000) {
            int idx = (int) (keys[cursor] & 0xFFFFFFFFL);
            int dz = idx % n - rMax;
            idx /= n;
            int dy = idx % n - rMax;
            int dx = idx / n - rMax;
            cursor++;
            done++;
            BlockPos p = hole.offset(dx, dy, dz);
            if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            BlockState st = level.getBlockState(p);
            if (Terrain.breakable(level, p, st)) Terrain.clear(level, p);
        }
    }

    private void pull() {
        double hx = cx();
        double hy = cy() + 5;
        double hz = cz();
        double range = 2.6 * rMax;
        AABB box = new AABB(hx - range, hy - range, hz - range, hx + range, hy + range, hz + range);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            Vec3 d = new Vec3(hx, hy, hz).subtract(e.position());
            double dist = d.length();
            if (dist < 0.01 || dist > range) continue;
            double str = 0.05 + 0.25 * (1.0 - dist / range);
            e.setDeltaMovement(e.getDeltaMovement().add(d.scale(str / dist)));
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
            if (dist < 5.0) e.hurt(level.damageSources().generic(), 1000F);
        }
    }
}
