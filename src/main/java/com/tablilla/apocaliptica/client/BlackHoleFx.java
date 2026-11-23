package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.BlackHoleStrike;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;

/**
 * El Abismo: un agujero negro enorme con disco de acrecion girando, sigilo en espiral rosado, cielo nocturno con
 * estrellas y aurora, relampagos en el horizonte y trozos de terreno que suben en espiral hacia el.
 */
public class BlackHoleFx implements FxEffect {
    private static final int FORM = 100;
    private static final int FULL = 180;
    private static final int COLLAPSE = BlackHoleStrike.EAT_END;
    private static final int D = BlackHoleStrike.DURATION;
    private static final float STAR_Y = 260.0F;
    private static final float CY = 12.0F;
    private static final int STARS = 240;

    private GroundMap ground;
    private List<Debris.Proto> protos;
    private float[] sx;
    private float[] sy;
    private float[] sz;

    @Override
    public int duration() {
        return D;
    }

    private static float radius(float t, float s) {
        float grow = smooth(t, FORM, FULL);
        float gone = 1.0F - smooth(t, COLLAPSE, COLLAPSE + 16);
        return 22.0F * s * grow * gone * (1.0F + 0.03F * sin(t * 0.7F));
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) {
            float half = Math.min(200.0F, 70.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(3.5F, half / 40.0F));
            protos = Debris.sample(level, x, z, 2.0, 50.0 * s, 300, 0.3F, r);
            FxUtil.snd(level, "blackhole.lockon", x, y, z, 16.0F, 1.0F);
        }
        if (t == 6) FxUtil.snd(level, "blackhole.charge", x, y, z, 16.0F, 1.0F);
        if (t == FORM) FxUtil.snd(level, "blackhole.fire", x, y, z, 16.0F, 1.0F);
        if (t == FORM + 10) FxUtil.snd(level, "blackhole.beam", x, y, z, 16.0F, 1.0F);
        if (t == FORM + 20 && protos != null) {
            // el terreno se arranca en cubos grandes que suben girando hacia el agujero
            Debris.spiral(protos, x, y + CY, z, 230, 190, 1.5F, 3.2F, r);
        }
        if (t == FORM + 50) FxUtil.snd(level, "blackhole.rumble", x, y, z, 16.0F, 1.0F);
        if (t == COLLAPSE) FxUtil.snd(level, "blackhole.accent", x, y, z, 16.0F, 1.0F);
        if (t == COLLAPSE + 8) FxUtil.snd(level, "blackhole.shutdown", x, y, z, 16.0F, 1.0F);

