package com.tablilla.apocaliptica.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Figuras luminosas para los efectos: cilindros, anillos, esferas, rayos... Todo de doble cara. */
public final class FxGeo {
    public static final float TAU = (float) (Math.PI * 2.0);

    private FxGeo() {}

    public static float sin(float a) {
        return (float) Math.sin(a);
    }

    public static float cos(float a) {
        return (float) Math.cos(a);
    }

    public static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    public static float smooth(float t, float a, float b) {
        float x = clamp01((t - a) / (b - a));
        return x * x * (3.0F - 2.0F * x);
    }

    public static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                            float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).endVertex();
    }

    /** Cuadrilatero de doble cara con alfa por vertice. */
    public static void quad(VertexConsumer vc, Matrix4f m,
                            float x1, float y1, float z1, float a1,
                            float x2, float y2, float z2, float a2,
                            float x3, float y3, float z3, float a3,
                            float x4, float y4, float z4, float a4,
                            float r, float g, float b) {
        vtx(vc, m, x1, y1, z1, r, g, b, a1);
        vtx(vc, m, x2, y2, z2, r, g, b, a2);
        vtx(vc, m, x3, y3, z3, r, g, b, a3);
        vtx(vc, m, x4, y4, z4, r, g, b, a4);
        vtx(vc, m, x4, y4, z4, r, g, b, a4);
        vtx(vc, m, x3, y3, z3, r, g, b, a3);
        vtx(vc, m, x2, y2, z2, r, g, b, a2);
        vtx(vc, m, x1, y1, z1, r, g, b, a1);
    }

    /** Sector de anillo horizontal entre dos angulos. */
    public static void sector(VertexConsumer vc, Matrix4f m, float cx, float y, float cz,
                              float r1, float r2, float ang0, float ang1,
                              float r, float g, float b, float aIn, float aOut) {
        float c0 = cos(ang0);
        float s0 = sin(ang0);
        float c1 = cos(ang1);
        float s1 = sin(ang1);
        quad(vc, m,
                cx + c0 * r1, y, cz + s0 * r1, aIn,
                cx + c1 * r1, y, cz + s1 * r1, aIn,
                cx + c1 * r2, y, cz + s1 * r2, aOut,
                cx + c0 * r2, y, cz + s0 * r2, aOut,
                r, g, b);
    }

    /** Anillo horizontal (anillo plano) con alfa distinto en el borde interior y exterior. */
    public static void ring(VertexConsumer vc, Matrix4f m, float cx, float y, float cz,
                            float rIn, float rOut, int sides,
                            float r, float g, float b, float aIn, float aOut) {
        for (int i = 0; i < sides; i++) {
            float a0 = TAU * i / sides;
            float a1 = TAU * (i + 1) / sides;
            sector(vc, m, cx, y, cz, rIn, rOut, a0, a1, r, g, b, aIn, aOut);
        }
    }

    /** Anillo plano cuyo brillo gira (disco de acrecion). arms = numero de brazos brillantes. */
    public static void diskRing(VertexConsumer vc, Matrix4f m, float cx, float y, float cz,
                                float rIn, float rOut, int sides,
                                float r, float g, float b, float aIn, float aOut, int arms, float phase) {
        for (int i = 0; i < sides; i++) {
            float a0 = TAU * i / sides;
            float a1 = TAU * (i + 1) / sides;
            float mid = (a0 + a1) * 0.5F;
            float mod = 0.25F + 0.75F * (0.5F + 0.5F * sin(arms * mid + phase));
            sector(vc, m, cx, y, cz, rIn, rOut, a0, a1, r, g, b, aIn * mod, aOut * mod);
        }
    }

    /** Tubo (cilindro o cono truncado) en cualquier direccion. */
    public static void tube(VertexConsumer vc, Matrix4f m,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float rad0, float rad1, int sides,
                            float r, float g, float b, float a0, float a1) {
        float ax = x1 - x0;
        float ay = y1 - y0;
        float az = z1 - z0;
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1.0E-4F) return;
        ax /= len;
        ay /= len;
        az /= len;
        float hx = 0.0F;
        float hy = 1.0F;
        float hz = 0.0F;
        if (Math.abs(ay) > 0.95F) {
            hx = 1.0F;
            hy = 0.0F;
        }
        float ux = ay * hz - az * hy;
        float uy = az * hx - ax * hz;
        float uz = ax * hy - ay * hx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = ay * uz - az * uy;
        float vy = az * ux - ax * uz;
        float vz = ax * uy - ay * ux;
        for (int i = 0; i < sides; i++) {
            float t0 = TAU * i / sides;
            float t1 = TAU * (i + 1) / sides;
            float c0 = cos(t0);
            float s0 = sin(t0);
            float c1 = cos(t1);
            float s1 = sin(t1);
            float o0x = ux * c0 + vx * s0;
            float o0y = uy * c0 + vy * s0;
            float o0z = uz * c0 + vz * s0;
            float o1x = ux * c1 + vx * s1;
            float o1y = uy * c1 + vy * s1;
            float o1z = uz * c1 + vz * s1;
            quad(vc, m,
                    x0 + o0x * rad0, y0 + o0y * rad0, z0 + o0z * rad0, a0,
                    x0 + o1x * rad0, y0 + o1y * rad0, z0 + o1z * rad0, a0,
                    x1 + o1x * rad1, y1 + o1y * rad1, z1 + o1z * rad1, a1,
                    x1 + o0x * rad1, y1 + o0y * rad1, z1 + o0z * rad1, a1,
                    r, g, b);
        }
    }

    /** Esfera. */
    public static void sphere(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float rad,
                              int stacks, int slices, float r, float g, float b, float a) {
        for (int i = 0; i < stacks; i++) {
            float p0 = -TAU / 4.0F + (TAU / 2.0F) * i / stacks;
            float p1 = -TAU / 4.0F + (TAU / 2.0F) * (i + 1) / stacks;
            float y0 = sin(p0) * rad;
            float r0 = cos(p0) * rad;
            float y1 = sin(p1) * rad;
            float r1 = cos(p1) * rad;
            for (int j = 0; j < slices; j++) {
                float a0 = TAU * j / slices;
                float a1 = TAU * (j + 1) / slices;
                quad(vc, m,
                        cx + cos(a0) * r0, cy + y0, cz + sin(a0) * r0, a,
                        cx + cos(a1) * r0, cy + y0, cz + sin(a1) * r0, a,
                        cx + cos(a1) * r1, cy + y1, cz + sin(a1) * r1, a,
                        cx + cos(a0) * r1, cy + y1, cz + sin(a0) * r1, a,
                        r, g, b);
            }
        }
    }

    /** Rayo zigzagueante hecho con dos cintas cruzadas. */
    public static void bolt(VertexConsumer vc, Matrix4f m, float[] xs, float[] ys, float[] zs, int n, float w,
                            float r, float g, float b, float a) {
        for (int i = 0; i < n - 1; i++) {
            quad(vc, m,
                    xs[i] - w, ys[i], zs[i], a,
                    xs[i] + w, ys[i], zs[i], a,
                    xs[i + 1] + w, ys[i + 1], zs[i + 1], a,
                    xs[i + 1] - w, ys[i + 1], zs[i + 1], a,
                    r, g, b);
            quad(vc, m,
                    xs[i], ys[i], zs[i] - w, a,
                    xs[i], ys[i], zs[i] + w, a,
                    xs[i + 1], ys[i + 1], zs[i + 1] + w, a,
                    xs[i + 1], ys[i + 1], zs[i + 1] - w, a,
                    r, g, b);
        }
    }

    /** Rayo brillante de varias capas: nucleo blanco y halos de color. Se dibuja de adentro hacia afuera. */
    public static void glowBeam(VertexConsumer vc, Matrix4f m,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float rad, float r, float g, float b, float k, int sides) {
        tube(vc, m, x0, y0, z0, x1, y1, z1, rad * 0.18F, rad * 0.18F, sides, 1.0F, 1.0F, 1.0F, 0.95F * k, 0.95F * k);
        tube(vc, m, x0, y0, z0, x1, y1, z1, rad * 0.42F, rad * 0.42F, sides,
                lerp(r, 1.0F, 0.5F), lerp(g, 1.0F, 0.5F), lerp(b, 1.0F, 0.5F), 0.55F * k, 0.55F * k);
        tube(vc, m, x0, y0, z0, x1, y1, z1, rad * 0.75F, rad * 0.75F, sides, r, g, b, 0.28F * k, 0.28F * k);
        tube(vc, m, x0, y0, z0, x1, y1, z1, rad * 1.2F, rad * 1.2F, sides, r, g, b, 0.12F * k, 0.12F * k);
    }

    /** Tronco de cono vertical con sombreado simple (para la nave). rot gira las facetas. */
    public static void frustumSolid(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                                    float y0, float y1, float rad0, float rad1, int sides,
                                    float r, float g, float b, float rot) {
        float dy = y1 - y0;
        float dr = rad0 - rad1;
        for (int i = 0; i < sides; i++) {
            float a0 = rot + TAU * i / sides;
            float a1 = rot + TAU * (i + 1) / sides;
            float am = (a0 + a1) * 0.5F;
            float nx = cos(am) * dy;
            float ny = dr;
            float nz = sin(am) * dy;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1.0E-4F) continue;
            float light = Math.max(0.0F, (nx * 0.35F + ny * 0.8F + nz * 0.45F) / nl);
            float sh = 0.5F + 0.5F * light;
            if ((i & 1) == 0) sh *= 0.93F;
            quad(vc, m,
                    cx + cos(a0) * rad0, cy + y0, cz + sin(a0) * rad0, 1.0F,
                    cx + cos(a1) * rad0, cy + y0, cz + sin(a1) * rad0, 1.0F,
                    cx + cos(a1) * rad1, cy + y1, cz + sin(a1) * rad1, 1.0F,
                    cx + cos(a0) * rad1, cy + y1, cz + sin(a0) * rad1, 1.0F,
                    r * sh, g * sh, b * sh);
        }
    }

    // ------------------------------------------------------------------ figuras pegadas al relieve

    /** Cinta que sigue el relieve entre dos puntos (x, z locales). Alfa va de a0 a a1 a lo largo de la linea. */
    public static void terrainLine(VertexConsumer vc, Matrix4f m, GroundMap gm, float x0, float z0, float x1, float z1,
                                   float width, float lift, float cr, float cg, float cb, float a0, float a1) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3F) return;
        float px = -dz / len * width * 0.5F;
        float pz = dx / len * width * 0.5F;
        int segs = Math.max(1, (int) Math.ceil(len / 2.5F));
        for (int i = 0; i < segs; i++) {
            float ka = i / (float) segs;
            float kb = (i + 1) / (float) segs;
            float xa = x0 + dx * ka;
            float za = z0 + dz * ka;
            float xb = x0 + dx * kb;
            float zb = z0 + dz * kb;
            float ha = gm.at(xa, za) + lift;
            float hb = gm.at(xb, zb) + lift;
            float aa = a0 + (a1 - a0) * ka;
            float ab = a0 + (a1 - a0) * kb;
            quad(vc, m,
                    xa + px, ha, za + pz, aa,
                    xa - px, ha, za - pz, aa,
                    xb - px, hb, zb - pz, ab,
                    xb + px, hb, zb + pz, ab,
                    cr, cg, cb);
        }
    }

    /** Anillo plano que sigue el relieve. */
    public static void terrainRing(VertexConsumer vc, Matrix4f m, GroundMap gm, float cx, float cz,
                                   float rIn, float rOut, int sides, float lift,
                                   float cr, float cg, float cb, float aIn, float aOut) {
        for (int i = 0; i < sides; i++) {
            float a0 = TAU * i / sides;
            float a1 = TAU * (i + 1) / sides;
            float c0 = cos(a0);
            float s0 = sin(a0);
            float c1 = cos(a1);
            float s1 = sin(a1);
            quad(vc, m,
                    cx + c0 * rIn, gm.at(cx + c0 * rIn, cz + s0 * rIn) + lift, cz + s0 * rIn, aIn,
                    cx + c1 * rIn, gm.at(cx + c1 * rIn, cz + s1 * rIn) + lift, cz + s1 * rIn, aIn,
                    cx + c1 * rOut, gm.at(cx + c1 * rOut, cz + s1 * rOut) + lift, cz + s1 * rOut, aOut,
                    cx + c0 * rOut, gm.at(cx + c0 * rOut, cz + s0 * rOut) + lift, cz + s0 * rOut, aOut,
                    cr, cg, cb);
        }
    }

    /** Pared vertical (onda de choque) que sigue el relieve. */
    public static void terrainWall(VertexConsumer vc, Matrix4f m, GroundMap gm, float cx, float cz,
                                   float rad, float height, int sides, float lift,
                                   float cr, float cg, float cb, float aBottom, float aTop) {
        for (int i = 0; i < sides; i++) {
            float a0 = TAU * i / sides;
            float a1 = TAU * (i + 1) / sides;
            float x0 = cx + cos(a0) * rad;
            float z0 = cz + sin(a0) * rad;
            float x1 = cx + cos(a1) * rad;
            float z1 = cz + sin(a1) * rad;
            float h0 = gm.at(x0, z0) + lift;
            float h1 = gm.at(x1, z1) + lift;
            quad(vc, m,
                    x0, h0, z0, aBottom,
                    x1, h1, z1, aBottom,
                    x1, h1 + height, z1, aTop,
                    x0, h0 + height, z0, aTop,
                    cr, cg, cb);
        }
    }

    /** Circulo hecho de cinta pegada al relieve. frac = que parte del circulo se dibuja (0 a 1). */
    public static void polyCircle(VertexConsumer vc, Matrix4f m, GroundMap gm, float cx, float cz, float rad,
                                  int sides, float width, float lift, float cr, float cg, float cb, float a, float frac) {
        int n = Math.round(sides * clamp01(frac));
        for (int i = 0; i < n; i++) {
            float a0 = TAU * i / sides;
            float a1 = TAU * (i + 1) / sides;
            terrainLine(vc, m, gm, cx + cos(a0) * rad, cz + sin(a0) * rad, cx + cos(a1) * rad, cz + sin(a1) * rad,
                    width, lift, cr, cg, cb, a, a);
        }
    }

    /** Disco inclinado (disco de acrecion visto de lado). tilt en radianes, gira sobre el eje X. */
    public static void tiltedDisk(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                                  float rIn, float rOut, int sides, float tilt,
                                  float cr, float cg, float cb, float aIn, float aOut, int arms, float phase) {
        float ct = cos(tilt);
        float st = sin(tilt);
        for (int i = 0; i < sides; i++) {
            float a0 = TAU * i / sides;
            float a1 = TAU * (i + 1) / sides;
            float mid = (a0 + a1) * 0.5F;
            float mod = 0.2F + 0.8F * (0.5F + 0.5F * sin(arms * mid + phase));
            float c0 = cos(a0);
            float s0 = sin(a0);
            float c1 = cos(a1);
            float s1 = sin(a1);
            quad(vc, m,
                    cx + c0 * rIn, cy + s0 * rIn * st, cz + s0 * rIn * ct, aIn * mod,
                    cx + c1 * rIn, cy + s1 * rIn * st, cz + s1 * rIn * ct, aIn * mod,
                    cx + c1 * rOut, cy + s1 * rOut * st, cz + s1 * rOut * ct, aOut * mod,
                    cx + c0 * rOut, cy + s0 * rOut * st, cz + s0 * rOut * ct, aOut * mod,
                    cr, cg, cb);
        }
    }

    /** Estrella (cuadrado que mira a la camara). (dx, dy, dz) = direccion unitaria desde la camara hacia la estrella. */
    public static void star(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                            float dx, float dy, float dz, float size, float cr, float cg, float cb, float a) {
        float rx = dz;
        float rz = -dx;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-4F) return;
        rx /= rl;
        rz /= rl;
        float ux = dy * rz;
        float uy = dz * rx - dx * rz;
        float uz = -dy * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0E-4F) return;
        ux /= ul;
        uy /= ul;
        uz /= ul;
        quad(vc, m,
                x - rx * size - ux * size, y - uy * size, z - rz * size - uz * size, a,
                x + rx * size - ux * size, y - uy * size, z + rz * size - uz * size, a,
                x + rx * size + ux * size, y + uy * size, z + rz * size + uz * size, a,
                x - rx * size + ux * size, y + uy * size, z - rz * size + uz * size, a,
                cr, cg, cb);
    }

    /** Cinta fina en el plano horizontal a altura y. */
    public static void flatLine(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float z1,
                                float width, float cr, float cg, float cb, float a) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3F) return;
        float px = -dz / len * width * 0.5F;
        float pz = dx / len * width * 0.5F;
        quad(vc, m, x0 + px, y, z0 + pz, a, x0 - px, y, z0 - pz, a, x1 - px, y, z1 - pz, a, x1 + px, y, z1 + pz, a, cr, cg, cb);
    }

    /** Cinta en un plano vertical definido por el eje horizontal (ux, uz) y la altura: de (a0, h0) a (a1, h1). */
    public static void planeLine(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float ux, float uz,
                                 float a0, float h0, float a1, float h1, float w0, float w1,
                                 float cr, float cg, float cb, float al0, float al1) {
        float dx = a1 - a0;
        float dh = h1 - h0;
        float len = (float) Math.sqrt(dx * dx + dh * dh);
        if (len < 1.0E-3F) return;
        float nx = -dh / len;
        float nh = dx / len;
        float p0x = nx * w0 * 0.5F;
        float p0h = nh * w0 * 0.5F;
        float p1x = nx * w1 * 0.5F;
        float p1h = nh * w1 * 0.5F;
        quad(vc, m,
                cx + ux * (a0 + p0x), cy + h0 + p0h, cz + uz * (a0 + p0x), al0,
                cx + ux * (a0 - p0x), cy + h0 - p0h, cz + uz * (a0 - p0x), al0,
                cx + ux * (a1 - p1x), cy + h1 - p1h, cz + uz * (a1 - p1x), al1,
                cx + ux * (a1 + p1x), cy + h1 + p1h, cz + uz * (a1 + p1x), al1,
                cr, cg, cb);
    }
}
