package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.sin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;

/** La nave nodriza (casco solido de paneles de bloques + luces). La comparten La Barca Celeste y La Caida. Se dibuja centrada en el origen. */
public final class ShipModel {
    public static final int SIDES = 32;

    // y0, y1, radio abajo, radio arriba, brillo
    private static final float[][] HULL = {
        {-40, -30, 24, 44, 0.70F},
        {-30, -16, 44, 82, 0.75F},
        {-16, -5, 82, 112, 0.82F},
        {-5, 3, 112, 112, 1.05F},
        {3, 13, 112, 80, 0.95F},
        {13, 22, 80, 46, 0.88F},
        {22, 36, 46, 18, 0.82F},
        {36, 40, 18, 6, 1.00F}
    };

    private float[][] topBoxes;
    private float[][] botBoxes;

    /** Mueve y gira el sistema de coordenadas (hay que deshacerlo con pop). */
    public static void push(PoseStack.Pose p, float x, float y, float z, float rz, float rx) {
        p.pose().translate(x, y, z).rotateZ(rz).rotateX(rx);
        p.normal().rotateZ(rz).rotateX(rx);
    }

    public static void pop(PoseStack.Pose p, float x, float y, float z, float rz, float rx) {
        p.normal().rotateX(-rx).rotateZ(-rz);
        p.pose().rotateX(-rx).rotateZ(-rz).translate(-x, -y, -z);
    }

    public static void push(Matrix4f m, float x, float y, float z, float rz, float rx) {
        m.translate(x, y, z).rotateZ(rz).rotateX(rx);
    }

    public static void pop(Matrix4f m, float x, float y, float z, float rz, float rx) {
        m.rotateX(-rx).rotateZ(-rz).translate(-x, -y, -z);
    }

    /** Altura de la cara de arriba del casco a ese radio (sin escala). */
    public static float topY(float r) {
        if (r >= 112.0F) return 3.0F;
        if (r >= 80.0F) return 3.0F + (112.0F - r) / 32.0F * 10.0F;
        if (r >= 46.0F) return 13.0F + (80.0F - r) / 34.0F * 9.0F;
        if (r >= 18.0F) return 22.0F + (46.0F - r) / 28.0F * 14.0F;
        return 36.0F + (18.0F - r) / 12.0F * 4.0F;
    }

    /** Altura de la panza del casco a ese radio (sin escala). */
    public static float botY(float r) {
        if (r <= 24.0F) return -40.0F;
        if (r <= 44.0F) return -40.0F + (r - 24.0F) / 20.0F * 10.0F;
        if (r <= 82.0F) return -30.0F + (r - 44.0F) / 38.0F * 14.0F;
        return -16.0F + Math.min(r - 82.0F, 30.0F) / 30.0F * 11.0F;
    }

    private void makeBoxes(long seed) {
        Random r = new Random(seed * 7L + 13L);
        topBoxes = new float[120][];
        for (int i = 0; i < topBoxes.length; i++) {
            float rr = 14.0F + (float) Math.sqrt(r.nextFloat()) * 92.0F;
            float a = r.nextFloat() * TAU;
            float hx = 2.0F + r.nextFloat() * 5.0F;
            float hz = 2.0F + r.nextFloat() * 5.0F;
            float hy = 1.0F + r.nextFloat() * 3.5F;
            topBoxes[i] = new float[] {cos(a) * rr, topY(rr) + hy * 0.4F, sin(a) * rr, hx, hy, hz, 0.75F + r.nextFloat() * 0.35F, r.nextInt(5)};
        }
        botBoxes = new float[100][];
        for (int i = 0; i < botBoxes.length; i++) {
            float rr = 28.0F + (float) Math.sqrt(r.nextFloat()) * 80.0F;
            float a = r.nextFloat() * TAU;
            float hx = 2.0F + r.nextFloat() * 5.0F;
            float hz = 2.0F + r.nextFloat() * 5.0F;
            float hy = 1.0F + r.nextFloat() * 3.0F;
            botBoxes[i] = new float[] {cos(a) * rr, botY(rr) - hy * 0.4F, sin(a) * rr, hx, hy, hz, 0.6F + r.nextFloat() * 0.3F, r.nextInt(5)};
        }
    }