        if (t >= FORM && t < COLLAPSE) {
            int n = (int) (14 * smooth(t, FORM, FORM + 60));
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = (14.0 + r.nextDouble() * 40.0) * s;
                double c = Math.cos(a);
                double sn = Math.sin(a);
                double px = x + c * d;
                double pz = z + sn * d;
                double py = y + CY + r.nextGaussian() * 5.0;
                double vx = (-c * 0.6 - sn * 0.9) * 0.5;
                double vz = (-sn * 0.6 + c * 0.9) * 0.5;
                FxUtil.p(level, ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, vx, 0.0, vz);
                if (i % 2 == 0) FxUtil.p(level, ParticleTypes.END_ROD, px, py, pz, vx, 0.0, vz);
                if (i % 3 == 0) FxUtil.p(level, ParticleTypes.SMOKE, px, py, pz, vx, 0.05, vz);
            }
        }

        if (t == COLLAPSE) {
            for (int i = 0; i < 160; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = (r.nextDouble() - 0.5) * Math.PI;
                double sp = 1.5 + r.nextDouble() * 2.5;
                FxUtil.p(level, ParticleTypes.END_ROD, x, y + CY, z,
                        Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp);
            }
            for (int i = 0; i < 6; i++) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * 12 * s, y + CY + r.nextGaussian() * 6, z + r.nextGaussian() * 12 * s, 0, 0, 0);
            }
        }
        if (t > COLLAPSE && t < COLLAPSE + 50 && t % 2 == 0) {
            FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + r.nextGaussian() * 12 * s, y + 2, z + r.nextGaussian() * 12 * s, 0, 0.3, 0);
        }
    }

    @Override
    public boolean usesHull() {
        return true;
    }

    /** La esfera negra es solida (bloque negro con el RenderType de entidades). */
    @Override
    public void renderHull(VertexConsumer vc, PoseStack.Pose pose, float t, long seed, float s) {
        float rad = radius(t, s);
        if (rad > 0.2F) {
            HullGeo.sphere(vc, pose, HullGeo.sprite(Blocks.BLACK_CONCRETE), 0, CY, 0, rad, 14, 28, 0.04F, 0.04F, 0.06F);
        }
    }

    private void makeStars(long seed) {
        sx = new float[STARS];
        sy = new float[STARS];
        sz = new float[STARS];
        Random r = new Random(seed ^ 0x5DEECE66DL);
        for (int i = 0; i < STARS; i++) {
            float a = r.nextFloat() * TAU;
            float h = 0.04F + r.nextFloat() * 0.96F;
            float rr = (float) Math.sqrt(1.0F - h * h);
            sx[i] = cos(a) * rr;
            sy[i] = h;
            sz[i] = sin(a) * rr;
        }
    }

    private void spiralSigil(VertexConsumer vc, Matrix4f m, float t, float s, float sg) {
        float rv = smooth(t, 0, 50);
        float rr = 44.0F * s;
        int n = 40;
        float wmul = Math.max(1.0F, s);
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < n * rv; i++) {
                float r0 = 3.0F + (rr - 3.0F) * i / n;
                float r1 = 3.0F + (rr - 3.0F) * (i + 1) / n;
                float a0 = k * TAU / 3.0F + t * 0.02F + (r0 / rr) * 2.6F;
                float a1 = k * TAU / 3.0F + t * 0.02F + (r1 / rr) * 2.6F;
                float w = (2.4F - 1.5F * i / n) * wmul;
                float fadeIn = 0.95F * (1.0F - 0.55F * i / n) * sg;
                float fadeOut = 0.95F * (1.0F - 0.55F * (i + 1) / n) * sg;
                FxGeo.terrainLine(vc, m, ground, cos(a0) * r0, sin(a0) * r0, cos(a1) * r1, sin(a1) * r1,
                        w * 3.0F, 0.35F, 1.0F, 0.35F, 0.75F, fadeIn * 0.3F, fadeOut * 0.3F);
                FxGeo.terrainLine(vc, m, ground, cos(a0) * r0, sin(a0) * r0, cos(a1) * r1, sin(a1) * r1,
                        w, 0.42F, 1.0F, 0.6F, 0.9F, fadeIn, fadeOut);
            }
        }
        FxGeo.polyCircle(vc, m, ground, 0, 0, 3.0F, 24, 1.2F * wmul, 0.4F, 1.0F, 0.7F, 0.95F, 0.9F * sg, rv);
    }

    private void sky(VertexConsumer vc, Matrix4f m, float t, long seed, float night) {
        if (sx == null) makeStars(seed);
        float cx = (float) FxCtx.camX;
        float cy = (float) FxCtx.camY;
        float cz = (float) FxCtx.camZ;
        float R = 140.0F;
        for (int i = 0; i < STARS; i++) {
            float tw = 0.55F + 0.45F * sin(t * 0.13F + i * 1.7F);
            float big = (i % 11 == 0) ? 1.7F : 1.0F;
            float col = (i % 5 == 0) ? 0.75F : 1.0F;
            FxGeo.star(vc, m, cx + sx[i] * R, cy + sy[i] * R, cz + sz[i] * R, sx[i], sy[i], sz[i],
                    0.55F * big, col, col, 1.0F, 0.85F * night * tw);
        }
        // aurora: cortinas de luz morada y verde en el cielo
        for (int c = 0; c < 3; c++) {
            float az0 = c * 2.1F + (seed % 100) * 0.03F;
            boolean green = c == 1;
            float cr = green ? 0.3F : 0.65F;
            float cg = green ? 1.0F : 0.3F;
            float cb = green ? 0.6F : 1.0F;
            int segs = 22;
            for (int i = 0; i < segs; i++) {
                float a0 = az0 + i * 1.1F / segs;
                float a1 = az0 + (i + 1) * 1.1F / segs;
                float w0 = 25.0F + 14.0F * sin(a0 * 5.0F + t * 0.03F + c);
                float w1 = 25.0F + 14.0F * sin(a1 * 5.0F + t * 0.03F + c);
                float rr = 130.0F;
                float edge = 0.5F + 0.5F * sin(i * 3.14159F / segs);
                float al = 0.28F * night * edge;
                FxGeo.quad(vc, m,
                        cx + cos(a0) * rr, cy + 22.0F, cz + sin(a0) * rr, al,
                        cx + cos(a1) * rr, cy + 22.0F, cz + sin(a1) * rr, al,
                        cx + cos(a1) * rr, cy + 22.0F + w1 * 2.0F, cz + sin(a1) * rr, 0.0F,
                        cx + cos(a0) * rr, cy + 22.0F + w0 * 2.0F, cz + sin(a0) * rr, 0.0F,
                        cr, cg, cb);
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float night = smooth(t, 50, 160) * (1.0F - smooth(t, COLLAPSE, COLLAPSE + 25));
        if (night > 0.01F) sky(vc, m, t, seed, night);

        float sg = smooth(t, 0, 25) * (1.0F - smooth(t, FORM + 60, FORM + 110));
        if (sg > 0.01F && ground != null) spiralSigil(vc, m, t, s, sg);

        // rayo que alimenta al agujero, con una estrella brillante en lo alto
        float feed = smooth(t, 0, 30) * (1.0F - smooth(t, FULL - 10, FULL + 20));
        if (feed > 0.01F) {
            FxGeo.glowBeam(vc, m, 0, CY, 0, 0, STAR_Y, 0, 1.4F * s * feed, 0.65F, 0.4F, 1.0F, 1.0F, 10);
            FxGeo.sphere(vc, m, 0, STAR_Y, 0, 5.0F * feed, 6, 12, 1.0F, 1.0F, 1.0F, 0.95F);
            FxGeo.sphere(vc, m, 0, STAR_Y, 0, 11.0F * feed, 6, 12, 0.7F, 0.5F, 1.0F, 0.35F);
        }

        float rad = radius(t, s);
        if (rad > 0.3F) {
            FxGeo.sphere(vc, m, 0, CY, 0, rad * 1.12F, 10, 24, 1.0F, 0.7F, 0.3F, 0.22F);
            FxGeo.sphere(vc, m, 0, CY, 0, rad * 1.4F, 8, 20, 1.0F, 0.5F, 0.2F, 0.08F);
            float dk = smooth(t, FORM + 10, FULL + 10) * (1.0F - smooth(t, COLLAPSE - 4, COLLAPSE + 12));
            if (dk > 0.01F) {
                float tilt = 0.38F;
                FxGeo.tiltedDisk(vc, m, 0, CY, 0, rad * 1.2F, rad * 1.8F, 80, tilt, 1.0F, 0.97F, 0.85F, 0.95F * dk, 0.65F * dk, 3, t * 0.20F);
                FxGeo.tiltedDisk(vc, m, 0, CY, 0, rad * 1.8F, rad * 2.7F, 80, tilt, 1.0F, 0.85F, 0.4F, 0.7F * dk, 0.35F * dk, 4, -t * 0.14F);
                FxGeo.tiltedDisk(vc, m, 0, CY, 0, rad * 2.7F, rad * 3.9F, 80, tilt, 1.0F, 0.55F, 0.2F, 0.4F * dk, 0.12F * dk, 5, t * 0.10F);
                FxGeo.tiltedDisk(vc, m, 0, CY, 0, rad * 3.9F, rad * 5.2F, 80, tilt, 0.6F, 0.35F, 1.0F, 0.22F * dk, 0.0F, 6, -t * 0.07F);
                // arco de luz doblada por encima del agujero
                FxGeo.tiltedDisk(vc, m, 0, CY, 0, rad * 1.1F, rad * 1.5F, 80, 1.40F, 1.0F, 0.9F, 0.7F, 0.5F * dk, 0.25F * dk, 2, t * 0.1F);
            }
        }

        // relampagos blancos y lilas a lo largo del horizonte
        float lt = smooth(t, FORM + 10, FORM + 50) * (1.0F - smooth(t, COLLAPSE - 10, COLLAPSE + 5));
        if (lt > 0.01F) {
            for (int j = 0; j < 5; j++) {
                Random rr = new Random(seed * 31L + (long) (t / 3.0F) * 97L + j * 7919L);
                int n = 18;
                float[] xs = new float[n];
                float[] ys = new float[n];
                float[] zs = new float[n];
                float ang = rr.nextFloat() * TAU;
                xs[0] = 0;
                ys[0] = CY + 4.0F;
                zs[0] = 0;
                for (int k = 1; k < n; k++) {
                    ang += (rr.nextFloat() - 0.5F) * 0.7F;
                    xs[k] = xs[k - 1] + cos(ang) * 11.0F;
                    zs[k] = zs[k - 1] + sin(ang) * 11.0F;
                    ys[k] = Math.max(1.0F, ys[k - 1] - rr.nextFloat() * 0.9F + (rr.nextFloat() - 0.4F) * 2.5F);
                }
                FxGeo.bolt(vc, m, xs, ys, zs, n, 0.6F, 1.0F, 0.95F, 1.0F, 0.95F * lt);
                FxGeo.bolt(vc, m, xs, ys, zs, n, 2.0F, 0.7F, 0.45F, 1.0F, 0.25F * lt);
            }
        }

        float cs = t - COLLAPSE;
        if (cs > 0.0F && cs < 70.0F) {
            float rr = cs * 5.0F * s;
            float fade = 1.0F - cs / 70.0F;
            float w = 2.0F + rr * 0.04F;
            if (ground != null) {
                int sides = Math.max(40, Math.min(140, (int) (rr * 0.6F)));
                FxGeo.terrainRing(vc, m, ground, 0, 0, Math.max(0.0F, rr - w), rr, sides, 0.8F, 1.0F, 0.85F, 0.6F, 0.0F, 0.8F * fade);
                FxGeo.terrainRing(vc, m, ground, 0, 0, rr, rr + w, sides, 0.8F, 1.0F, 0.85F, 0.6F, 0.8F * fade, 0.0F);
            } else {
                FxGeo.ring(vc, m, 0, 0.8F, 0, Math.max(0.0F, rr - w), rr, 48, 1.0F, 0.85F, 0.6F, 0.0F, 0.8F * fade);
            }
            FxGeo.sphere(vc, m, 0, CY, 0, rr * 0.8F, 10, 24, 0.8F, 0.6F, 1.0F, 0.12F * fade);
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.7F * smooth(t, FORM, FULL) * (1.0F - smooth(t, COLLAPSE - 4, COLLAPSE));
        if (t >= COLLAPSE) a += 3.5F * (float) Math.exp(-(t - COLLAPSE) / 30.0F);
        return a * (1.0F - smooth(t, COLLAPSE + 30, D));
    }

    @Override
    public float flash(float t) {
        if (t >= COLLAPSE && t < COLLAPSE + 16) {
            float k = 1.0F - (t - COLLAPSE) / 16.0F;
            return 0.9F * k * k;
        }
        if (t >= FORM && t < FORM + 10) return 0.5F * (1.0F - (t - FORM) / 10.0F);
        return 0.0F;
    }

    @Override
    public float flare(float t) {
        return smooth(t, FORM - 25, FORM + 10) * (1.0F - smooth(t, COLLAPSE - 12, COLLAPSE + 4));
    }

    @Override
    public int flareColor() {
        return 0xB070FF;
    }

    @Override
    public float flareHeight() {
        return 120.0F;
    }

    @Override
    public int tint(float t) {
        int al = (int) (125.0F * smooth(t, 60, 190) * (1.0F - smooth(t, COLLAPSE + 6, COLLAPSE + 50)));
        return (al << 24) | 0x0A0418;
    }
}
