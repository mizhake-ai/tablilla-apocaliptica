package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.BlackHoleStrike;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/** El Abismo: esfera negra con disco de acrecion que se traga el terreno y colapsa en una explosion de luz. */
public class BlackHoleFx implements FxEffect {
    private static final int FORM = 100;
    private static final int FULL = 180;
    private static final int COLLAPSE = BlackHoleStrike.EAT_END;
    private static final int D = BlackHoleStrike.DURATION;
    private static final float TOP = 420.0F;
    private static final float CY = 5.0F;

    @Override
    public int duration() {
        return D;
    }

    private static float radius(float t, float s) {
        float grow = smooth(t, FORM, FULL);
        float gone = 1.0F - smooth(t, COLLAPSE, COLLAPSE + 16);
        return 18.0F * s * grow * gone * (1.0F + 0.03F * sin(t * 0.7F));
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) FxUtil.snd(level, "blackhole.lockon", x, y, z, 16.0F, 1.0F);
        if (t == 6) FxUtil.snd(level, "blackhole.charge", x, y, z, 16.0F, 1.0F);
        if (t == FORM) FxUtil.snd(level, "blackhole.fire", x, y, z, 16.0F, 1.0F);
        if (t == FORM + 10) FxUtil.snd(level, "blackhole.beam", x, y, z, 16.0F, 1.0F);
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
    public void renderSolid(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float rad = radius(t, s);
        if (rad > 0.2F) FxGeo.sphere(vc, m, 0, CY, 0, rad, 14, 28, 0.0F, 0.0F, 0.0F, 1.0F);
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float feed = smooth(t, 0, 30) * (1.0F - smooth(t, FULL - 10, FULL + 20));
        if (feed > 0.01F) FxGeo.glowBeam(vc, m, 0, CY, 0, 0, TOP, 0, 1.4F * s * feed, 0.6F, 0.4F, 1.0F, 1.0F, 10);

        float rad = radius(t, s);
        if (rad > 0.3F) {
            FxGeo.sphere(vc, m, 0, CY, 0, rad * 1.12F, 10, 24, 1.0F, 0.7F, 0.3F, 0.22F);
            FxGeo.sphere(vc, m, 0, CY, 0, rad * 1.4F, 8, 20, 1.0F, 0.5F, 0.2F, 0.08F);
            float dk = smooth(t, FORM + 10, FULL + 10) * (1.0F - smooth(t, COLLAPSE - 4, COLLAPSE + 12));
            if (dk > 0.01F) {
                FxGeo.diskRing(vc, m, 0, CY, 0, rad * 1.25F, rad * 1.9F, 72, 1.0F, 0.95F, 0.8F, 0.95F * dk, 0.6F * dk, 3, t * 0.18F);
                FxGeo.diskRing(vc, m, 0, CY, 0, rad * 1.9F, rad * 2.8F, 72, 1.0F, 0.6F, 0.2F, 0.6F * dk, 0.25F * dk, 4, -t * 0.12F);
                FxGeo.diskRing(vc, m, 0, CY, 0, rad * 2.8F, rad * 3.8F, 72, 0.55F, 0.3F, 1.0F, 0.3F * dk, 0.0F, 5, t * 0.08F);
            }
        }

        float cs = t - COLLAPSE;
        if (cs > 0.0F && cs < 70.0F) {
            float rr = cs * 5.0F * s;
            float fade = 1.0F - cs / 70.0F;
            float w = 2.0F + rr * 0.04F;
            FxGeo.ring(vc, m, 0, 0.8F, 0, rr - w, rr, 48, 1.0F, 0.85F, 0.6F, 0.0F, 0.8F * fade);
            FxGeo.ring(vc, m, 0, 0.8F, 0, rr, rr + w, 48, 1.0F, 0.85F, 0.6F, 0.8F * fade, 0.0F);
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
    public int tint(float t) {
        int al = (int) (85.0F * smooth(t, 90, 200) * (1.0F - smooth(t, COLLAPSE + 6, COLLAPSE + 50)));
        return (al << 24) | 0x0A0418;
    }
}
