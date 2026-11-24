package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.DownfallStrike;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * La Caida: siete misiles lanzados desde el horizonte derriban la nave-asteroide (tres la alcanzan). Se estrella,
 * se desliza abriendo un surco, su motor trasero revienta y un agujero negro pequeno se la traga entera.
 */
public class DownfallFx implements FxEffect {
    private static final int MISSILE_START = DownfallStrike.MISSILE_START;
    private static final int FIRST_HIT = DownfallStrike.FIRST_HIT;
    private static final int PLUNGE = DownfallStrike.PLUNGE;
    private static final int CRASH = DownfallStrike.CRASH;
    private static final int STOP = DownfallStrike.STOP;
    private static final int RUPTURE = DownfallStrike.RUPTURE;
    private static final int PULL_END = DownfallStrike.PULL_END;
    private static final int HOLE_END = DownfallStrike.HOLE_END;
    private static final int D = DownfallStrike.DURATION;
    private static final int MISSILES = 7;
    private static final int FLIGHT = 60;
    private static final int GROUPS = 6;
    private static final float TX = (float) DownfallStrike.TOUCH_X;
    private static final float SX = (float) DownfallStrike.STOP_X;
    private static final float HX = (float) DownfallStrike.HOLE_X;

    private static final Block[] CHUNKS = {Blocks.DRIPSTONE_BLOCK, Blocks.COBBLED_DEEPSLATE, Blocks.IRON_BLOCK,
            Blocks.BLACKSTONE, Blocks.SMOOTH_BASALT, Blocks.SCULK};

    private final ShipModel ship = new ShipModel();
    private GroundMap ground;
    private List<Debris.Proto> crashProtos;
    private List<Debris.Proto> holeProtos;
    @SuppressWarnings("unchecked")
    private final List<Debris.Proto>[] furrow = new List[GROUPS];
    private final boolean[] furrowDone = new boolean[GROUPS];

    // misiles
    private boolean inited = false;
    private final float[] gx = new float[MISSILES];
    private final float[] gz = new float[MISSILES];
    private final float[] tx = new float[MISSILES];
    private final float[] ty = new float[MISSILES];
    private final float[] tz = new float[MISSILES];
    private final boolean[] hit = new boolean[MISSILES];

    @Override
    public int duration() {
        return D;
    }

    private static float ease(float k) {
        return 1.0F - (1.0F - k) * (1.0F - k);
    }

    /** Estado de la nave: {x, y, rz, rx, escala, visible}. */
    private static float[] st(float t, float q) {
        float x;
        float y;
        float rz;
        float rx = 0.0F;
        if (t <= PLUNGE) {
            x = lerp(-800.0F, -330.0F, ease(smooth(t, 0, PLUNGE)));
            y = 300.0F;
            rz = -0.05F * smooth(t, FIRST_HIT, PLUNGE);
            rx = 0.10F * sin(t * 0.06F);
        } else if (t <= CRASH) {
            float k = (t - PLUNGE) / (float) (CRASH - PLUNGE);
            x = lerp(-330.0F, TX * q, k);
            y = lerp(300.0F, 40.0F * q, (float) Math.pow(k, 1.5));
            rz = -(0.05F + 0.30F * k);
            rx = 0.10F * sin(t * 0.06F) * (1.0F - k);
        } else {
            float j = smooth(t, CRASH, STOP);
            x = lerp(TX * q, SX * q, ease(j));
            y = lerp(40.0F * q, 4.0F * q, j);
            rz = lerp(-0.35F, -0.06F, j);
        }
        float kp = smooth(t, RUPTURE + 10, PULL_END);
        float kk = (float) Math.pow(kp, 1.4);
        x = lerp(x, HX * q, kk);
        y = lerp(y, 14.0F * q, kk);
        rz += kp * 2.2F;
        rx += kp * 3.0F;
        float qe = q * (1.0F - 0.985F * kp);
        return new float[] {x, y, rz, rx, qe, t < PULL_END ? 1.0F : 0.0F};
    }

