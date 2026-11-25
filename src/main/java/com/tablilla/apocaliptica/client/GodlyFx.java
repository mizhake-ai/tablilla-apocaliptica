package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.GodlyStrike;
import com.tablilla.apocaliptica.MagicCircle;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Juicio Divino: columna de luz dorada, circulos magicos de relampagos en el suelo, pilares, islas flotantes y bloques en el aire. */
public class GodlyFx implements FxEffect {
    private static final int C = GodlyStrike.CHARGE;
    private static final int E = GodlyStrike.BEAM_END;
    private static final int D = GodlyStrike.DURATION;
    private static final float TOP = 420.0F;

    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.3F), 3.0F);

    private GroundMap ground;
    private List<Debris.Proto> protos;

    @Override
    public int duration() {
        return D;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) {
            float half = Math.min(280.0F, 128.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(4.0F, half / 45.0F));
            protos = Debris.sample(level, x, z, 4.0 * s, 62.0 * s, 210, 0.5F, r);
            FxUtil.snd(level, "godly.lockon", x, y, z, 16.0F, 1.0F);
        }
        if (t == 6) FxUtil.snd(level, "godly.charge", x, y, z, 16.0F, 1.0F);
        if (t == C) {
            FxUtil.snd(level, "godly.fire", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "godly.beam", x, y, z, 16.0F, 1.0F);
        }
        if (t == C + 1 && protos != null) {
            Debris.burst(protos, x, y, z, 1.3, 1.8, 26, 0.9F, 1.6F, 52.0 * s, 28.0 * s, r);
        }
        if (t == C + 6) Debris.islands(level, x, y, z, 8, 14.0 * s, 62.0 * s, 24, r);
        if (t > C + 1 && t < C + 62) Debris.ripple(level, x, z, (t - C) * 3.2 * s, 4.0 * s, 16, r);
        if (t == C + 14) FxUtil.snd(level, "godly.rumble", x, y, z, 16.0F, 1.0F);
        if (t == C + 40) FxUtil.snd(level, "godly.accent", x, y, z, 16.0F, 1.0F);
        if (t == E - 8) FxUtil.snd(level, "godly.shutdown", x, y, z, 16.0F, 1.0F);

        if (t < C) {
            float k = t / (float) C;
            for (int i = 0; i < 8; i++) {
                double a = t * 0.25 + i * 0.785;
                double rad = 40.0 * s * (1.0 - k) + 3.0;
                double c = Math.cos(a);
                double sn = Math.sin(a);
                FxUtil.p(level, ParticleTypes.END_ROD, x + c * rad, y + 0.3 + r.nextDouble() * 3, z + sn * rad, -c * 0.3, 0.2, -sn * 0.3);
                FxUtil.p(level, GOLD, x + c * rad, y + 0.5, z + sn * rad, -c * 0.2, 0.15, -sn * 0.2);
            }
        } else if (t < E) {
            double big = 6.0 * s;
            for (int i = 0; i < 10; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = r.nextDouble() * big * 2.0;
                FxUtil.p(level, ParticleTypes.END_ROD, x + Math.cos(a) * d, y + 0.3, z + Math.sin(a) * d, 0, 0.5 + r.nextDouble() * 0.7, 0);
                FxUtil.p(level, GOLD, x + Math.cos(a) * d, y + 0.3, z + Math.sin(a) * d, 0, 0.4 + r.nextDouble() * 0.5, 0);
            }
            for (int i = 0; i < 4; i++) {
                FxUtil.p(level, ParticleTypes.WAX_ON, x + r.nextGaussian() * big, y + r.nextDouble() * 40, z + r.nextGaussian() * big, 0, 0.1, 0);
            }
            if (t % 6 == 0) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * big * 2, y + 1, z + r.nextGaussian() * big * 2, 0, 0, 0);
            }
            int bt = t - C;
            if (bt <= 55) {
                double rr = bt * 3.4 * s;
                int n = (int) Math.min(70, rr * 0.7 + 10);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    FxUtil.p(level, ParticleTypes.END_ROD, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.3, 0.1, Math.sin(a) * 0.3);
                }
            }
        }
    }

    // ------------------------------------------------------------------ circulos magicos

    /** Una linea de luz dorada con nucleo blanco, que se va dibujando (frac 0 a 1). */
    private void line(VertexConsumer vc, Matrix4f m, float ax, float az, float bx, float bz, float frac, float w, float a) {
        if (frac <= 0.001F) return;
        float ex = ax + (bx - ax) * frac;
        float ez = az + (bz - az) * frac;
        FxGeo.terrainLine(vc, m, ground, ax, az, ex, ez, w * 3.2F, 0.35F, 1.0F, 0.82F, 0.35F, a * 0.32F, a * 0.32F);
        FxGeo.terrainLine(vc, m, ground, ax, az, ex, ez, w, 0.42F, 1.0F, 0.97F, 0.82F, a, a);
    }

    private void circle(VertexConsumer vc, Matrix4f m, float cx, float cz, float rad, int sides, float frac, float w, float a) {
        if (frac <= 0.001F) return;
        FxGeo.polyCircle(vc, m, ground, cx, cz, rad, sides, w * 3.2F, 0.35F, 1.0F, 0.82F, 0.35F, a * 0.32F, frac);
        FxGeo.polyCircle(vc, m, ground, cx, cz, rad, sides, w, 0.42F, 1.0F, 0.97F, 0.82F, a, frac);
    }

    private void magicCircles(VertexConsumer vc, Matrix4f m, float t, float s, float sg) {
        float R = (float) MagicCircle.RADIUS * s;
        float w = Math.max(1.0F, s) * 0.8F;
        float a = 0.95F * sg;

        // circulo exterior blanco con hexagono y 6 circulos pequeños
        circle(vc, m, 0, 0, R, 128, smooth(t, 0, 30), w * 1.3F, a);
        float hexR = (float) MagicCircle.HEX_R * R;
        float hv = smooth(t, 10, 40);
        for (int k = 0; k < 6; k++) {
            float a0 = (float) MagicCircle.hexAngle(k);
            float a1 = (float) MagicCircle.hexAngle(k + 1);
            line(vc, m, cos(a0) * hexR, sin(a0) * hexR, cos(a1) * hexR, sin(a1) * hexR, hv, w, a);
            circle(vc, m, cos(a0) * hexR, sin(a0) * hexR, (float) MagicCircle.SMALL_R * R, 36, smooth(t, 20, 50), w, a);
        }

        // anillo de texto (marcas que giran despacio)
        float tIn = (float) MagicCircle.TEXT_IN * R;
        float tOut = (float) MagicCircle.TEXT_OUT * R;
        float tv = smooth(t, 20, 48);
        circle(vc, m, 0, 0, tIn, 96, tv, w * 0.8F, a * 0.9F);
        circle(vc, m, 0, 0, tOut, 96, tv, w * 0.8F, a * 0.9F);
        int ticks = 72;
        for (int i = 0; i < ticks * tv; i++) {
            float ang = i * TAU / ticks + t * 0.004F;
            float len = (i % 3 == 0) ? 1.0F : ((i % 3 == 1) ? 0.55F : 0.8F);
            float r1 = tIn + 0.8F * s;
            float r2 = r1 + (tOut - tIn - 1.6F * s) * len;
            FxGeo.terrainLine(vc, m, ground, cos(ang) * r1, sin(ang) * r1, cos(ang) * r2, sin(ang) * r2,
                    w * 0.9F, 0.42F, 1.0F, 0.95F, 0.75F, a * 0.8F, a * 0.8F);
        }

        // circulo interior: anillo, hexagrama, runas y 3 puntos
        float ringR = (float) MagicCircle.RING_R * R;
        circle(vc, m, 0, 0, ringR, 112, smooth(t, 25, 55), w * 1.1F, a);
        float starR = (float) MagicCircle.STAR_R * R;
        float sv = smooth(t, 28, 58);
        for (int k = 0; k < 6; k++) {
            float a0 = (float) MagicCircle.hexAngle(k) + TAU / 12.0F;
            float a1 = (float) MagicCircle.hexAngle(k + 2) + TAU / 12.0F;
            line(vc, m, cos(a0) * starR, sin(a0) * starR, cos(a1) * starR, sin(a1) * starR, sv, w, a);
        }
        int runes = 48;
        float rr1 = ringR + 1.2F * s;
        float rr2 = tIn - 1.2F * s;
        for (int i = 0; i < runes * sv; i++) {
            float ang = -i * TAU / runes - t * 0.006F;
            float len = 0.35F + 0.65F * ((i * 7) % 5) / 4.0F;
            float r2 = rr1 + (rr2 - rr1) * len;
            FxGeo.terrainLine(vc, m, ground, cos(ang) * rr1, sin(ang) * rr1, cos(ang) * r2, sin(ang) * r2,
                    w * 0.9F, 0.42F, 1.0F, 0.9F, 0.6F, a * 0.8F, a * 0.8F);
        }
        float pR = (float) MagicCircle.POINT_R * R;
        for (int k = 0; k < 3; k++) {
            float ang = (float) MagicCircle.pointAngle(k);
            float px = cos(ang) * pR;
            float pz = sin(ang) * pR;
            float pv = smooth(t, 35, 60);
            circle(vc, m, px, pz, (float) MagicCircle.POINT_SIZE * R, 28, pv, w, a);
            circle(vc, m, px, pz, (float) MagicCircle.POINT_SIZE * R * 0.45F, 20, pv, w * 3.0F, a);
        }
    }


    // ------------------------------------------------------------------ efectos celestiales

    /** Circulo magico horizontal flotante: anillos, un poligono inscrito y marcas, girando. shape = lados del poligono (0 = estrella). */
    private void halo(VertexConsumer vc, Matrix4f m, float t, float y, float rad, int shape, float spin, float al) {
        float w = Math.max(0.8F, rad * 0.025F);
        FxGeo.ring(vc, m, 0, y, 0, rad - w, rad, 64, 1.0F, 0.9F, 0.55F, al, al);
        FxGeo.ring(vc, m, 0, y, 0, rad * 0.80F - w * 0.7F, rad * 0.80F, 56, 1.0F, 0.95F, 0.75F, al * 0.8F, al * 0.8F);
        int n = shape == 0 ? 5 : shape;
        float pr = rad * 0.78F;
        for (int i = 0; i < n; i++) {
            float a0 = spin + i * TAU / n;
            int skip = shape == 0 ? 2 : 1;
            float a1 = spin + (i + skip) * TAU / n;
            FxGeo.flatLine(vc, m, cos(a0) * pr, y, sin(a0) * pr, cos(a1) * pr, sin(a1) * pr, w, 1.0F, 0.97F, 0.8F, al);
            FxGeo.ring(vc, m, cos(a0) * pr, y, sin(a0) * pr, rad * 0.06F, rad * 0.08F + w, 12, 1.0F, 0.9F, 0.6F, al * 0.9F, al * 0.9F);
        }
        int ticks = 36;
        for (int i = 0; i < ticks; i++) {
            float a = -spin * 0.6F + i * TAU / ticks;
            float r1 = rad * 0.86F;
            float r2 = rad * (i % 2 == 0 ? 0.96F : 0.91F);
            FxGeo.flatLine(vc, m, cos(a) * r1, y, sin(a) * r1, cos(a) * r2, sin(a) * r2, w * 0.8F, 1.0F, 0.92F, 0.65F, al * 0.8F);
        }
    }

    /** Ala de serafin: abanico de plumas (cintas afiladas) que sale del circulo central, en un plano que mira a la camara. */
    private void wing(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float ux, float uz, float side,
                      float baseAngle, float span, float len, int feathers, float t, float al) {
        for (int i = 0; i < feathers; i++) {
            float k = feathers == 1 ? 0.5F : (float) i / (feathers - 1);
            float ang = baseAngle + (k - 0.5F) * span + 0.04F * sin(t * 0.05F + i);
            float l = len * (0.45F + 0.55F * (float) Math.sin(Math.PI * (0.15 + 0.85 * (1.0 - k))));
            float dx = cos(ang) * side;
            float dh = sin(ang);
            float r0 = len * 0.10F;
            float wb = len * 0.035F;
            FxGeo.planeLine(vc, m, cx, cy, cz, ux, uz, dx * r0, dh * r0, dx * (r0 + l), dh * (r0 + l), wb, 0.0F,
                    1.0F, 0.95F, 0.8F, al, al * 0.15F);
            FxGeo.planeLine(vc, m, cx, cy, cz, ux, uz, dx * r0, dh * r0, dx * (r0 + l * 0.9F), dh * (r0 + l * 0.9F), wb * 2.6F, 0.0F,
                    1.0F, 0.85F, 0.45F, al * 0.30F, 0.0F);
        }
    }

    private void seraph(VertexConsumer vc, Matrix4f m, float t, float s, float vis) {
        // circulos magicos horizontales a distintos niveles, tamaños y formas
        float[] hy = {26.0F, 60.0F, 105.0F, 155.0F, 215.0F, 300.0F, 360.0F};
        float[] hr = {58.0F, 40.0F, 70.0F, 34.0F, 52.0F, 78.0F, 46.0F};
        int[] hs = {0, 3, 6, 4, 8, 0, 6};
        for (int i = 0; i < hy.length; i++) {
            float k = smooth(t, C - 10 + i * 5, C + 25 + i * 5);
            float al = 0.75F * vis * k;
            if (al < 0.02F) continue;
            halo(vc, m, t, hy[i] * s, hr[i] * s * (0.9F + 0.1F * k), hs[i], t * 0.012F * (i % 2 == 0 ? 1 : -1) + i, al);
        }

        // circulo central grande (vertical, mirando a la camara) y 6 alas de distintos tamaños
        float cxz = (float) Math.sqrt(FxCtx.camX * FxCtx.camX + FxCtx.camZ * FxCtx.camZ);
        float dx = cxz < 1.0F ? 0.0F : (float) FxCtx.camX / cxz;
        float dz = cxz < 1.0F ? 1.0F : (float) FxCtx.camZ / cxz;
        float ux = -dz;
        float uz = dx;
        float cy = 250.0F * s;
        float R = 46.0F * s;
        float grow = smooth(t, C, C + 40);
        float al = 0.85F * vis * grow;
        if (al < 0.02F) return;
        int sides = 56;
        for (int i = 0; i < sides; i++) {
            float a0 = TAU * i / sides;
            float a1 = TAU * (i + 1) / sides;
            FxGeo.planeLine(vc, m, 0, cy, 0, ux, uz, cos(a0) * R, sin(a0) * R, cos(a1) * R, sin(a1) * R, R * 0.07F, R * 0.07F, 1.0F, 0.9F, 0.55F, al, al);
            FxGeo.planeLine(vc, m, 0, cy, 0, ux, uz, cos(a0) * R * 0.72F, sin(a0) * R * 0.72F, cos(a1) * R * 0.72F, sin(a1) * R * 0.72F,
                    R * 0.035F, R * 0.035F, 1.0F, 0.97F, 0.8F, al * 0.9F, al * 0.9F);
        }
        for (int i = 0; i < 6; i++) {
            float a0 = t * 0.008F + i * TAU / 6;
            float a1 = t * 0.008F + (i + 2) * TAU / 6;
            FxGeo.planeLine(vc, m, 0, cy, 0, ux, uz, cos(a0) * R * 0.7F, sin(a0) * R * 0.7F, cos(a1) * R * 0.7F, sin(a1) * R * 0.7F,
                    R * 0.03F, R * 0.03F, 1.0F, 0.95F, 0.75F, al * 0.8F, al * 0.8F);
        }
        FxGeo.sphere(vc, m, 0, cy, 0, R * 0.30F, 6, 12, 1.0F, 1.0F, 0.9F, 0.55F * al);
        FxGeo.sphere(vc, m, 0, cy, 0, R * 0.55F, 6, 12, 1.0F, 0.85F, 0.4F, 0.18F * al);

        // 3 alas a cada lado: grande arriba, mediana al centro y chica abajo (el tamaño varia)
        float[] ang = {0.62F, 0.0F, -0.55F};
        float[] len = {230.0F, 300.0F, 170.0F};
        float[] span = {0.95F, 0.85F, 0.8F};
        int[] feath = {16, 20, 14};
        float flap = 0.04F * sin(t * 0.04F);
        for (int side = -1; side <= 1; side += 2) {
            for (int w = 0; w < 3; w++) {
                float sc = (0.85F + 0.15F * grow) * s;
                wing(vc, m, 0, cy, 0, ux, uz, side, ang[w] + flap * (w == 0 ? 1 : -1), span[w], len[w] * sc, feath[w], t, 0.7F * al);
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float sg = smooth(t, 0, 20) * (1.0F - smooth(t, E, E + 35));
        if (sg > 0.01F && ground != null) magicCircles(vc, m, t, s, sg);
        float vis = smooth(t, C - 10, C + 20) * (1.0F - smooth(t, E - 10, E + 35));
        if (vis > 0.01F) seraph(vc, m, t, s, vis);

        float rad;
        float yLow;
        if (t < C) {
            float k = t / (float) C;
            yLow = lerp(TOP, 0.0F, k * k);
            rad = (0.6F + 1.6F * k) * s;
        } else {
            yLow = 0.0F;
            rad = 6.0F * s * smooth(t, C, C + 8) * (1.0F - smooth(t, E - 10, E + 5)) * (1.0F + 0.05F * sin(t * 1.5F));
        }
        if (rad > 0.05F) {
            FxGeo.glowBeam(vc, m, 0, yLow - 2, 0, 0, TOP, 0, rad, 1.0F, 0.84F, 0.35F, 1.0F, 14);
            if (t >= C) {
                FxGeo.glowBeam(vc, m, 0, -2, 0, 0, TOP, 0, rad * 2.8F, 1.0F, 0.9F, 0.6F, 0.3F, 14);
                // rayos de luz abriendose hacia el suelo
                float ca = 0.20F * smooth(t, C, C + 20) * (1.0F - smooth(t, E - 10, E + 20));
                FxGeo.tube(vc, m, 0, 0, 0, 0, TOP, 0, 70.0F * s, 1.0F, 24, 1.0F, 0.9F, 0.55F, ca, ca * 0.2F);
            }
        }

        if (t >= C && rad > 0.5F) {
            for (int i = 0; i < 5; i++) {
                float yy = ((t * 4.0F + i * 84.0F) % TOP);
                float fade = 1.0F - yy / TOP;
                FxGeo.ring(vc, m, 0, yy, 0, 24.0F * s * (0.6F + 0.4F * fade), 28.0F * s * (0.6F + 0.4F * fade), 48,
                        1.0F, 0.9F, 0.5F, 0.65F * fade, 0.65F * fade);
                for (int k = 0; k < 12; k++) {
                    float a = k * TAU / 12 + t * 0.03F * (i % 2 == 0 ? 1 : -1);
                    FxGeo.sector(vc, m, 0, yy + 0.3F, 0, 19.0F * s, 23.0F * s, a, a + 0.08F, 1.0F, 1.0F, 0.8F, 0.7F * fade, 0.7F * fade);
                }
            }
        }

        // pilares blancos de luz alrededor del crater
        float pil = smooth(t, C + 8, C + 45) * (1.0F - smooth(t, E, E + 40));
        if (pil > 0.01F && ground != null) {
            float pr = (float) MagicCircle.RING_R * (float) MagicCircle.RADIUS * s;
            for (int i = 0; i < 8; i++) {
                float a = i * TAU / 8 + 0.2F;
                float px = cos(a) * pr;
                float pz = sin(a) * pr;
                float base = ground.at(px, pz);
                FxGeo.glowBeam(vc, m, px, base, pz, px, base + 170.0F * pil, pz, 1.6F * s, 1.0F, 0.95F, 0.8F, 0.85F, 8);
            }
        }

        // ondas de choque que siguen el relieve
        if (ground != null) {
            float since = t - C;
            float maxR = 200.0F * s;
            for (int k = 0; k < 3; k++) {
                float rr = (since - k * 10.0F) * 3.2F * s;
                if (rr > 1.0F && rr < maxR) {
                    float fade = 1.0F - rr / maxR;
                    float w = 2.0F + rr * 0.04F;
                    int sides = Math.max(40, Math.min(150, (int) (rr * 0.6F)));
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr - w, rr, sides, 0.8F + k, 1.0F, 0.92F, 0.6F, 0.0F, 0.7F * fade);
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr, rr + w, sides, 0.8F + k, 1.0F, 0.92F, 0.6F, 0.7F * fade, 0.0F);
                    FxGeo.terrainWall(vc, m, ground, 0, 0, rr, 8.0F + rr * 0.05F, sides, 0.6F, 1.0F, 0.9F, 0.5F, 0.4F * fade, 0.0F);
                }
            }
        }

        float dk = smooth(t, C, C + 26);
        float da = 0.3F * (1.0F - smooth(t, C + 16, C + 66));
        if (dk > 0.0F && da > 0.01F) {
            FxGeo.sphere(vc, m, 0, 0, 0, 16.0F * s * dk, 6, 16, 1.0F, 1.0F, 1.0F, da * 1.5F);
            FxGeo.sphere(vc, m, 0, 0, 0, 32.0F * s * dk, 8, 20, 1.0F, 0.88F, 0.45F, da);
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.35F * smooth(t, 20, C);
        if (t >= C) a += 1.6F * (float) Math.exp(-(t - C) / 35.0F) + 0.5F * (1.0F - smooth(t, 140, E));
        return a * (1.0F - smooth(t, E, D));
    }

    @Override
    public float flash(float t) {
        if (t >= C && t < C + 18) {
            float k = 1.0F - (t - C) / 18.0F;
            return k * k;
        }
        return 0.0F;
    }

    @Override
    public int tint(float t) {
        int al = (int) (55.0F * smooth(t, 30, C + 10) * (1.0F - smooth(t, E, D)));
        return (al << 24) | 0xFFD670;
    }
}
