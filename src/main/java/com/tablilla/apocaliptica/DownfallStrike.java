package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La Caida: la nave-asteroide es derribada por misiles lanzados desde el horizonte, se estrella y se desliza dejando
 * un surco enorme con tierra levantada; su motor trasero revienta y un agujero negro pequeno se la traga junto con
 * parte del terreno. No queda nada salvo los crateres.
 */
public class DownfallStrike extends BaseStrike {
    public static final int MISSILE_START = 70;
    public static final int FIRST_HIT = 142;
    public static final int PLUNGE = 150;
    public static final int CRASH = 330;
    public static final int STOP = 410;
    public static final int RUPTURE = 430;
    public static final int PULL_END = 570;
    public static final int HOLE_END = 610;
    public static final int DURATION = 660;
    /** Posiciones (sin escala) sobre el eje X: donde toca el suelo, donde se detiene y donde queda el motor. */
    public static final double TOUCH_X = -200.0;
    public static final double STOP_X = 300.0;
    public static final double HOLE_X = STOP_X - 330.0;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        if (d < 0.30) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.65) return r.nextFloat() < 0.5F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        return r.nextFloat() < 0.4F ? Blocks.NETHERRACK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    };

    private BowlCrater crater;
    private BlastWave wave1;
    private BlastWave wave2;
    private SphereEater eater;
    private int perTick = 1;
    private int slice = -1;
    private final int waveR1;
    private final int waveR2;
    private final int furrowHalf;
    private final int holeR;
    private BlockPos holePos;
    private BlockPos touchPos;

    public DownfallStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.waveR1 = Mth.clamp(Math.round(200 * scale), 50, 300);
        this.waveR2 = Mth.clamp(Math.round(110 * scale), 40, 190);
        this.furrowHalf = Mth.clamp(Math.round(34 * scale), 12, 70);
        this.holeR = Mth.clamp(Math.round(36 * scale), 12, 60);
    }

    @Override
    protected Laser laser() {
        return Laser.DOWNFALL;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    private static double ease(double k) {
        return 1.0 - (1.0 - k) * (1.0 - k);
    }

    /** Avance de la nave por el surco (igual que en el cliente). */
    public static double skidProgress(int t) {
        double k = Math.max(0.0, Math.min(1.0, (t - CRASH) / (double) (STOP - CRASH)));
        return ease(k * k * (3.0 - 2.0 * k));
    }

    private static double wave(double since, double len, int radius) {
        double k = Math.min(1.0, since / len);
        return radius * (1.0 - (1.0 - k) * (1.0 - k));
    }

    private BlockPos surfaceAt(int x, int z) {
        return new BlockPos(x, Terrain.surfaceTop(level, x, z), z);
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;

        // el choque: la nave toca el suelo
        if (t == CRASH) {
            touchPos = surfaceAt(center.getX() + (int) Math.round(TOUCH_X * scale), center.getZ());
            Terrain.hurtArea(level, touchPos.getX() + 0.5, touchPos.getY() + 1.0, touchPos.getZ() + 0.5, 110 * scale, 500, 200F, 10, 5.0);
            int tr = Mth.clamp(Math.round(55 * scale), 14, 100);
            Terrain.raiseRim(level, touchPos.getX(), touchPos.getZ(), tr, 0.18, rnd);
            Terrain.scatterRuin(level, touchPos.getX(), touchPos.getZ(), tr, (int) (tr * 2.6), 22.0, 0.0006, false, rnd);
            crater = new BowlCrater(level, touchPos, Mth.clamp(Math.round(55 * scale), 14, 100), Mth.clamp(Math.round(26 * scale), 8, 60), LINER);
            perTick = (int) Math.ceil(crater.total() / 100.0);
            wave1 = new BlastWave(level, touchPos, waveR1, 0.65F, 0.02F).withPush(4.0, waveR1 * 1.1);
        }
        if (crater != null && !crater.done() && t > CRASH + 2) crater.step(perTick);
        if (wave1 != null && !wave1.done()) wave1.sweep(wave(t - CRASH, 90, waveR1));

        // el surco: se abre mientras la nave se desliza, con tierra levantada a los lados
        if (t > CRASH && t <= STOP + 2) {
            int len = (int) Math.round((STOP_X - TOUCH_X) * scale);
            int target = (int) Math.floor(len * skidProgress(t));
            while (slice < target) {
                slice++;
                cutFurrow(slice, len);
            }
            if (t % 6 == 0) {
                Terrain.hurtArea(level, center.getX() + 0.5 + TOUCH_X * scale + slice, cy(), cz(), 50 * scale, 300, 25F, 6, 2.5);
            }
        }

        // el motor trasero revienta y aparece el agujero negro pequeno
        if (t == RUPTURE) {
            int hx = center.getX() + (int) Math.round(HOLE_X * scale);
            int hz = center.getZ();
            holePos = new BlockPos(hx, Terrain.surfaceTop(level, hx, hz) + 3, hz);
            Terrain.hurtArea(level, hx + 0.5, holePos.getY(), hz + 0.5, 140 * scale, 500, 150F, 8, 5.0);
            eater = new SphereEater(level, holePos, holeR);
            Terrain.scatterRuin(level, hx, hz, holeR, (int) (holeR * 3.0), 18.0, 0.0006, false, rnd);
            wave2 = new BlastWave(level, holePos, waveR2, 0.5F, 0.01F).withPush(4.5, waveR2 * 1.1);
        }
        if (eater != null && t >= RUPTURE + 10 && !eater.done()) {
            double k = Math.min(1.0, (t - RUPTURE - 10) / 120.0);
            eater.eat(holeR * (k * k * (3.0 - 2.0 * k)));
        }
        if (wave2 != null && !wave2.done()) wave2.sweep(wave(t - RUPTURE, 80, waveR2));
        if (eater != null && eater.lineDone() == false && t >= RUPTURE + 135) eater.line(8);
    }

    private void cutFurrow(int sx, int len) {
        int x = center.getX() + (int) Math.round(TOUCH_X * scale) + sx;
        double frac = len <= 0 ? 0.0 : (double) sx / len;
        double dmax = (7.0 + 18.0 * frac) * scale;
        int ridgeW = Math.max(8, (int) Math.round(20.0 * scale));
        int burnW = Math.max(14, (int) Math.round(48.0 * scale));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dz = -(furrowHalf + burnW); dz <= furrowHalf + burnW; dz++) {
            int z = center.getZ() + dz;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int surface = Terrain.surfaceTop(level, x, z);
            int ad = Math.abs(dz);
            if (ad <= furrowHalf) {
                double k = (double) dz / (furrowHalf + 0.5);
                int dep = (int) Math.round(dmax * (1.0 - k * k));
                int floorY = surface - dep;
                Terrain.cutColumn(level, x, z, surface + 22, floorY);
                BlockPos fp = new BlockPos(x, floorY - 1, z);
                BlockState st = level.getBlockState(fp);
                if (Terrain.breakable(level, fp, st)) {
                    float f = rnd.nextFloat();
                    BlockState lining = f < 0.30F ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : (f < 0.50F ? Blocks.OBSIDIAN.defaultBlockState()
                            : (f < 0.70F ? Blocks.BLACKSTONE.defaultBlockState()
                            : (f < 0.78F ? Blocks.GLASS.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState())));
                    level.setBlock(fp, lining, Terrain.FLAGS);
                    if (rnd.nextFloat() < 0.012F) level.setBlock(fp, Blocks.DIAMOND_BLOCK.defaultBlockState(), Terrain.FLAGS);
                }
            } else {
                if (ad <= furrowHalf + ridgeW) {
                    // tierra levantada y empujada a ambos lados
                    double k = 1.0 - (double) (ad - furrowHalf) / ridgeW;
                    int h = (int) Math.round((5.0 + 7.0 * frac) * scale * k * (0.5 + rnd.nextDouble()));
                    if (h >= 1) {
                        p.set(x, surface, z);
                        BlockState base = level.getBlockState(p);
                        if (Terrain.breakable(level, p, base) && base.getFluidState().isEmpty()) {
                            BlockState fill = rnd.nextFloat() < 0.4F ? Blocks.COARSE_DIRT.defaultBlockState() : base;
                            for (int y = 1; y <= h; y++) {
                                p.set(x, surface + y, z);
                                if (level.getBlockState(p).isAir()) level.setBlock(p, fill, Terrain.FLAGS);
                            }
                        }
                    }
                }
                // tierra abrasada y arboles arrancados
                if (rnd.nextFloat() < 0.85F) Terrain.scorchColumn(level, x, z, 0.7F, 0.012F, rnd);
                if (rnd.nextFloat() < 0.02F) Terrain.ruinColumn(level, x, z, 0.0008, rnd);
            }
        }
    }
}
