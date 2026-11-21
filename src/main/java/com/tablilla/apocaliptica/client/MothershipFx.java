package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.MothershipStrike;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/** La Barca Celeste: una nave colosal llega, carga su canon, arrasa la zona con un rayo y se marcha. */
public class MothershipFx implements FxEffect {
    private static final int ARRIVE = MothershipStrike.ARRIVE;
    private static final int CHARGE = MothershipStrike.CHARGE;
    private static final int FIRE = MothershipStrike.FIRE;
    private static final int FIRE_END = MothershipStrike.FIRE_END;
    private static final int DEPART = MothershipStrike.DEPART;
    private static final int D = MothershipStrike.DURATION;
    private static final float HOVER = 170.0F;

    // y0, y1, radio abajo, radio arriba, r, g, b
    private static final float[][] HULL = {
        {-40, -30, 24, 44, 0.16F, 0.17F, 0.22F},
        {-30, -16, 44, 82, 0.20F, 0.22F, 0.28F},
        {-16, -5, 82, 112, 0.26F, 0.28F, 0.35F},
        {-5, 3, 112, 112, 0.46F, 0.48F, 0.55F},
        {3, 13, 112, 80, 0.36F, 0.39F, 0.46F},
        {13, 22, 80, 46, 0.34F, 0.37F, 0.44F},
        {22, 36, 46, 18, 0.30F, 0.34F, 0.42F},
        {36, 40, 18, 6, 0.42F, 0.46F, 0.54F}
    };

    @Override
    public int duration() {
        return D;
    }

    private static float ease(float k) {
        return 1.0F - (1.0F - k) * (1.0F - k);
    }

    private static float sx(float t) {
        float k = ease(smooth(t, 0, ARRIVE));
        float dep = Math.max(0.0F, t - DEPART);
        return lerp(-420.0F, 0.0F, k) + dep * dep * 0.04F;
    }

