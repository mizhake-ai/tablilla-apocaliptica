package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.PlanetKillerStrike;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;

/**
 * El Fin de los Mundos: siete obeliscos antiguos (solo se ven las puntas, con glifos que brillan) emergen en el horizonte y
 * descargan rayos sobre un anillo en el cielo; el rayo del anillo abre un crater inmenso.
 */
public class PlanetKillerFx implements FxEffect {
    private static final int RISE = PlanetKillerStrike.RISE;
    private static final int LIGHT = PlanetKillerStrike.LIGHTNING;
    private static final int BEAM = PlanetKillerStrike.BEAM;
    private static final int BEAM_END = PlanetKillerStrike.BEAM_END;
    private static final int D = PlanetKillerStrike.DURATION;
    private static final int N = 7;
    private static final float OB_H = 96.0F;
    private static final float RING_Y = 380.0F;

    private GroundMap ground;
    private List<Debris.Proto> protos;
    private final float[] gy = new float[N];

    @Override
    public int duration() {
        return D;
    }

    private static float angle(int k) {
        return k * TAU / N + 0.4F;
    }

    private static float yOff(float t, int k) {
        float up = smooth(t, RISE + k * 6, RISE + 90 + k * 6);
        float sink = smooth(t, BEAM + 40, BEAM + 160);
        return -OB_H * (1.0F - up) - OB_H * sink;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        float q = s;
        float ro = 215.0F * q;
        if (t == 1) {
            float half = Math.min(280.0F, 270.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(5.0F, half / 45.0F));
            for (int k = 0; k < N; k++) gy[k] = ground.at(cos(angle(k)) * ro, sin(angle(k)) * ro);
            protos = Debris.sample(level, x, z, 20.0 * s, 240.0 * s, 300, 0.5F, r);
            FxUtil.snd(level, "mothership.arrival", x, y, z, 16.0F, 0.6F);
        }
        if (t == RISE + 20) FxUtil.snd(level, "meteor.rumble", x, y, z, 16.0F, 0.6F);
        if (t == LIGHT) FxUtil.snd(level, "plasma.charge", x, y, z, 16.0F, 0.7F);
        if (t == BEAM) {
            FxUtil.snd(level, "plasma.fire", x, y, z, 16.0F, 0.7F);
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 0.6F);
            FxUtil.snd(level, "plasma.collapse", x, y, z, 16.0F, 0.6F);
            if (protos != null) Debris.burst(protos, x, y, z, 3.4, 3.6, 50, 1.5F, 3.4F, 240.0 * s, 40.0 * s, r);
            for (int i = 0; i < 40; i++) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * 60 * q, y + 2, z + r.nextGaussian() * 60 * q, 0, 0, 0);
            }
        }
        if (t == BEAM + 12) FxUtil.snd(level, "plasma.rumble", x, y, z, 16.0F, 0.7F);
        if (t == BEAM_END - 10) FxUtil.snd(level, "plasma.shutdown", x, y, z, 16.0F, 0.8F);
        int since = t - BEAM;
        if (since > 1 && since < 110) Debris.ripple(level, x, z, since * 8.0 * s, 8.0 * s, 26, r);

        // los obeliscos emergen: polvo y escombros en la base
        for (int k = 0; k < N; k++) {
            float up = smooth(t, RISE + k * 6, RISE + 90 + k * 6);
            if (up > 0.02F && up < 0.98F) {
                double bx = x + cos(angle(k)) * ro;
                double bz = z + sin(angle(k)) * ro;
                for (int i = 0; i < 6; i++) {
                    FxUtil.p(level, ParticleTypes.LARGE_SMOKE, bx + r.nextGaussian() * 20 * q, y + gy[k] + 1, bz + r.nextGaussian() * 20 * q, 0, 0.2, 0);
                    FxUtil.p(level, ParticleTypes.CLOUD, bx + r.nextGaussian() * 26 * q, y + gy[k] + 1, bz + r.nextGaussian() * 26 * q, 0, 0.1, 0);
                }
            }
        }

        // el rayo
        if (t >= BEAM && t < BEAM_END) {
            double big = 36.0 * q;
            for (int i = 0; i < 18; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = r.nextDouble() * big * 2.5;
                FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * d, y + 0.2, z + Math.sin(a) * d, 0, 0.4 + r.nextDouble() * 0.4, 0);
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * d * 1.6, y + 0.5, z + Math.sin(a) * d * 1.6, 0, 0.4, 0);
            }
            for (int i = 0; i < 10; i++) {
                FxUtil.p(level, ParticleTypes.END_ROD, x + r.nextGaussian() * big * 0.5, y + r.nextDouble() * 300, z + r.nextGaussian() * big * 0.5, 0, -0.4, 0);
            }
            if (t % 4 == 0) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * big, y + 1, z + r.nextGaussian() * big, 0, 0, 0);
            }
        }
    }

    // ------------------------------------------------------------------ obeliscos solidos

    @Override
    public boolean usesHull() {
        return true;
    }

    @Override
    public void renderHull(VertexConsumer vc, PoseStack.Pose pose, float t, long seed, float s) {
        if (ground == null) return;
        float q = s;
        float ro = 215.0F * q;
        TextureAtlasSprite[] sp = {HullGeo.sprite(Blocks.SMOOTH_BASALT), HullGeo.sprite(Blocks.POLISHED_ANDESITE),
                HullGeo.sprite(Blocks.CHISELED_STONE_BRICKS), HullGeo.sprite(Blocks.STONE_BRICKS),
                HullGeo.sprite(Blocks.MOSSY_STONE_BRICKS), HullGeo.sprite(Blocks.CRACKED_STONE_BRICKS)};
        float rot = (float) (Math.PI / 4.0);
        for (int k = 0; k < N; k++) {
            float yo = yOff(t, k);
            if (yo < -OB_H * q * 1.9F) continue;
            float px = cos(angle(k)) * ro;
            float pz = sin(angle(k)) * ro;
            float by = gy[k] + yo * q;
            // cuerpo en punta de piramide con repisas, y piramidion dorado arriba
            HullGeo.frustum(vc, pose, sp, px, by, pz, 0, 78.0F * q, 40.0F * q, 7.0F * q, 4, 2, 5, 0.9F, rot);
            HullGeo.frustum(vc, pose, sp, px, by, pz, 18.0F * q, 22.0F * q, 44.0F * q, 44.0F * q, 4, 1, 1, 0.8F, rot);
            HullGeo.frustum(vc, pose, sp, px, by, pz, 44.0F * q, 47.0F * q, 31.0F * q, 31.0F * q, 4, 1, 1, 0.8F, rot);
            HullGeo.frustum(vc, pose, new TextureAtlasSprite[] {HullGeo.sprite(Blocks.GOLD_BLOCK)}, px, by, pz, 78.0F * q, OB_H * q, 7.0F * q, 0.5F * q, 4, 1, 1, 1.0F, rot);
        }
    }

    // ------------------------------------------------------------------ luces

    /** Glifos tipo cuneiforme que se encienden de abajo hacia arriba en las 4 caras. */
    private void glyphs(VertexConsumer vc, Matrix4f m, float px, float by, float pz, float q, float act, float t, int k) {
        for (int f = 0; f < 4; f++) {
            float fa = f * TAU / 4;
            float nx = cos(fa);
            float nz = sin(fa);
            float tx = -nz;
            float tz = nx;
            for (int row = 0; row < 5; row++) {
                float hy = (10.0F + row * 13.0F) * q;
                if (hy > act * 70.0F * q) continue;
                float w = (40.0F + (7.0F - 40.0F) * (hy / (78.0F * q))) * 0.7071F * q;
                for (int c = -2; c <= 2; c++) {
                    float off = c * w * 0.36F;
                    float gx = px + nx * (w * 1.03F + 0.4F * q) + tx * off;
                    float gz = pz + nz * (w * 1.03F + 0.4F * q) + tz * off;
                    float gyy = by + hy;
                    float on = 0.55F + 0.45F * sin(t * 0.1F + row * 1.3F + c * 0.9F + k);
                    float sz = 2.2F * q;
                    int variant = (row * 7 + c * 3 + f) & 3;
                    // trazo horizontal
                    FxGeo.quad(vc, m, gx - tx * sz, gyy, gz - tz * sz, 0.9F * on, gx + tx * sz, gyy, gz + tz * sz, 0.9F * on,
                            gx + tx * sz, gyy + 0.6F * q, gz + tz * sz, 0.9F * on, gx - tx * sz, gyy + 0.6F * q, gz - tz * sz, 0.9F * on, 1.0F, 0.75F, 0.3F);
                    // trazo vertical o diagonal (cuña)
                    float vh = (variant == 0 ? 3.0F : 4.5F) * q;
                    float dxs = (variant == 2 ? 1.2F : 0.0F) * q;
                    FxGeo.quad(vc, m, gx - 0.5F * q * tx + dxs * tx, gyy, gz - 0.5F * q * tz + dxs * tz, 0.9F * on,
                            gx + 0.5F * q * tx + dxs * tx, gyy, gz + 0.5F * q * tz + dxs * tz, 0.9F * on,
                            gx + 0.5F * q * tx, gyy + vh, gz + 0.5F * q * tz, 0.9F * on,
                            gx - 0.5F * q * tx, gyy + vh, gz - 0.5F * q * tz, 0.9F * on, 1.0F, 0.75F, 0.3F);
                }
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float q = s;
        if (ground == null) return;
        float ro = 215.0F * q;
        float ringY = RING_Y * q;
        float ringR = 150.0F * q;
        float charge = smooth(t, RISE + 80, LIGHT + 60);
        float fadeAll = 1.0F - smooth(t, BEAM_END, BEAM_END + 120);

        // obeliscos: glifos y punta dorada
        for (int k = 0; k < N; k++) {
            float yo = yOff(t, k);
            float px = cos(angle(k)) * ro;
            float pz = sin(angle(k)) * ro;
            float by = gy[k] + yo * q;
            float act = smooth(t, RISE + 60 + k * 6, LIGHT + 40) * (1.0F - smooth(t, BEAM + 60, BEAM + 150));
            if (act > 0.02F) glyphs(vc, m, px, by, pz, q, act, t, k);
            FxGeo.sphere(vc, m, px, by + OB_H * q, pz, 5.0F * q * (0.3F + act), 5, 8, 1.0F, 0.85F, 0.4F, 0.8F * act);

            // rayos del obelisco al anillo
            float lt = smooth(t, LIGHT - 40 + k * 4, LIGHT + 10 + k * 4) * (1.0F - smooth(t, BEAM + 90, BEAM + 170));
            if (lt > 0.02F) {
                float tx = px;
                float ty = by + OB_H * q;
                float tz = pz;
                float ex = cos(angle(k)) * ringR;
                float ez = sin(angle(k)) * ringR;
                for (int b = 0; b < 3; b++) {
                    Random rr = new Random(seed * 41L + (long) (t / 2.0F) * 131L + k * 977L + b * 31L);
                    int n = 22;
                    float[] xs = new float[n];
                    float[] ys = new float[n];
                    float[] zs = new float[n];
                    for (int i = 0; i < n; i++) {
                        float u = i / (float) (n - 1);
                        float j = (i == 0 || i == n - 1) ? 0.0F : 1.0F;
                        xs[i] = tx + (ex - tx) * u + (rr.nextFloat() - 0.5F) * 30.0F * q * j;
                        ys[i] = ty + (ringY - ty) * u + (rr.nextFloat() - 0.5F) * 24.0F * q * j;
                        zs[i] = tz + (ez - tz) * u + (rr.nextFloat() - 0.5F) * 30.0F * q * j;
                    }
                    FxGeo.bolt(vc, m, xs, ys, zs, n, 0.8F * q, 0.9F, 0.97F, 1.0F, 0.95F * lt);
                    FxGeo.bolt(vc, m, xs, ys, zs, n, 3.0F * q, 0.55F, 0.75F, 1.0F, 0.30F * lt);
                }
            }
        }

        // el anillo del cielo
        if (charge > 0.02F && fadeAll > 0.02F) {
            float al = charge * fadeAll;
            for (int i = -2; i <= 2; i++) {
                FxGeo.ring(vc, m, 0, ringY + i * 3.0F * q, 0, ringR - 7.0F * q, ringR + 7.0F * q, 128, 0.7F, 0.9F, 1.0F, 0.55F * al, 0.55F * al);
            }
            FxGeo.ring(vc, m, 0, ringY, 0, ringR * 0.55F, ringR * 0.56F, 96, 1.0F, 0.9F, 0.6F, 0.8F * al, 0.8F * al);
            for (int i = 0; i < 48; i++) {
                float a = i * TAU / 48 + t * 0.004F;
                FxGeo.flatLine(vc, m, cos(a) * ringR * 0.84F, ringY + 2.0F, sin(a) * ringR * 0.84F, cos(a) * ringR * 0.97F, sin(a) * ringR * 0.97F,
                        2.0F * q, 1.0F, 0.9F, 0.6F, 0.8F * al);
            }
            FxGeo.sphere(vc, m, 0, ringY, 0, 26.0F * q * charge, 6, 12, 0.8F, 0.95F, 1.0F, 0.6F * al);
        }

        // el rayo del anillo
        if (t >= BEAM) {
            float rad = 40.0F * q * smooth(t, BEAM, BEAM + 10) * (1.0F - smooth(t, BEAM_END - 40, BEAM_END + 20)) * (1.0F + 0.06F * sin(t * 1.4F));
            if (rad > 0.5F) {
                FxGeo.glowBeam(vc, m, 0, 0, 0, 0, ringY, 0, rad, 1.0F, 1.0F, 1.0F, 1.0F, 18);
                FxGeo.glowBeam(vc, m, 0, 0, 0, 0, ringY, 0, rad * 2.6F, 0.55F, 0.8F, 1.0F, 0.34F, 18);
                for (int i = 0; i < 8; i++) {
                    float yy = ringY - ((t * 11.0F + i * 90.0F) % ringY);
                    FxGeo.ring(vc, m, 0, yy, 0, rad * 1.1F, rad * 2.7F, 28, 0.7F, 0.9F, 1.0F, 0.6F, 0.0F);
                }
            }
        }

        // ondas de choque y pared de fuego pegadas al relieve
        float since = t - BEAM;
        if (since > 0.0F) {
            float maxR = 340.0F * q;
            for (int k = 0; k < 4; k++) {
                float rr = (since - k * 16.0F) * 9.0F * q;
                if (rr > 1.0F && rr < maxR) {
                    float fade = 1.0F - rr / maxR;
                    float w = 4.0F + rr * 0.05F;
                    int sides = Math.max(64, Math.min(180, (int) (rr * 0.5F)));
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr - w, rr, sides, 0.9F + k, 0.8F, 0.9F, 1.0F, 0.0F, 0.8F * fade);
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr, rr + w, sides, 0.9F + k, 0.8F, 0.9F, 1.0F, 0.8F * fade, 0.0F);
                    FxGeo.terrainWall(vc, m, ground, 0, 0, rr, 28.0F + rr * 0.14F, sides, 0.6F, 1.0F, 0.75F, 0.5F, 0.6F * fade, 0.0F);
                }
            }
            float fk = smooth(t, BEAM, BEAM + 80);
            float fa = 1.0F - smooth(t, BEAM + 60, BEAM + 260);
            if (fk > 0.0F && fa > 0.01F) {
                FxGeo.sphere(vc, m, 0, 0, 0, 100.0F * q * fk, 10, 22, 1.0F, 0.97F, 0.9F, 0.55F * fa);
                FxGeo.sphere(vc, m, 0, 0, 0, 170.0F * q * fk, 10, 26, 0.8F, 0.85F, 1.0F, 0.28F * fa);
                FxGeo.sphere(vc, m, 0, 0, 0, 250.0F * q * fk, 10, 28, 1.0F, 0.55F, 0.2F, 0.13F * fa);
            }
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.4F * smooth(t, RISE, LIGHT) + 0.8F * smooth(t, LIGHT, BEAM);
        if (t >= BEAM) a += 7.0F * (float) Math.exp(-(t - BEAM) / 90.0F) + 1.5F * (1.0F - smooth(t, BEAM + 100, BEAM_END));
        return Math.min(a, 8.0F) * (1.0F - smooth(t, BEAM_END, D));
    }

    @Override
    public float flash(float t) {
        if (t >= BEAM && t < BEAM + 70) {
            float k = 1.0F - (t - BEAM) / 70.0F;
            return k * k;
        }
        return 0.0F;
    }

    @Override
    public float flare(float t) {
        return smooth(t, BEAM, BEAM + 8) * (1.0F - smooth(t, BEAM_END - 60, BEAM_END));
    }

    @Override
    public int flareColor() {
        return 0x9CC8FF;
    }

    @Override
    public float flareHeight() {
        return 120.0F;
    }

    @Override
    public int tint(float t) {
        int al = (int) (80.0F * smooth(t, 20, LIGHT) * (1.0F - smooth(t, BEAM_END, D)));
        return (al << 24) | 0x1A2250;
    }
}
