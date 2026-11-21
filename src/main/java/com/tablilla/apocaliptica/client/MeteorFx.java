package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.MeteorPlan;
import com.tablilla.apocaliptica.MeteorStrike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

/** Lluvia de Estrellas: meteoros con estela de fuego que caen por toda la zona. */
public class MeteorFx implements FxEffect {
    private static final int D = MeteorStrike.DURATION;
    private static final double HORIZ = MeteorPlan.HORIZ;
    private static final double HEIGHT = MeteorPlan.HEIGHT;

    private final MeteorPlan plan;
    private final float[] gy = new float[MeteorPlan.COUNT];

    public MeteorFx(long seed, float scale) {
        this.plan = new MeteorPlan(seed, scale);
    }

    @Override
    public int duration() {
        return D;
    }

    private void groundAt(ClientLevel level, double x, double y, double z, int i) {
        int wx = (int) Math.floor(x + plan.ox[i]);
        int wz = (int) Math.floor(z + plan.oz[i]);
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
        gy[i] = (h < y - 80) ? 0.0F : (float) (h - y);
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) {
            for (int i = 0; i < MeteorPlan.COUNT; i++) groundAt(level, x, y, z, i);
            FxUtil.snd(level, "meteor.lockon", x, y, z, 16.0F, 1.0F);
        }
        if (t == 6) FxUtil.snd(level, "meteor.charge", x, y, z, 16.0F, 1.0F);
        if (t == 30) {
            FxUtil.snd(level, "meteor.fire", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "meteor.approach", x, y, z, 16.0F, 1.0F);
        }
        if (t == 36) FxUtil.snd(level, "meteor.storm", x, y, z, 16.0F, 1.0F);
        if (t == 150) FxUtil.snd(level, "meteor.rumble", x, y, z, 16.0F, 1.0F);
        if (t == D - 60) FxUtil.snd(level, "meteor.shutdown", x, y, z, 16.0F, 1.0F);

        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < MeteorPlan.COUNT; i++) {
            int ts = plan.start[i];
            int ti = plan.impactTick(i);
            if (t == ts) groundAt(level, x, y, z, i);
            if (t < ts || t > ti) continue;

            double tau = (t - ts) / (double) plan.flight[i];
            double k = 1.0 - tau;
            double mx = x + plan.ox[i] + plan.dirX[i] * HORIZ * k;
            double my = y + gy[i] + HEIGHT * k;
            double mz = z + plan.oz[i] + plan.dirZ[i] * HORIZ * k;

            if (t == ts + plan.flight[i] / 2) {
                FxUtil.snd(level, (i % 2 == 0) ? "meteor.whoosh1" : "meteor.whoosh2", mx, my, mz, 8.0F, 0.9F + r.nextFloat() * 0.3F);
            }

            double tl = Math.sqrt(HORIZ * HORIZ + HEIGHT * HEIGHT);
            double bx = plan.dirX[i] * HORIZ / tl;
            double by = HEIGHT / tl;
            double bz = plan.dirZ[i] * HORIZ / tl;
            double rd = plan.rad[i];
            for (int q = 0; q < 5; q++) {
                double off = q * rd * 1.6;
                FxUtil.p(level, ParticleTypes.FLAME, mx + bx * off, my + by * off, mz + bz * off,
                        r.nextGaussian() * 0.05, r.nextGaussian() * 0.05, r.nextGaussian() * 0.05);
            }
            FxUtil.p(level, ParticleTypes.LARGE_SMOKE, mx + bx * rd * 3, my + by * rd * 3, mz + bz * rd * 3, 0, 0, 0);
            FxUtil.p(level, ParticleTypes.LAVA, mx, my, mz, 0, 0, 0);

            if (t == ti) {
                double ix = x + plan.ox[i];
                double iy = y + gy[i];
                double iz = z + plan.oz[i];
                double d2 = mc.player == null ? 0.0 : mc.player.distanceToSqr(ix, iy, iz);
                String nm = (d2 > 90.0 * 90.0 ? "meteor.hit_far" : "meteor.hit") + (1 + i % 3);
                FxUtil.snd(level, nm, ix, iy, iz, 12.0F, 0.9F + r.nextFloat() * 0.2F);
                boolean big = i % 12 == 11;
                if (big) FxUtil.snd(level, "meteor.impact", ix, iy, iz, 16.0F, 1.0F);
                int rc = Math.max(3, plan.crater[i]);
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, ix, iy + 1, iz, 0, 0, 0);
                if (big) {
                    for (int e = 0; e < 4; e++) {
                        FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, ix + r.nextGaussian() * rc * 0.4, iy + 1, iz + r.nextGaussian() * rc * 0.4, 0, 0, 0);
                    }
                }
                for (int e = 0; e < 50; e++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double sp = 0.3 + r.nextDouble() * 0.9;
                    FxUtil.p(level, ParticleTypes.FLAME, ix, iy + 1, iz, Math.cos(a) * sp, 0.3 + r.nextDouble() * 0.8, Math.sin(a) * sp);
                }
                for (int e = 0; e < 30; e++) {
                    FxUtil.p(level, ParticleTypes.LARGE_SMOKE, ix + r.nextGaussian() * rc * 0.5, iy + 1, iz + r.nextGaussian() * rc * 0.5, 0, 0.25, 0);
                }
                for (int e = 0; e < 6; e++) {
                    FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, ix + r.nextGaussian() * rc * 0.4, iy + 1, iz + r.nextGaussian() * rc * 0.4, 0, 0.1, 0);
                }
                for (int e = 0; e < 40; e++) {
                    double a = Math.PI * 2 * e / 40;
                    FxUtil.p(level, ParticleTypes.CLOUD, ix + Math.cos(a) * rc * 0.5, iy + 0.5, iz + Math.sin(a) * rc * 0.5, Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5);
                }
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        double tl = Math.sqrt(HORIZ * HORIZ + HEIGHT * HEIGHT);
        for (int i = 0; i < MeteorPlan.COUNT; i++) {
            int ts = plan.start[i];
            int ti = plan.impactTick(i);
            if (t < ts || t > ti + 30) continue;
            float ox = (float) plan.ox[i];
            float oz = (float) plan.oz[i];
            float rd = (float) plan.rad[i];

            if (t <= ti) {
                float tau = (t - ts) / (float) plan.flight[i];
                float k = 1.0F - tau;
                float mx = ox + (float) (plan.dirX[i] * HORIZ) * k;
                float my = gy[i] + (float) HEIGHT * k;
                float mz = oz + (float) (plan.dirZ[i] * HORIZ) * k;
                float tx = (float) (plan.dirX[i] * HORIZ / tl);
                float ty = (float) (HEIGHT / tl);
                float tz = (float) (plan.dirZ[i] * HORIZ / tl);
                float len = rd * 14.0F;

                FxGeo.tube(vc, m, mx, my, mz, mx + tx * len * 0.7F, my + ty * len * 0.7F, mz + tz * len * 0.7F,
                        rd * 0.35F, rd * 0.05F, 10, 1.0F, 1.0F, 0.9F, 0.9F, 0.0F);
                FxGeo.tube(vc, m, mx, my, mz, mx + tx * len * 0.9F, my + ty * len * 0.9F, mz + tz * len * 0.9F,
                        rd * 0.7F, rd * 0.1F, 10, 1.0F, 0.7F, 0.3F, 0.5F, 0.0F);
                FxGeo.tube(vc, m, mx, my, mz, mx + tx * len, my + ty * len, mz + tz * len,
                        rd * 1.1F, rd * 0.2F, 10, 1.0F, 0.4F, 0.1F, 0.22F, 0.0F);
                FxGeo.sphere(vc, m, mx, my, mz, rd * 0.6F, 6, 10, 1.0F, 1.0F, 0.95F, 0.95F);
                FxGeo.sphere(vc, m, mx, my, mz, rd * 1.0F, 6, 10, 1.0F, 0.7F, 0.3F, 0.6F);
                FxGeo.sphere(vc, m, mx, my, mz, rd * 1.7F, 6, 10, 1.0F, 0.4F, 0.1F, 0.22F);
            } else {
                float since = t - ti;
                float fade = 1.0F - since / 30.0F;
                float rc = Math.max(3, plan.crater[i]);
                float gr = smooth(since, 0, 8);
                FxGeo.sphere(vc, m, ox, gy[i], oz, rc * 0.6F * gr, 6, 12, 1.0F, 0.9F, 0.6F, 0.55F * fade);
                FxGeo.sphere(vc, m, ox, gy[i], oz, rc * 1.1F * gr, 6, 12, 1.0F, 0.5F, 0.15F, 0.3F * fade);
                float rr = since * rc * 0.35F;
                FxGeo.ring(vc, m, ox, gy[i] + 0.6F, oz, Math.max(0.0F, rr - 1.5F), rr, 24, 1.0F, 0.7F, 0.3F, 0.0F, 0.6F * fade);
                FxGeo.ring(vc, m, ox, gy[i] + 0.6F, oz, rr, rr + 1.5F, 24, 1.0F, 0.7F, 0.3F, 0.6F * fade, 0.0F);
            }
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.0F;
        for (int i = 0; i < MeteorPlan.COUNT; i++) {
            float dt = t - plan.impactTick(i);
            if (dt >= 0.0F && dt < 60.0F) a += (i % 12 == 11 ? 2.0F : 0.8F) * (float) Math.exp(-dt / 10.0F);
        }
        return Math.min(a, 3.2F) * (1.0F - smooth(t, 380, D));
    }

    @Override
    public int tint(float t) {
        int al = (int) (40.0F * smooth(t, 10, 60) * (1.0F - smooth(t, 360, 415)));
        return (al << 24) | 0x701800;
    }
}