    private void init(long seed, float q) {
        inited = true;
        Random r = new Random(seed * 17L + 3L);
        for (int m = 0; m < MISSILES; m++) {
            float a = m * TAU / MISSILES + r.nextFloat() * 0.5F + 0.3F;
            float d = 420.0F + r.nextFloat() * 80.0F;
            gx[m] = cos(a) * d;
            gz[m] = sin(a) * d;
            hit[m] = m == 2 || m == 4 || m == 6;
            float th = MISSILE_START + m * 6 + FLIGHT;
            float[] s = st(th, q);
            if (hit[m]) {
                tx[m] = s[0] + (r.nextFloat() - 0.5F) * 240.0F * q;
                ty[m] = s[1] - 10.0F * q;
                tz[m] = (r.nextFloat() - 0.5F) * 60.0F * q;
            } else {
                float sg = r.nextBoolean() ? 1.0F : -1.0F;
                tx[m] = s[0] + sg * (110.0F + r.nextFloat() * 60.0F) * q;
                ty[m] = s[1] + (30.0F + r.nextFloat() * 40.0F) * q;
                tz[m] = (r.nextFloat() < 0.5F ? -1.0F : 1.0F) * (60.0F + r.nextFloat() * 60.0F) * q;
            }
        }
    }

    private float mx(int m, float tau) {
        return gx[m] + (tx[m] - gx[m]) * tau;
    }

    private float my(int m, float tau) {
        return (ty[m]) * tau + 55.0F * sin(3.14159F * tau);
    }