    /** Casco solido centrado en el origen. burn = 0..1 oscurece el casco (quemado). */
    public void hull(VertexConsumer vc, PoseStack.Pose pose, long seed, float q, float burn) {
        if (topBoxes == null) makeBoxes(seed);
        float dark = 1.0F - 0.45F * burn;
        TextureAtlasSprite[] sps = {
                HullGeo.sprite(Blocks.DEEPSLATE_TILES), HullGeo.sprite(Blocks.POLISHED_BLACKSTONE_BRICKS),
                HullGeo.sprite(Blocks.BLACKSTONE), HullGeo.sprite(Blocks.POLISHED_DEEPSLATE),
                HullGeo.sprite(Blocks.GRAY_CONCRETE)};

        for (float[] h : HULL) {
            int rows = Math.max(1, Math.round((h[1] - h[0]) / 6.0F));
            HullGeo.frustum(vc, pose, sps, 0, 0, 0, h[0] * q, h[1] * q, h[2] * q, h[3] * q, SIDES, 3, rows, h[4] * dark, 0.0F);
        }

        float by = -40.0F * q;
        for (int i = 0; i < SIDES; i++) {
            float a0 = TAU * i / SIDES;
            float a1 = TAU * (i + 1) / SIDES;
            HullGeo.quad(vc, pose, sps[0], 0, by, 0, 0, by, 0,
                    cos(a1) * 24.0F * q, by, sin(a1) * 24.0F * q, cos(a0) * 24.0F * q, by, sin(a0) * 24.0F * q,
                    0, -1, 0, 0.45F * dark, 0.45F * dark, 0.5F * dark);
        }

        for (float[] b : topBoxes) {
            float k = b[6] * dark;
            HullGeo.box(vc, pose, sps[(int) b[7]], b[0] * q, b[1] * q, b[2] * q, b[3] * q, b[4] * q, b[5] * q,
                    k * 0.85F, k * 0.88F, k * 0.95F, true, false);
        }
        for (float[] b : botBoxes) {
            float k = b[6] * dark;
            HullGeo.box(vc, pose, sps[(int) b[7]], b[0] * q, b[1] * q, b[2] * q, b[3] * q, b[4] * q, b[5] * q,
                    k * 0.7F, k * 0.72F, k * 0.78F, false, true);
        }
        HullGeo.box(vc, pose, sps[1], 0, 46.0F * q, 0, 6.0F * q, 6.0F * q, 6.0F * q, 0.9F * dark, 0.92F * dark, 1.0F * dark, true, false);
        HullGeo.box(vc, pose, sps[2], 0, 58.0F * q, 0, 2.0F * q, 6.0F * q, 2.0F * q, 0.8F * dark, 0.82F * dark, 0.9F * dark, true, false);
        for (int i = 0; i < 4; i++) {
            float a = i * TAU / 4 + 0.4F;
            HullGeo.box(vc, pose, sps[3], cos(a) * 20.0F * q, 40.0F * q, sin(a) * 20.0F * q,
                    1.2F * q, 6.0F * q, 1.2F * q, 0.8F * dark, 0.82F * dark, 0.9F * dark, true, false);
        }
    }

    private static void bellyRibbon(VertexConsumer vc, Matrix4f m, float q,
                                    float a, float r0, float r1, float w, float cr, float cg, float cb, float al) {
        float c = cos(a);
        float sn = sin(a);
        float px = -sn * w * q;
        float pz = c * w * q;
        float x0 = c * r0 * q;
        float z0 = sn * r0 * q;
        float y0 = botY(r0) * q - 0.7F * q;
        float x1 = c * r1 * q;
        float z1 = sn * r1 * q;
        float y1 = botY(r1) * q - 0.7F * q;
        FxGeo.quad(vc, m, x0 + px, y0, z0 + pz, al, x0 - px, y0, z0 - pz, al, x1 - px, y1, z1 - pz, al, x1 + px, y1, z1 + pz, al, cr, cg, cb);
    }

