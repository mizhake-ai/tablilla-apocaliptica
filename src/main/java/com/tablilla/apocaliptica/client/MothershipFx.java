package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.MothershipStrike;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * La Barca Celeste: una nave colosal, SOLIDA (hecha de paneles de bloques), con lineas verdes y rejillas naranjas,
 * que llega, carga su canon, arrasa la zona con un rayo verde y se marcha.
 */
public class MothershipFx implements FxEffect {
    private static final int ARRIVE = MothershipStrike.ARRIVE;
    private static final int CHARGE = MothershipStrike.CHARGE;
    private static final int FIRE = MothershipStrike.FIRE;
    private static final int FIRE_END = MothershipStrike.FIRE_END;
    private static final int DEPART = MothershipStrike.DEPART;
    private static final int D = MothershipStrike.DURATION;
    private static final float HOVER = 170.0F;

    private GroundMap ground;
    private List<Debris.Proto> protos;

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
        if (t == 1) {
            float half = Math.min(270.0F, 250.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(4.5F, half / 45.0F));
            protos = Debris.sample(level, x, z, 4.0 * s, 115.0 * s, 300, 0.5F, r);
            FxUtil.snd(level, "mothership.arrival", x, y, z, 16.0F, 1.0F);
        }
        if (t == 100 || t == 400) FxUtil.snd(level, "mothership.hum", x, y, z, 16.0F, 1.0F);
        if (t == 130) FxUtil.snd(level, "mothership.sweep1", x, y, z, 16.0F, 1.0F);
        if (t == 190) FxUtil.snd(level, "mothership.sweep2", x, y, z, 16.0F, 1.0F);
        if (t == CHARGE) FxUtil.snd(level, "mothership.charge", x, y, z, 16.0F, 1.0F);
        if (t == CHARGE + 10) FxUtil.snd(level, "mothership.rumble", x, y, z, 16.0F, 1.0F);
        if (t == FIRE) {
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "mothership.fire", x, y, z, 16.0F, 1.0F);
        }
        if (t == FIRE + 1 && protos != null) {
            Debris.burst(protos, x, y, z, 2.3, 2.6, 40, 1.0F, 2.2F, 88.0 * s, 46.0 * s, r);
            BlockState[] hot = {Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState()};
            Debris.burst(Debris.synthetic(hot, x, y, z, 12.0 * s, 70, r), x, y, z, 1.4, 3.6, 8, 1.0F, 2.4F, 30.0 * s, 46.0 * s, r);
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
                FxUtil.p(level, ParticleTypes.END_ROD, px + dx * 30 * q, py + dy * 30 * q, z + dz * 30 * q, -dx * 0.8, -dy * 0.8, -dz * 0.8);
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

    // ------------------------------------------------------------------ casco solido

    private final ShipModel ship = new ShipModel();

    @Override
    public boolean usesHull() {
        return true;
    }

    @Override
    public void renderHull(VertexConsumer vc, PoseStack.Pose pose, float t, long seed, float s) {
        float oy = sy(t);
        if (oy > 3000.0F) return;
        float ox = sx(t);
        ShipModel.push(pose, ox, oy, 0, 0, 0);
        ship.hull(vc, pose, seed, s, 0.0F);
        ShipModel.pop(pose, ox, oy, 0, 0, 0);
    }

    @Override
    public void renderSolid(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float ox = sx(t);
        float shadow = 0.38F * smooth(t, 20, ARRIVE) * (1.0F - smooth(t, DEPART, DEPART + 60));
        if (shadow > 0.01F) FxGeo.ring(vc, m, ox, 0.35F, 0, 0.0F, 125.0F * s, 48, 0.0F, 0.0F, 0.0F, shadow, shadow * 0.5F);
    }

    // ------------------------------------------------------------------ luces

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float ox = sx(t);
        float oy = sy(t);
        float q = s;

        if (oy < 3000.0F) {
            ShipModel.push(m, ox, oy, 0, 0, 0);
            ship.lights(vc, m, t, q, 0.3F, 1.0F, 0.5F, 1.0F, 0.0F);
            ShipModel.pop(m, ox, oy, 0, 0, 0);

            // brillo del canon
            float charge = smooth(t, CHARGE - 30, FIRE);
            FxGeo.ring(vc, m, ox, oy - 41.0F * q, 0, 0.0F, 22.0F * q, 32, 0.5F, 1.0F, 0.65F, 0.2F + 0.7F * charge, 0.1F + 0.4F * charge);

            // esfera de carga
            float cs = smooth(t, CHARGE, FIRE) * (1.0F - smooth(t, FIRE_END, FIRE_END + 20));
            float sr = 20.0F * q * cs + (t >= FIRE && t < FIRE_END ? 6.0F * q * sin(t * 0.5F) : 0.0F);
            if (sr > 0.5F) {
                float cy = oy - 46.0F * q;
                FxGeo.sphere(vc, m, ox, cy, 0, sr * 0.5F, 8, 16, 1.0F, 1.0F, 1.0F, 0.9F);
                FxGeo.sphere(vc, m, ox, cy, 0, sr * 0.8F, 8, 16, 0.4F, 1.0F, 0.6F, 0.5F);
                FxGeo.sphere(vc, m, ox, cy, 0, sr * 1.15F, 8, 16, 0.25F, 0.9F, 0.45F, 0.2F);
            }

            // rayo verde
            if (t >= FIRE && t < FIRE_END + 25) {
                float b = 11.0F * q * smooth(t, FIRE, FIRE + 10) * (1.0F - smooth(t, FIRE_END, FIRE_END + 25)) * (1.0F + 0.06F * sin(t * 1.5F));
                float portY = oy - 44.0F * q;
                if (b > 0.1F) {
                    FxGeo.glowBeam(vc, m, ox, 0, 0, ox, portY, 0, b, 0.45F, 1.0F, 0.6F, 1.0F, 16);
                    FxGeo.glowBeam(vc, m, ox, 0, 0, ox, portY, 0, b * 2.2F, 0.25F, 1.0F, 0.45F, 0.38F, 16);
                    for (int i = 0; i < 8; i++) {
                        float yy = portY - ((t * 9.0F + i * 60.0F) % portY);
                        FxGeo.ring(vc, m, ox, yy, 0, b * 1.1F, b * 2.6F, 24, 0.6F, 1.0F, 0.75F, 0.6F, 0.0F);
                    }
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

        if (ground != null && t > FIRE) {
            // grietas de lava que se abren desde el impacto, pegadas al relieve
            float ck = smooth(t, FIRE, FIRE + 70);
            float cf = ck * (1.0F - smooth(t, FIRE + 170, FIRE + 330));
            if (cf > 0.01F) {
                for (int c = 0; c < 14; c++) {
                    Random rr = new Random(seed * 13L + c * 7717L);
                    float ang = rr.nextFloat() * TAU;
                    float px = 0.0F;
                    float pz = 0.0F;
                    int pieces = 14;
                    for (int i = 0; i < pieces; i++) {
                        if (i > pieces * ck) break;
                        ang += (rr.nextFloat() - 0.5F) * 0.55F;
                        float nx = px + cos(ang) * 9.0F * q;
                        float nz = pz + sin(ang) * 9.0F * q;
                        float fade = 1.0F - i / (float) pieces;
                        FxGeo.terrainLine(vc, m, ground, px, pz, nx, nz, 4.2F * q, 0.5F, 1.0F, 0.4F, 0.08F, 0.35F * cf * fade, 0.3F * cf * fade);
                        FxGeo.terrainLine(vc, m, ground, px, pz, nx, nz, 1.4F * q, 0.6F, 1.0F, 0.85F, 0.45F, 0.95F * cf * fade, 0.9F * cf * fade);
                        px = nx;
                        pz = nz;
                    }
                }
            }

            // ondas de choque y pared de fuego pegadas al relieve
            float since = t - FIRE;
            float maxR = 240.0F * q;
            for (int k = 0; k < 4; k++) {
                float rr = (since - k * 14.0F) * 5.2F * q;
                if (rr > 1.0F && rr < maxR) {
                    float fade = 1.0F - rr / maxR;
                    float w = 3.0F + rr * 0.05F;
                    int sides = Math.max(48, Math.min(160, (int) (rr * 0.6F)));
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr - w, rr, sides, 0.9F + k, 1.0F, 0.75F, 0.4F, 0.0F, 0.75F * fade);
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr, rr + w, sides, 0.9F + k, 1.0F, 0.75F, 0.4F, 0.75F * fade, 0.0F);
                    FxGeo.terrainWall(vc, m, ground, 0, 0, rr, 20.0F + rr * 0.12F, sides, 0.6F, 1.0F, 0.6F, 0.3F, 0.55F * fade, 0.0F);
                }
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
    public float flare(float t) {
        return smooth(t, FIRE, FIRE + 8) * (1.0F - smooth(t, FIRE_END - 20, FIRE_END));
    }

    @Override
    public int flareColor() {
        return 0x66FF99;
    }

    @Override
    public float flareHeight() {
        return 100.0F;
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
