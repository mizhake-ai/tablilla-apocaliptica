package com.tablilla.apocaliptica.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Geometria SOLIDA con texturas de bloques (para la nave y el agujero negro). Se dibuja con el RenderType de
 * entidades, que funciona bien con shaders. Todo de doble cara para no depender del orden de los vertices.
 */
public final class HullGeo {
    private static final int LIGHT = 0xF000F0;
    private static final float TAU = (float) (Math.PI * 2.0);

    private HullGeo() {}

    public static TextureAtlasSprite sprite(Block b) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(b.defaultBlockState());
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b,
                          float u, float w, Vector3f n) {
        vc.vertex(m, x, y, z).color(r, g, b, 1.0F).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LIGHT)
                .normal(n.x, n.y, n.z).endVertex();
    }

    /** Cuadrilatero texturizado de doble cara. (nx, ny, nz) = normal aproximada del lado "de afuera". */
    public static void quad(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3, float x4, float y4, float z4,
                            float nx, float ny, float nz, float r, float g, float b) {
        Matrix4f m = pose.pose();
        Vector3f n = pose.normal().transform(new Vector3f(nx, ny, nz));
        Vector3f n2 = new Vector3f(-n.x, -n.y, -n.z);
        float u0 = sp.getU0();
        float u1 = sp.getU1();
        float v0 = sp.getV0();
        float v1 = sp.getV1();
        v(vc, m, x1, y1, z1, r, g, b, u0, v0, n);
        v(vc, m, x2, y2, z2, r, g, b, u1, v0, n);
        v(vc, m, x3, y3, z3, r, g, b, u1, v1, n);
        v(vc, m, x4, y4, z4, r, g, b, u0, v1, n);
        v(vc, m, x4, y4, z4, r, g, b, u0, v1, n2);
        v(vc, m, x3, y3, z3, r, g, b, u1, v1, n2);
        v(vc, m, x2, y2, z2, r, g, b, u1, v0, n2);
        v(vc, m, x1, y1, z1, r, g, b, u0, v0, n2);
    }

    private static float hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0F;
    }

    private static float shade(float nx, float ny, float nz) {
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1.0E-4F) return 0.6F;
        float d = Math.max(0.0F, (nx * 0.35F + ny * 0.8F + nz * 0.45F) / l);
        return 0.38F + 0.62F * d;
    }

    /**
     * Tronco de cono vertical hecho de paneles de bloque (mosaico). cols x rows paneles por cara.
     * tint = brillo base del tramo.
     */
    public static void frustum(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite[] sps,
                               float cx, float cy, float cz, float y0, float y1, float rad0, float rad1,
                               int sides, int cols, int rows, float tint, float rot) {
        float dy = y1 - y0;
        float dr = rad0 - rad1;
        for (int i = 0; i < sides; i++) {
            float a0 = rot + TAU * i / sides;
            float a1 = rot + TAU * (i + 1) / sides;
            float am = (a0 + a1) * 0.5F;
            float nx = (float) Math.cos(am) * dy;
            float ny = dr;
            float nz = (float) Math.sin(am) * dy;
            float sh = shade(nx, ny, nz) * tint;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            for (int sc = 0; sc < cols; sc++) {
                float ua = sc / (float) cols;
                float ub = (sc + 1) / (float) cols;
                for (int sr = 0; sr < rows; sr++) {
                    float va = sr / (float) rows;
                    float vb = (sr + 1) / (float) rows;
                    float h = hash(i, sc, sr + (int) (y0 * 3));
                    TextureAtlasSprite sp = sps[(int) (h * 997.0F) % sps.length];
                    float k = sh * (0.82F + 0.28F * hash(sr, i, sc));
                    float ra = rad0 + (rad1 - rad0) * va;
                    float rb = rad0 + (rad1 - rad0) * vb;
                    float ya = cy + y0 + dy * va;
                    float yb = cy + y0 + dy * vb;
                    quad(vc, pose, sp,
                            cx + (c0 + (c1 - c0) * ua) * ra, ya, cz + (s0 + (s1 - s0) * ua) * ra,
                            cx + (c0 + (c1 - c0) * ub) * ra, ya, cz + (s0 + (s1 - s0) * ub) * ra,
                            cx + (c0 + (c1 - c0) * ub) * rb, yb, cz + (s0 + (s1 - s0) * ub) * rb,
                            cx + (c0 + (c1 - c0) * ua) * rb, yb, cz + (s0 + (s1 - s0) * ua) * rb,
                            nx, ny, nz, k * 0.95F, k, k * 1.08F);
                }
            }
        }
    }

    /** Caja con textura. Sin cara de abajo si skipBottom. */
    public static void box(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp,
                           float cx, float cy, float cz, float hx, float hy, float hz,
                           float r, float g, float b, boolean skipBottom, boolean skipTop) {
        float x0 = cx - hx;
        float x1 = cx + hx;
        float y0 = cy - hy;
        float y1 = cy + hy;
        float z0 = cz - hz;
        float z1 = cz + hz;
        if (!skipTop) {
            quad(vc, pose, sp, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, r, g, b);
        }
        if (!skipBottom) {
            quad(vc, pose, sp, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r * 0.55F, g * 0.55F, b * 0.55F);
        }
        quad(vc, pose, sp, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r * 0.8F, g * 0.8F, b * 0.8F);
        quad(vc, pose, sp, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, r * 0.7F, g * 0.7F, b * 0.7F);
        quad(vc, pose, sp, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, r * 0.9F, g * 0.9F, b * 0.9F);
        quad(vc, pose, sp, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r * 0.65F, g * 0.65F, b * 0.65F);
    }

    /** Esfera solida con una sola textura (se usa con un bloque negro para el agujero negro). */
    public static void sphere(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp,
                              float cx, float cy, float cz, float rad, int stacks, int slices,
                              float r, float g, float b) {
        for (int i = 0; i < stacks; i++) {
            float p0 = -TAU / 4.0F + (TAU / 2.0F) * i / stacks;
            float p1 = -TAU / 4.0F + (TAU / 2.0F) * (i + 1) / stacks;
            float y0 = (float) Math.sin(p0) * rad;
            float r0 = (float) Math.cos(p0) * rad;
            float y1 = (float) Math.sin(p1) * rad;
            float r1 = (float) Math.cos(p1) * rad;
            for (int j = 0; j < slices; j++) {
                float a0 = TAU * j / slices;
                float a1 = TAU * (j + 1) / slices;
                float am = (a0 + a1) * 0.5F;
                float pm = (p0 + p1) * 0.5F;
                quad(vc, pose, sp,
                        cx + (float) Math.cos(a0) * r0, cy + y0, cz + (float) Math.sin(a0) * r0,
                        cx + (float) Math.cos(a1) * r0, cy + y0, cz + (float) Math.sin(a1) * r0,
                        cx + (float) Math.cos(a1) * r1, cy + y1, cz + (float) Math.sin(a1) * r1,
                        cx + (float) Math.cos(a0) * r1, cy + y1, cz + (float) Math.sin(a0) * r1,
                        (float) (Math.cos(am) * Math.cos(pm)), (float) Math.sin(pm), (float) (Math.sin(am) * Math.cos(pm)),
                        r, g, b);
            }
        }
    }
}