    private static void bellyRing(VertexConsumer vc, Matrix4f m, float q, float rr, float w,
                                  int segs, float cr, float cg, float cb, float al) {
        float y = botY(rr) * q - 0.7F * q;
        for (int i = 0; i < segs; i++) {
            float a0 = TAU * i / segs;
            float a1 = TAU * (i + 1) / segs;
            float c0 = cos(a0);
            float s0 = sin(a0);
            float c1 = cos(a1);
            float s1 = sin(a1);
            FxGeo.quad(vc, m,
                    c0 * (rr - w) * q, y, s0 * (rr - w) * q, al,
                    c0 * (rr + w) * q, y, s0 * (rr + w) * q, al,
                    c1 * (rr + w) * q, y, s1 * (rr + w) * q, al,
                    c1 * (rr - w) * q, y, s1 * (rr - w) * q, al,
                    cr, cg, cb);
        }
    }

    /**
     * Luces de la nave, centradas en el origen: borde, cuadricula de la panza, rejillas naranjas.
     * grid = color de la cuadricula, ventBoost = fuerza de las rejillas, damage 0..1 apaga luces al azar.
     */
    public void lights(VertexConsumer vc, Matrix4f m, float t, float q, float gr, float gg, float gb,
                       float ventBoost, float damage) {
        Random rnd = new Random(77L);
        for (int i = 0; i < 64; i++) {
            float a = i * TAU / 64 + t * 0.008F;
            float da = 0.022F;
            float rr = 112.0F * q + 0.8F;
            boolean warm = i % 2 == 0;
            float cr = warm ? 1.0F : 0.4F;
            float cg = warm ? 0.65F : 1.0F;
            float cb = warm ? 0.25F : 0.6F;
            float on = rnd.nextFloat() < damage * 0.7F ? 0.0F : 1.0F;
            float al = 0.9F * on * (0.55F + 0.45F * sin(t * 0.2F + i));
            if (al < 0.02F) continue;
            float h = 3.2F * q;
            FxGeo.quad(vc, m,
                    cos(a - da) * rr, -h, sin(a - da) * rr, al,
                    cos(a + da) * rr, -h, sin(a + da) * rr, al,
                    cos(a + da) * rr, h, sin(a + da) * rr, al,
                    cos(a - da) * rr, h, sin(a - da) * rr, al,
                    cr, cg, cb);
        }

        float pulse = (0.6F + 0.4F * sin(t * 0.08F)) * (1.0F - 0.5F * damage);
        for (int k = 0; k < 24; k++) {
            if (rnd.nextFloat() < damage * 0.5F) continue;
            bellyRibbon(vc, m, q, k * TAU / 24, 27.0F, 108.0F, 0.35F, gr, gg, gb, 0.55F * pulse);
        }
        float[] ringsR = {44.0F, 63.0F, 82.0F, 98.0F};
        for (float rr : ringsR) bellyRing(vc, m, q, rr, 0.35F, 48, gr, gg, gb, 0.55F * pulse);

        for (int v = 0; v < 8; v++) {
            float az = v * TAU / 8 + 0.2F;
            float flick = (0.7F + 0.3F * sin(t * 0.3F + v * 1.3F)) * ventBoost;
            for (int sl = -3; sl <= 3; sl++) {
                bellyRibbon(vc, m, q, az + sl * 0.022F, 62.0F, 90.0F, 0.4F, 1.0F, 0.5F, 0.15F, Math.min(1.0F, 0.9F * flick));
            }
            for (int rr = 0; rr < 3; rr++) {
                float r0 = 66.0F + rr * 11.0F;
                bellyRibbon(vc, m, q, az, r0, r0 + 1.6F, 3.4F, 1.0F, 0.55F, 0.15F, Math.min(1.0F, 0.8F * flick));
            }
            bellyRibbon(vc, m, q, az, 62.0F, 90.0F, 3.6F, 1.0F, 0.4F, 0.1F, Math.min(1.0F, 0.22F * flick));
        }
    }
}