    private float mz(int m, float tau) {
        return gz[m] + (tz[m] - gz[m]) * tau;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        float q = s;
        if (t == 1) {
            float half = Math.min(280.0F, 270.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(5.0F, half / 45.0F));
            crashProtos = Debris.sample(level, x + TX * q, z, 4.0 * s, 90.0 * s, 300, 0.5F, r);
            for (int k = 0; k < GROUPS; k++) {
                float gxk = (TX + (SX - TX) * k / (GROUPS - 1)) * q;
                furrow[k] = Debris.sample(level, x + gxk, z, 0.0, 26.0 * s, 26, 0.5F, r);
            }
            FxUtil.snd(level, "mothership.arrival", x, y, z, 16.0F, 1.0F);
        }
        if (!inited) init(seed, q);
        if (t == 60) FxUtil.snd(level, "mothership.hum", x, y, z, 16.0F, 1.0F);
        if (t == RUPTURE - 1) holeProtos = Debris.sample(level, x + HX * q, z, 2.0, 50.0 * s, 260, 0.3F, r);

        float tt = t;
        float[] sh = st(tt, q);
        float[] sn = st(tt + 1, q);
        double sxp = x + sh[0];
        double syp = y + sh[1];
        double vx = sn[0] - sh[0];
        double vy = sn[1] - sh[1];

        // ------------------------------------------------ misiles
        for (int m = 0; m < MISSILES; m++) {
            int tl = MISSILE_START + m * 6;
            int th = tl + FLIGHT;
            if (t == tl) {
                FxUtil.snd(level, (m % 2 == 0) ? "meteor.whoosh1" : "meteor.whoosh2", x + gx[m], y + 5, z + gz[m], 14.0F, 0.9F);
                FxUtil.snd(level, "meteor.approach", x, y, z, 16.0F, 1.2F);
            }
            if (t >= tl && t < th) {
                float tau = (t - tl) / (float) FLIGHT;
                for (int k = 0; k < 4; k++) {
                    float tk = Math.max(0.0F, tau - k * 0.012F);
                    double px = x + mx(m, tk);
                    double py = y + my(m, tk);
                    double pz = z + mz(m, tk);
                    FxUtil.p(level, ParticleTypes.FLAME, px, py, pz, 0, 0, 0);
                    FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, py, pz, 0, 0.05, 0);
                    if (k == 0) FxUtil.p(level, ParticleTypes.LARGE_SMOKE, px, py, pz, 0, 0, 0);
                }
            }
            if (t == th) {
                double ex = x + tx[m];
                double ey = y + ty[m];
                double ez = z + tz[m];
                FxUtil.snd(level, "meteor.hit" + (1 + m % 3), ex, ey, ez, 14.0F, 0.9F);
                FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, hit[m] ? 1.0F : 1.3F);
                int n = hit[m] ? 8 : 3;
                for (int i = 0; i < n; i++) {
                    FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, ex + r.nextGaussian() * 14 * q, ey + r.nextGaussian() * 8 * q, ez + r.nextGaussian() * 14 * q, 0, 0, 0);
                }
                for (int i = 0; i < 60; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double b = (r.nextDouble() - 0.5) * Math.PI;
                    double sp = 1.0 + r.nextDouble() * 2.0;
                    FxUtil.p(level, ParticleTypes.FLAME, ex, ey, ez, Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp);
                }
            }
        }

        // ------------------------------------------------ nave en llamas y cayendo
        if (t >= FIRST_HIT && t < PULL_END) {
            for (int i = 0; i < 10; i++) {
                double ox = (r.nextDouble() - 0.5) * 400.0 * q;
                double oy = (r.nextDouble() - 0.4) * 80.0 * q;
                double oz = r.nextGaussian() * 40.0 * q;
                FxUtil.p(level, ParticleTypes.FLAME, sxp + ox, syp + oy, z + oz, 0, 0.1, 0);
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, sxp + ox, syp + oy, z + oz, 0, 0.1, 0);
                if (i % 3 == 0) FxUtil.p(level, ParticleTypes.LAVA, sxp + ox, syp + oy, z + oz, 0, 0, 0);
                if (i % 2 == 0 && t < STOP + 40) FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, sxp + ox, syp + oy, z + oz, 0, 0.12, 0);
            }
        }
        if (t >= FIRST_HIT && t <= STOP && t % 5 == 0) {
            double ox = (r.nextDouble() - 0.5) * 300.0 * q;
            double oz = r.nextGaussian() * 40.0 * q;
            double fx = sxp + ox;
            double fy = syp - 20.0 * q;
            float gh = 0.0F;
            if (ground != null && Math.abs(fx - x) < 260.0 && Math.abs(oz) < 260.0) gh = ground.at(fx - x, oz);
            BlockState bs = CHUNKS[r.nextInt(CHUNKS.length)].defaultBlockState();
            Debris.linear(bs, fx, fy, z + oz, vx * 0.5 + r.nextGaussian() * 0.3, -1.0 - r.nextDouble() * 1.5 + vy * 0.3,
                    r.nextGaussian() * 0.3, 3.0F + r.nextFloat() * 4.0F, y + gh, 400, r);
        }

        // ------------------------------------------------ choque y deslizamiento
        if (t == CRASH) {
            FxUtil.snd(level, "meteor.impact", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 0.9F);
            FxUtil.snd(level, "plasma.collapse", x, y, z, 16.0F, 0.9F);
            if (crashProtos != null) Debris.burst(crashProtos, x + TX * q, y, z, 2.6, 3.0, 30, 1.0F, 2.6F, 40.0 * s, 22.0 * s, r);
            BlockState[] parts = new BlockState[CHUNKS.length];
            for (int i = 0; i < parts.length; i++) parts[i] = CHUNKS[i].defaultBlockState();
            Debris.burst(Debris.synthetic(parts, x + TX * q, y + 10.0 * q, z, 30.0 * s, 110, r), x + TX * q, y, z, 1.6, 3.4, 8, 1.5F, 4.5F, 40.0 * s, 22.0 * s, r);
            for (int i = 0; i < 18; i++) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + TX * q + r.nextGaussian() * 40 * q, y + 2, z + r.nextGaussian() * 30 * q, 0, 0, 0);
            }
        }
        if (t > CRASH && t <= STOP) {
            for (int i = 0; i < 12; i++) {
                double ox = (r.nextDouble() - 0.5) * 360.0 * q;
                double oz = r.nextGaussian() * 22.0 * q;
                FxUtil.p(level, ParticleTypes.CLOUD, sxp + ox, y + 1, z + oz, 0, 0.2, 0);
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, sxp + ox, y + 2, z + oz, 0, 0.3, 0);
                if (i % 3 == 0) FxUtil.p(level, ParticleTypes.FLAME, sxp + ox, y + 1, z + oz, 0, 0.2, 0);
            }
            if (t % 5 == 0) FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, sxp + (r.nextDouble() - 0.5) * 300 * q, y + 2, z + r.nextGaussian() * 25 * q, 0, 0, 0);
            double rel = sh[0];
            for (int k = 0; k < GROUPS; k++) {
                float gxk = (TX + (SX - TX) * k / (GROUPS - 1)) * q;
                if (!furrowDone[k] && rel >= gxk && furrow[k] != null) {
                    furrowDone[k] = true;
                    Debris.burst(furrow[k], x + gxk, y, z, 1.6, 2.4, 5, 0.9F, 2.0F, 26.0 * s, 16.0 * s, r);
                }
            }
        }

        // ------------------------------------------------ el motor revienta: agujero negro pequeno
        double hx = x + HX * q;
        double hy = y + 14.0 * q;
        if (t == RUPTURE) {
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 0.8F);
            FxUtil.snd(level, "blackhole.fire", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "plasma.collapse", x, y, z, 16.0F, 0.9F);
            for (int i = 0; i < 16; i++) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, hx + r.nextGaussian() * 18 * q, hy + r.nextGaussian() * 10 * q, z + r.nextGaussian() * 18 * q, 0, 0, 0);
            }
            for (int i = 0; i < 120; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = (r.nextDouble() - 0.3) * Math.PI;
                double sp = 1.5 + r.nextDouble() * 2.5;
                FxUtil.p(level, ParticleTypes.FLAME, hx, hy, z, Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp);
            }
        }
        if (t == RUPTURE + 6 && holeProtos != null) {
            Debris.spiral(holeProtos, hx, hy, z, 120, 170, 1.4F, 3.0F, r);
        }
        if (t == RUPTURE + 40) FxUtil.snd(level, "blackhole.rumble", x, y, z, 16.0F, 1.0F);
        if (t >= RUPTURE && t < PULL_END) {
            int n = (int) (10 * smooth(t, RUPTURE, RUPTURE + 40));
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = (10.0 + r.nextDouble() * 45.0) * q;
                double c = Math.cos(a);
                double sn2 = Math.sin(a);
                double vxp = (-c * 0.6 - sn2 * 0.9) * 0.5;
                double vzp = (-sn2 * 0.6 + c * 0.9) * 0.5;
                FxUtil.p(level, ParticleTypes.SOUL_FIRE_FLAME, hx + c * d, hy + r.nextGaussian() * 4, z + sn2 * d, vxp, 0.0, vzp);
                if (i % 2 == 0) FxUtil.p(level, ParticleTypes.END_ROD, hx + c * d, hy + r.nextGaussian() * 4, z + sn2 * d, vxp, 0.0, vzp);
            }
        }
        if (t == PULL_END + 6) {
            FxUtil.snd(level, "blackhole.shutdown", x, y, z, 16.0F, 1.0F);
            for (int i = 0; i < 100; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = (r.nextDouble() - 0.5) * Math.PI;
                double sp = 1.5 + r.nextDouble() * 2.5;
                FxUtil.p(level, ParticleTypes.END_ROD, hx, hy, z, Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp);
            }
        }
        if (t > RUPTURE && t < D - 30 && t % 2 == 0) {
            FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + r.nextGaussian() * 60 * q, y + 2, z + r.nextGaussian() * 40 * q, 0, 0.3, 0);
        }
        if (t == D - 60) FxUtil.snd(level, "meteor.shutdown", x, y, z, 16.0F, 1.0F);
    }

    // ------------------------------------------------------------------ casco solido y agujero negro

    @Override
    public boolean usesHull() {
        return true;
    }

    private float holeRadius(float t, float q) {
        return 17.0F * q * smooth(t, RUPTURE, RUPTURE + 40) * (1.0F - smooth(t, PULL_END, HOLE_END));
    }

    @Override
    public void renderHull(VertexConsumer vc, PoseStack.Pose pose, float t, long seed, float s) {
        float[] a = st(t, s);
        if (a[5] > 0.5F) {
            float burn = 0.85F * smooth(t, FIRST_HIT, FIRST_HIT + 100);
            ShipModel.push(pose, a[0], a[1], 0, a[2], a[3]);
            ship.hull(vc, pose, seed, a[4], burn);
            ShipModel.pop(pose, a[0], a[1], 0, a[2], a[3]);
        }
        float hr = holeRadius(t, s);
        if (hr > 0.2F) {
            HullGeo.sphere(vc, pose, HullGeo.sprite(Blocks.BLACK_CONCRETE), HX * s, 14.0F * s, 0, hr, 12, 24, 0.04F, 0.04F, 0.06F);
        }
    }

    // ------------------------------------------------------------------ luces

    private void shock(VertexConsumer vc, Matrix4f m, float cx, float since, float q, float maxR, float speed) {
        for (int k = 0; k < 4; k++) {
            float rr = (since - k * 14.0F) * speed * q;
            if (rr > 1.0F && rr < maxR) {
                float fade = 1.0F - rr / maxR;
                float w = 3.0F + rr * 0.05F;
                int sides = Math.max(48, Math.min(160, (int) (rr * 0.6F)));
                FxGeo.terrainRing(vc, m, ground, cx, 0, rr - w, rr, sides, 0.9F + k, 1.0F, 0.75F, 0.4F, 0.0F, 0.75F * fade);
                FxGeo.terrainRing(vc, m, ground, cx, 0, rr, rr + w, sides, 0.9F + k, 1.0F, 0.75F, 0.4F, 0.75F * fade, 0.0F);
                FxGeo.terrainWall(vc, m, ground, cx, 0, rr, 22.0F + rr * 0.12F, sides, 0.6F, 1.0F, 0.6F, 0.3F, 0.55F * fade, 0.0F);
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float q = s;
        if (!inited) return;

        // misiles y explosiones
        for (int mi = 0; mi < MISSILES; mi++) {
            int tl = MISSILE_START + mi * 6;
            int th = tl + FLIGHT;
            if (t >= tl && t < th) {
                float tau = (t - tl) / (float) FLIGHT;
                float tb = Math.max(0.0F, tau - 0.10F);
                float x1 = mx(mi, tau);
                float y1 = my(mi, tau);
                float z1 = mz(mi, tau);
                float x0 = mx(mi, tb);
                float y0 = my(mi, tb);
                float z0 = mz(mi, tb);
                FxGeo.tube(vc, m, x0, y0, z0, x1, y1, z1, 0.3F, 3.5F, 8, 1.0F, 0.55F, 0.15F, 0.0F, 0.75F);
                FxGeo.tube(vc, m, x0, y0, z0, x1, y1, z1, 0.15F, 1.6F, 8, 1.0F, 0.95F, 0.8F, 0.0F, 0.95F);
                FxGeo.sphere(vc, m, x1, y1, z1, 4.0F, 5, 8, 1.0F, 0.85F, 0.5F, 0.8F);
            }
            if (t >= th && t < th + 45) {
                float k = smooth(t, th, th + 14);
                float fade = 1.0F - (t - th) / 45.0F;
                float rad = (hit[mi] ? 34.0F : 16.0F) * q * k;
                FxGeo.sphere(vc, m, tx[mi], ty[mi], tz[mi], rad * 0.6F, 6, 12, 1.0F, 0.95F, 0.8F, 0.6F * fade);
                FxGeo.sphere(vc, m, tx[mi], ty[mi], tz[mi], rad, 6, 12, 1.0F, 0.55F, 0.15F, 0.35F * fade);
            }
        }

        // nave: luces y llamas
        float[] a = st(t, q);
        if (a[5] > 0.5F) {
            float burn = smooth(t, FIRST_HIT, FIRST_HIT + 100);
            float engine = t < RUPTURE ? (0.5F + 0.5F * burn) * (1.0F - 0.5F * smooth(t, CRASH, STOP)) : 1.0F;
            ShipModel.push(m, a[0], a[1], 0, a[2], a[3]);
            ship.lights(vc, m, t, a[4], 1.0F, 0.4F, 0.12F, engine, burn);
            Random fr = new Random(seed * 3L + 5L);
            for (int i = 0; i < 16; i++) {
                float fx = -140.0F + fr.nextFloat() * 360.0F;
                float fz = (fr.nextFloat() - 0.5F) * 70.0F;
                float fl = 0.55F + 0.45F * sin(t * 0.3F + i * 2.1F);
                float rad = (7.0F + fr.nextFloat() * 10.0F) * a[4] * fl * burn;
                float fy = ShipModel.topY(fx) * a[4] + 3.0F * a[4];
                FxGeo.sphere(vc, m, fx * a[4], fy, fz * a[4], rad, 6, 10, 1.0F, 0.55F, 0.12F, 0.40F);
                FxGeo.sphere(vc, m, fx * a[4], fy, fz * a[4], rad * 0.5F, 6, 10, 1.0F, 0.9F, 0.5F, 0.55F);
            }
            ShipModel.pop(m, a[0], a[1], 0, a[2], a[3]);

            // estela de fuego y humo detras de la nave
            float trail = smooth(t, FIRST_HIT, FIRST_HIT + 30) * (1.0F - smooth(t, CRASH, CRASH + 30));
            if (trail > 0.01F) {
                float len = 420.0F * trail;
                FxGeo.tube(vc, m, a[0], a[1], 0, a[0] - len * 0.93F, a[1] + len * 0.37F, 0, 70.0F * q, 6.0F * q, 14, 1.0F, 0.45F, 0.1F, 0.30F, 0.0F);
                FxGeo.tube(vc, m, a[0], a[1], 0, a[0] - len * 0.65F, a[1] + len * 0.26F, 0, 36.0F * q, 3.0F * q, 12, 1.0F, 0.75F, 0.3F, 0.45F, 0.0F);
            }
        }

        // agujero negro pequeno
        float hr = holeRadius(t, q);
        float hxl = HX * q;
        float hyl = 14.0F * q;
        if (hr > 0.3F) {
            FxGeo.sphere(vc, m, hxl, hyl, 0, hr * 1.12F, 8, 20, 1.0F, 0.7F, 0.3F, 0.22F);
            FxGeo.sphere(vc, m, hxl, hyl, 0, hr * 1.5F, 8, 16, 1.0F, 0.5F, 0.2F, 0.08F);
            float dk = smooth(t, RUPTURE + 10, RUPTURE + 50) * (1.0F - smooth(t, PULL_END - 10, PULL_END + 20));
            if (dk > 0.01F) {
                float tilt = 0.40F;
                FxGeo.tiltedDisk(vc, m, hxl, hyl, 0, hr * 1.2F, hr * 1.9F, 64, tilt, 1.0F, 0.97F, 0.85F, 0.95F * dk, 0.6F * dk, 3, t * 0.22F);
                FxGeo.tiltedDisk(vc, m, hxl, hyl, 0, hr * 1.9F, hr * 3.0F, 64, tilt, 1.0F, 0.85F, 0.4F, 0.7F * dk, 0.3F * dk, 4, -t * 0.15F);
                FxGeo.tiltedDisk(vc, m, hxl, hyl, 0, hr * 3.0F, hr * 4.4F, 64, tilt, 1.0F, 0.55F, 0.2F, 0.35F * dk, 0.0F, 5, t * 0.1F);
                FxGeo.tiltedDisk(vc, m, hxl, hyl, 0, hr * 1.1F, hr * 1.5F, 64, 1.40F, 1.0F, 0.9F, 0.7F, 0.5F * dk, 0.25F * dk, 2, t * 0.1F);
            }
        }
        float rk = smooth(t, RUPTURE, RUPTURE + 40);
        float ra = 1.0F - smooth(t, RUPTURE + 40, RUPTURE + 140);
        if (rk > 0.0F && ra > 0.01F) {
            FxGeo.sphere(vc, m, hxl, hyl, 0, 40.0F * q * rk, 8, 20, 1.0F, 0.95F, 0.8F, 0.5F * ra);
            FxGeo.sphere(vc, m, hxl, hyl, 0, 70.0F * q * rk, 8, 24, 1.0F, 0.6F, 0.2F, 0.28F * ra);
        }

        if (ground == null) return;

        // surco brillante por donde se desliza la nave
        if (t > CRASH) {
            float xe = lerp(TX * q, SX * q, ease(smooth(t, CRASH, STOP)));
            float cool = 1.0F - smooth(t, STOP + 40, RUPTURE + 130);
            if (cool > 0.01F && xe > TX * q) {
                int segs = (int) Math.max(1, (xe - TX * q) / 10.0F);
                for (int i = 0; i < segs; i++) {
                    float xa = TX * q + i * 10.0F;
                    float xb = Math.min(xe, xa + 10.0F);
                    float hot = Math.max(0.25F, 1.0F - (xe - xa) / (160.0F * q));
                    FxGeo.terrainLine(vc, m, ground, xa, 0, xb, 0, 40.0F * q, 0.5F, 1.0F, 0.4F, 0.08F, 0.22F * cool * hot, 0.22F * cool * hot);
                    FxGeo.terrainLine(vc, m, ground, xa, 0, xb, 0, 12.0F * q, 0.6F, 1.0F, 0.75F, 0.3F, 0.6F * cool * hot, 0.6F * cool * hot);
                }
            }
        }

        // ondas de choque del impacto y del reventon
        if (t > CRASH && t < CRASH + 150) shock(vc, m, TX * q, t - CRASH, q, 260.0F * q, 6.0F);
        if (t > RUPTURE && t < RUPTURE + 110) shock(vc, m, hxl, t - RUPTURE, q, 220.0F * q, 6.0F);

        float fk = smooth(t, CRASH, CRASH + 50);
        float fa = 1.0F - smooth(t, CRASH + 40, CRASH + 150);
        if (fk > 0.0F && fa > 0.01F) {
            FxGeo.sphere(vc, m, TX * q, 0, 0, 50.0F * q * fk, 8, 20, 1.0F, 0.95F, 0.8F, 0.5F * fa);
            FxGeo.sphere(vc, m, TX * q, 0, 0, 85.0F * q * fk, 8, 24, 1.0F, 0.6F, 0.2F, 0.28F * fa);
            FxGeo.sphere(vc, m, TX * q, 0, 0, 120.0F * q * fk, 8, 28, 1.0F, 0.35F, 0.08F, 0.14F * fa);
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.5F * smooth(t, 60, PLUNGE) + 0.8F * smooth(t, PLUNGE, CRASH);
        for (int m = 0; m < MISSILES; m++) {
            float dt = t - (MISSILE_START + m * 6 + FLIGHT);
            if (dt >= 0.0F && dt < 40.0F) a += 1.0F * (float) Math.exp(-dt / 12.0F);
        }
        if (t >= CRASH && t < RUPTURE) a += 5.0F * (float) Math.exp(-(t - CRASH) / 60.0F) + 1.2F * (1.0F - smooth(t, STOP, RUPTURE));
        if (t >= RUPTURE) a += 4.5F * (float) Math.exp(-(t - RUPTURE) / 50.0F) + 1.0F * (1.0F - smooth(t, PULL_END - 30, PULL_END + 20));
        return Math.min(a, 7.0F) * (1.0F - smooth(t, HOLE_END, D));
    }

    @Override
    public float flash(float t) {
        float f = 0.0F;
        for (int m = 0; m < MISSILES; m++) {
            if (!hit[m]) continue;
            float dt = t - (MISSILE_START + m * 6 + FLIGHT);
            if (dt >= 0.0F && dt < 12.0F) f = Math.max(f, 0.4F * (1.0F - dt / 12.0F));
        }
        if (t >= CRASH && t < CRASH + 22) {
            float k = 1.0F - (t - CRASH) / 22.0F;
            f = Math.max(f, 0.9F * k * k);
        }
        if (t >= RUPTURE && t < RUPTURE + 30) {
            float k = 1.0F - (t - RUPTURE) / 30.0F;
            f = Math.max(f, k * k);
        }
        return f;
    }

    @Override
    public float flare(float t) {
        float a = smooth(t, CRASH, CRASH + 6) * (1.0F - smooth(t, CRASH + 40, CRASH + 90));
        float b = smooth(t, RUPTURE, RUPTURE + 8) * (1.0F - smooth(t, PULL_END - 30, PULL_END + 10));
        return Math.max(a, b);
    }

    @Override
    public int flareColor() {
        return 0xFFAA66;
    }

    @Override
    public float flareHeight() {
        return 20.0F;
    }

    @Override
    public int tint(float t) {
        int al = (int) (85.0F * smooth(t, 0, 90) * (1.0F - smooth(t, HOLE_END, D)));
        return (al << 24) | 0x3A1406;
    }
}