    private static float sy(float t) {
        float k = ease(smooth(t, 0, ARRIVE));
        float dep = Math.max(0.0F, t - DEPART);
        return lerp(560.0F, HOVER, k) + dep * dep * 0.05F;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) FxUtil.snd(level, "mothership.arrival", x, y, z, 16.0F, 1.0F);
        if (t == 100 || t == 400) FxUtil.snd(level, "mothership.hum", x, y, z, 16.0F, 1.0F);
        if (t == 130) FxUtil.snd(level, "mothership.sweep1", x, y, z, 16.0F, 1.0F);
        if (t == 190) FxUtil.snd(level, "mothership.sweep2", x, y, z, 16.0F, 1.0F);
        if (t == CHARGE) FxUtil.snd(level, "mothership.charge", x, y, z, 16.0F, 1.0F);
        if (t == CHARGE + 10) FxUtil.snd(level, "mothership.rumble", x, y, z, 16.0F, 1.0F);
        if (t == FIRE) {
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "mothership.fire", x, y, z, 16.0F, 1.0F);
        }
        if (t == FIRE + 4) FxUtil.snd(level, "mothership.beam1", x, y, z, 16.0F, 1.0F);
        if (t == FIRE + 70) FxUtil.snd(level, "mothership.beam2", x, y, z, 16.0F, 1.0F);
        if (t == FIRE_END) FxUtil.snd(level, "mothership.cutoff", x, y, z, 16.0F, 1.0F);
        if (t == DEPART) FxUtil.snd(level, "mothership.depart", x, y, z, 16.0F, 1.0F);

        double q = s;
        double px = x + sx(t);
        double py = y + sy(t) - 46.0 * q;

        if (t >= CHARGE && t < FIRE) {
            for (int i = 0; i < 6; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = (r.nextDouble() - 0.5) * Math.PI;
                double dx = Math.cos(a) * Math.cos(b);
                double dy = Math.sin(b);
                double dz = Math.sin(a) * Math.cos(b);
                FxUtil.p(level, ParticleTypes.END_ROD, px + dx * 30 * q, py + dy * 30 * q, pz(z) + dz * 30 * q, -dx * 0.8, -dy * 0.8, -dz * 0.8);
            }
        }

        if (t >= FIRE && t < FIRE_END) {
            double big = 11.0 * q;
            double port = HOVER - 44.0 * q;
            for (int i = 0; i < 14; i++) {
                FxUtil.p(level, ParticleTypes.END_ROD, x + r.nextGaussian() * big * 0.5, y + r.nextDouble() * port, z + r.nextGaussian() * big * 0.5, 0, -0.3, 0);
            }
            for (int i = 0; i < 12; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = r.nextDouble() * big * 2.5;
                FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * d, y + 0.2, z + Math.sin(a) * d, 0, 0.3 + r.nextDouble() * 0.4, 0);
            }
            for (int i = 0; i < 10; i++) {
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + r.nextGaussian() * 30 * q, y + 0.5, z + r.nextGaussian() * 30 * q, 0, 0.35, 0);
            }
            for (int i = 0; i < 4; i++) {
                FxUtil.p(level, ParticleTypes.LAVA, x + r.nextGaussian() * 20 * q, y + 1, z + r.nextGaussian() * 20 * q, 0, 0, 0);
                FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + r.nextGaussian() * 40 * q, y + 1, z + r.nextGaussian() * 40 * q, 0, 0.12, 0);
            }
            if (t % 2 == 0) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(r.nextDouble()) * 60 * q;
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + Math.cos(a) * d, y + 1, z + Math.sin(a) * d, 0, 0, 0);
            }
        }

        if (t >= FIRE && t < FIRE + 40) {
            for (int i = 0; i < 50; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = r.nextDouble() * Math.PI * 0.5;
                double sp = 1.5 + r.nextDouble() * 2.5;
                FxUtil.p(level, ParticleTypes.FLAME, x, y + 2, z, Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp);
            }
        }

        int since = t - FIRE;
        if (since >= 1 && since <= 50) {
            double rr = since * 5.2 * q;
            int n = (int) Math.min(110, rr * 0.6 + 10);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                FxUtil.p(level, ParticleTypes.CLOUD, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5);
                if (i % 4 == 0) FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.3, 0.3, Math.sin(a) * 0.3);
            }
        }
    }

    private static double pz(double z) {
        return z;
    }

    @Override
    public void renderSolid(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float ox = sx(t);
        float oy = sy(t);
        float q = s;
        float rot = t * 0.004F;

        float shadow = 0.38F * smooth(t, 20, ARRIVE) * (1.0F - smooth(t, DEPART, DEPART + 60));
        if (shadow > 0.01F) FxGeo.ring(vc, m, ox, 0.35F, 0, 0.0F, 125.0F * q, 48, 0.0F, 0.0F, 0.0F, shadow, shadow * 0.5F);

        if (oy < 3000.0F) {
            for (float[] h : HULL) {
                FxGeo.frustumSolid(vc, m, ox, oy, 0, h[0] * q, h[1] * q, h[2] * q, h[3] * q, 48, h[4], h[5], h[6], rot);
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float ox = sx(t);
        float oy = sy(t);
        float q = s;
        if (oy > 3000.0F) return;

        // luces del borde
        for (int i = 0; i < 64; i++) {
            float a = i * TAU / 64 + t * 0.008F;
            float da = 0.022F;
            float rr = 112.0F * q + 0.6F;
            boolean warm = i % 2 == 0;
            float cr = warm ? 1.0F : 0.4F;
            float cg = warm ? 0.65F : 0.95F;
            float cb = warm ? 0.25F : 1.0F;
            float al = 0.9F * (0.55F + 0.45F * sin(t * 0.2F + i));
            float h = 3.2F * q;
            FxGeo.quad(vc, m,
                    ox + cos(a - da) * rr, oy - h, sin(a - da) * rr, al,
                    ox + cos(a + da) * rr, oy - h, sin(a + da) * rr, al,
                    ox + cos(a + da) * rr, oy + h, sin(a + da) * rr, al,
                    ox + cos(a - da) * rr, oy + h, sin(a - da) * rr, al,
                    cr, cg, cb);
        }

        // brillo del vientre y del canon
        float charge = smooth(t, CHARGE - 30, FIRE);
        FxGeo.ring(vc, m, ox, oy - 31.0F * q, 0, 24.0F * q, 44.0F * q, 40, 0.4F, 0.8F, 1.0F, 0.35F, 0.0F);
        FxGeo.ring(vc, m, ox, oy - 41.0F * q, 0, 0.0F, 22.0F * q, 32, 0.6F, 0.9F, 1.0F, 0.2F + 0.7F * charge, 0.1F + 0.4F * charge);

        // reflectores
        float vis = smooth(t, ARRIVE - 20, ARRIVE + 20) * (1.0F - smooth(t, CHARGE, CHARGE + 30));
        if (vis > 0.01F) {
            for (int k = 0; k < 3; k++) {
                float ang = t * 0.025F + k * 2.094F;
                float er = (60.0F + 40.0F * sin(t * 0.02F + k)) * q;
                FxGeo.tube(vc, m, ox + cos(ang) * 50.0F * q, oy - 20.0F * q, sin(ang) * 50.0F * q,
                        cos(ang) * er, 0.0F, sin(ang) * er, 1.5F * q, 7.0F * q, 10, 0.7F, 0.9F, 1.0F, 0.25F * vis, 0.12F * vis);
            }
        }

        // esfera de carga
        float cs = smooth(t, CHARGE, FIRE) * (1.0F - smooth(t, FIRE_END, FIRE_END + 20));
        float sr = 20.0F * q * cs + (t >= FIRE && t < FIRE_END ? 6.0F * q * sin(t * 0.5F) : 0.0F);
        if (sr > 0.5F) {
            float cy = oy - 46.0F * q;
            FxGeo.sphere(vc, m, ox, cy, 0, sr * 0.5F, 8, 16, 1.0F, 1.0F, 1.0F, 0.9F);
            FxGeo.sphere(vc, m, ox, cy, 0, sr * 0.8F, 8, 16, 0.5F, 0.9F, 1.0F, 0.5F);
            FxGeo.sphere(vc, m, ox, cy, 0, sr * 1.15F, 8, 16, 0.3F, 0.7F, 1.0F, 0.2F);
        }

        // rayo
        if (t >= FIRE && t < FIRE_END + 25) {
            float b = 11.0F * q * smooth(t, FIRE, FIRE + 10) * (1.0F - smooth(t, FIRE_END, FIRE_END + 25)) * (1.0F + 0.06F * sin(t * 1.5F));
            float portY = oy - 44.0F * q;
            if (b > 0.1F) {
                FxGeo.glowBeam(vc, m, ox, 0, 0, ox, portY, 0, b, 0.6F, 0.9F, 1.0F, 1.0F, 16);
                FxGeo.glowBeam(vc, m, ox, 0, 0, ox, portY, 0, b * 2.2F, 1.0F, 0.55F, 0.2F, 0.4F, 16);
                for (int i = 0; i < 8; i++) {
                    float yy = portY - ((t * 9.0F + i * 60.0F) % portY);
                    FxGeo.ring(vc, m, ox, yy, 0, b * 1.1F, b * 2.6F, 24, 0.8F, 0.95F, 1.0F, 0.6F, 0.0F);
                }
            }
        }

        // bola de fuego y ondas en el suelo
        float fk = smooth(t, FIRE, FIRE + 90);
        float fa = 1.0F - smooth(t, FIRE + 60, FIRE + 210);
        if (fk > 0.0F && fa > 0.01F) {
            FxGeo.sphere(vc, m, 0, 0, 0, 38.0F * q * fk, 8, 20, 1.0F, 0.95F, 0.8F, 0.5F * fa);
            FxGeo.sphere(vc, m, 0, 0, 0, 60.0F * q * fk, 8, 24, 1.0F, 0.7F, 0.3F, 0.3F * fa);
            FxGeo.sphere(vc, m, 0, 0, 0, 80.0F * q * fk, 8, 28, 1.0F, 0.4F, 0.1F, 0.16F * fa);
        }
        float glowFloor = smooth(t, FIRE, FIRE + 100) * (1.0F - smooth(t, FIRE + 150, FIRE + 300));
        if (glowFloor > 0.01F) FxGeo.ring(vc, m, 0, 0.5F, 0, 0.0F, 90.0F * q, 48, 1.0F, 0.55F, 0.15F, 0.35F * glowFloor, 0.1F * glowFloor);

        float since = t - FIRE;
        float maxR = 240.0F * q;
        for (int k = 0; k < 4; k++) {
            float rr = (since - k * 14.0F) * 5.2F * q;
            if (rr > 1.0F && rr < maxR) {
                float fade = 1.0F - rr / maxR;
                float w = 3.0F + rr * 0.05F;
                FxGeo.ring(vc, m, 0, 0.9F + k, 0, rr - w, rr, 56, 1.0F, 0.75F, 0.4F, 0.0F, 0.75F * fade);
                FxGeo.ring(vc, m, 0, 0.9F + k, 0, rr, rr + w, 56, 1.0F, 0.75F, 0.4F, 0.75F * fade, 0.0F);
                FxGeo.tube(vc, m, 0, 0.5F, 0, 0, 20.0F + rr * 0.12F, 0, rr, rr + 5.0F, 56, 1.0F, 0.6F, 0.3F, 0.5F * fade, 0.0F);
            }
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.45F * smooth(t, 30, ARRIVE);
        a += 0.9F * smooth(t, CHARGE, FIRE);
        if (t >= FIRE) a += 4.0F * (float) Math.exp(-(t - FIRE) / 50.0F) + 1.6F * (1.0F - smooth(t, FIRE_END, FIRE_END + 60));
        return a * (1.0F - smooth(t, DEPART + 80, D));
    }

    @Override
    public float flash(float t) {
        if (t >= FIRE && t < FIRE + 20) {
            float k = 1.0F - (t - FIRE) / 20.0F;
            return k * k;
        }
        return 0.0F;
    }

    @Override
    public int tint(float t) {
        if (t < FIRE) {
            int al = (int) (100.0F * smooth(t, 10, ARRIVE));
            return (al << 24) | 0x050810;
        }
        int hot = (int) (70.0F * (1.0F - smooth(t, FIRE + 100, FIRE + 260)));
        int dark = (int) (100.0F * (1.0F - smooth(t, FIRE, FIRE + 60)) * (1.0F - smooth(t, DEPART, DEPART + 100)));
        if (hot >= dark) return (hot << 24) | 0xC03A10;
        return (dark << 24) | 0x050810;
    }
}
